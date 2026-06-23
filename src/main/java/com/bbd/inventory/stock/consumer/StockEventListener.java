package com.bbd.inventory.stock.consumer;

import com.bbd.inventory.common.ProcessedEventRepository;
import com.bbd.inventory.outbox.OutboxRecorder;
import com.bbd.inventory.stock.command.StockCommandService;
import com.bbd.inventory.stock.event.ItemChanged;
import com.bbd.inventory.stock.event.SalesOrderReceived;
import com.bbd.inventory.stock.event.StockInRequested;

import com.bbd.inventory.stock.event.StockReplenished;
import com.bbd.inventory.stock.reservation.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockEventListener {

    private final ObjectMapper objectMapper;
    private final ProcessedEventRepository processedEventRepository;
    private final StockCommandService stockCommandService;
    private final OutboxRecorder outboxRecorder;
    private final ReservationService reservationService;


    @KafkaListener(topics = "procurement.stock-in-requested", groupId = "stock-group")
    @Transactional
    public void onStockIn(String message) {

        StockInRequested e = objectMapper.readValue(message, StockInRequested.class);

        if (processedEventRepository.claim(e.eventId()) == 0) return;   // already processed

        stockCommandService.inbound(e);

        // SO-linked inbound only -> echo a replenish notification to sales
        if (e.soId() != null && !e.soId().isBlank()) {
            String eventId = UUID.randomUUID().toString();
            StockReplenished out = new StockReplenished(
                    eventId,
                    "inventory",
                    "STOCK_REPLENISHED",
                    Instant.now().toString(),
                    e.soId(),
                    e.lines().stream()
                            .map(l -> new StockReplenished.Line(
                                    l.sku(), l.quantity(), l.warehouseCode())).toList()
            );

            outboxRecorder.record("inventory.stock-replenished",
                    e.soId(), eventId, out);
        }
    }

    /*
        Claim-first idempotency, one transaction per message:
            1. claim(eventId) -> atomic INSERT ... ON CONFLICT DO NOTHING; the unique PK is the gate
                                 1 = first time (proceed), 0 = duplicate (skip)
            2. business work
        Same tx: rollback un-claims, so a failed message is reprocessable.

        Replaces sales.stock-out-requested: sales sends only soNumber, inventory
        resolves the RESERVED rows itself and issues them (reserved -> shipped).
        Double-idempotent: claim(eventId) blocks redelivery, issue() skips non-RESERVED rows.
     */

    @KafkaListener(topics = "sales.order.received", groupId = "stock-group")
    @Transactional
    public void onSalesOrderReceived(String message) {
        SalesOrderReceived e = objectMapper.readValue(message, SalesOrderReceived.class);
        if (processedEventRepository.claim(e.eventId()) == 0) return;   // already processed

        reservationService.issue(e.soNumber());
    }


    @KafkaListener(topics = "item.changed", groupId = "stock-group")
    @Transactional
    public void onItemChanged(String message) {
        ItemChanged e = objectMapper.readValue(message, ItemChanged.class);

        if (processedEventRepository.claim(e.eventId()) == 0) return;   // already processed

        switch (e.eventType()) {
            case "ITEM_PRICE_CHANGED" -> {
                if (e.unitPrice() != null) stockCommandService.updateUnitPrice(e.sku(), e.unitPrice());
                else log.warn("ITEM_PRICE_CHANGED missing unitPrice, sku={}", e.sku());
            }
            case "ITEM_ACTIVE_CHANGED" -> {
                if (e.active() != null) stockCommandService.updateActive(e.sku(), e.active());
                else log.warn("ITEM_ACTIVE_CHANGED missing active flag, sku={}", e.sku());
            }
            default -> log.warn("unknown item eventType: {}", e.eventType());
        }
    }

}

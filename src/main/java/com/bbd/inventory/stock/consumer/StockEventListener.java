package com.bbd.inventory.stock.consumer;

import com.bbd.inventory.common.ProcessedEvent;
import com.bbd.inventory.common.ProcessedEventRepository;
import com.bbd.inventory.outbox.OutboxRecorder;
import com.bbd.inventory.stock.command.StockCommandService;
import com.bbd.inventory.stock.event.ItemChanged;
import com.bbd.inventory.stock.event.StockInRequested;
import com.bbd.inventory.stock.event.StockOutRequested;

import com.bbd.inventory.stock.event.StockReplenished;
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


    @KafkaListener(topics = "procurement.stock-in-requested", groupId = "stock-group")
    @Transactional
    public void onStockIn(String message) {

        StockInRequested e = objectMapper.readValue(message, StockInRequested.class);

        if (processedEventRepository.existsByEventId(e.eventId())) return;

        stockCommandService.inbound(e);

        // SO-linked inbound only -> echo a replenish notification to sales
        if (e.soNumber() != null && !e.soNumber().isBlank()) {
            String eventId = UUID.randomUUID().toString();
            StockReplenished out = new StockReplenished(
                    eventId,
                    "inventory",
                    "STOCK_REPLENISHED",
                    Instant.now().toString(),
                    e.soNumber(),
                    e.lines().stream()
                            .map(l -> new StockReplenished.Line(
                                    l.sku(), l.quantity(), l.warehouseCode())).toList()
            );

            outboxRecorder.record("inventory.stock-replenished",
                    e.soNumber(), eventId, out);
        }

        processedEventRepository.save(new ProcessedEvent(e.eventId()));
    }

    /*
        Idempotency, one transaction per message:
            1. existsByEventId -> clean fast-path skip for an already-processed event (no exception)
            2. business work
            3. save(ProcessedEvent) -> real INSERT (ProcessedEvent) implements persistable, isNew = TRUE)
     */

    @KafkaListener(topics = "sales.stock-out-requested", groupId = "stock-group")
    @Transactional
    public void onStockOut(String message) {
        /*
            Listener becomes claim-first, so the unique PK is the atomic gate

            atomic gate:    fully happened, or didn't happen at all. Gate means only
                            one thread/process can pass for a given key.
                            Hence, a single operation that simultaenously checks/records/rejects.

         */
        StockOutRequested e = objectMapper.readValue(message, StockOutRequested.class);
        if (processedEventRepository.existsByEventId(e.eventId())) return;

        stockCommandService.outbound(e);
        processedEventRepository.save(new ProcessedEvent(e.eventId()));
    }

    @KafkaListener(topics = "item.changed", groupId = "stock-group")
    @Transactional
    public void onItemChanged(String message) {
        ItemChanged e = objectMapper.readValue(message, ItemChanged.class);

        if (processedEventRepository.existsByEventId(e.eventId())) return;

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

        processedEventRepository.save(new ProcessedEvent(e.eventId()));
    }

}

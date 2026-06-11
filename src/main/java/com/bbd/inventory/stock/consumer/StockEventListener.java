package com.bbd.inventory.stock.consumer;

import com.bbd.inventory.common.ProcessedEvent;
import com.bbd.inventory.common.ProcessedEventRepository;
import com.bbd.inventory.stock.command.StockCommandService;
import com.bbd.inventory.stock.event.ItemPriceChanged;
import com.bbd.inventory.stock.event.StockInRequested;
import com.bbd.inventory.stock.event.StockOutRequested;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockEventListener {

    private final ObjectMapper objectMapper;
    private final ProcessedEventRepository processedEventRepository;
    private final StockCommandService stockCommandService;

    @KafkaListener(topics = "sales.stock-out-requested", groupId = "stock-group")
    @Transactional
    public void onStockOut(String message) throws JsonProcessingException {

        StockOutRequested e = objectMapper.readValue(message, StockOutRequested.class);

        /*
            Implements idempotency (멱등성) for kafka message processing.
            Takes the events UUID to see if it already exists, and if it does halt.
         */
        if (processedEventRepository.existsByEventId(e.eventId())) return;

        stockCommandService.outbound(e);
        processedEventRepository.save(new ProcessedEvent(e.eventId()));
    }

    @KafkaListener(topics = "procurement.stock-in-requested", groupId = "stock-group")
    @Transactional
    public void onStockIn(String message) throws JsonProcessingException {

        StockInRequested e = objectMapper.readValue(message, StockInRequested.class);

        if (processedEventRepository.existsByEventId(e.eventId())) return;

        stockCommandService.inbound(e);
        processedEventRepository.save(new ProcessedEvent(e.eventId()));
    }

    @KafkaListener(topics = "item.price-changed", groupId = "stock-group")
    @Transactional
    public void onPriceChanged(String message) throws JsonProcessingException {
        ItemPriceChanged e = objectMapper.readValue(message, ItemPriceChanged.class);

        if (processedEventRepository.existsByEventId(e.eventId())) return;

        stockCommandService.updateUnitPrice(e);
        processedEventRepository.save(new ProcessedEvent(e.eventId()));
    }
}

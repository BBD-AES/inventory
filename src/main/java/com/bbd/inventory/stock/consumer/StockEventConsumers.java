package com.bbd.inventory.stock.consumer;

import com.bbd.inventory.stock.command.StockCommandService;
import com.bbd.inventory.stock.command.dto.AdjustStockCommand;
import com.bbd.inventory.stock.command.dto.ReceiveStockCommand;
import com.bbd.inventory.stock.command.dto.TransferStockCommand;
import com.bbd.inventory.stock.consumer.event.CountAdjusted;
import com.bbd.inventory.stock.consumer.event.GoodsInbound;
import com.bbd.inventory.stock.consumer.event.StockTransferRequested;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;

import java.util.function.Consumer;

/**
 * Inbound event consumers — inventory's reaction door for facts OTHER services own.
 * There is no REST POST for transfer / receive / adjust: the fact is born upstream
 * (Sales approval, Procurement delivery, Stocktake count) and arrives as an event.
 * These beans replace the deleted POST endpoints (see PROCUREMENT_INBOUND.md, EDA.md §1).
 *
 * Spring Cloud Stream functional model (CONVENTION §8.2, last bullet — "add a Consumer<T>
 * bean only when inventory must react to another service's event"). The bean NAME is the
 * binding prefix: bean `goodsInbound` <- binding `goodsInbound-in-0` <- topic
 * `procurement.goods-inbound` (mapping lives in application.yml, not here).
 *
 * Each bean is a THIN seam: it pulls the de-dupe key + tenant from the headers, hands a
 * typed command to {@link StockCommandService}, and returns. The real work — invariant
 * checks, the stock_movement insert, the matching outbox row, and the processed_events
 * de-dupe — all happen inside ONE @Transactional command (CONVENTION §6, §11, §12).
 * Keep state changes, outbox writes, and broker calls OUT of these beans.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class StockEventConsumers {

    private final StockCommandService stockCommandService;

    /**
     * The @Bean return type Consumer<Message<T>> is the entire contract with the framework.
     * Spring Cloud Stream sees a bean named goodsInbound and automatically looks for a
     * binding named goodsInbound-in-0 in application.yml, which maps to the actual broker
     * topic. This means the binding is configuration, not code.
     * You can reroute the consumer to a different topic without touching this file.
     * The bean name is the binding prefix — that's the convention being leveraged here.
     * 34
     *  procurement.goods-inbound -> +quantity stock_movement (RECEIVE). EDA.md §7 box ④.
     *  */
    @Bean
    public Consumer<Message<GoodsInbound>> goodsInbound() {
        return message -> {
            GoodsInbound e = message.getPayload();
            MessageHeaders h = message.getHeaders();
            log.info("consume GoodsInbound po={} sku={} wh={} qty={}",
                    e.poNumber(), e.sku(), e.warehouseCode(), e.quantity());

            stockCommandService.receive(new ReceiveStockCommand(
                    eventId(h), tenancyId(h),
                    e.poNumber(), e.sku(), e.warehouseCode(), e.quantity()));
        };
    }

    /** inventory.stock-transfer-requested -> paired out/in stock_movements. EDA.md STEP 5. */
    @Bean
    public Consumer<Message<StockTransferRequested>> stockTransferRequested() {
        return message -> {
            StockTransferRequested e = message.getPayload();
            MessageHeaders h = message.getHeaders();
            log.info("consume StockTransferRequested sku={} {} -> {} qty={}",
                    e.sku(), e.fromWarehouseCode(), e.toWarehouseCode(), e.quantity());

            stockCommandService.transfer(new TransferStockCommand(
                    eventId(h), tenancyId(h),
                    e.sku(), e.fromWarehouseCode(), e.toWarehouseCode(), e.quantity()));
        };
    }

    /** stocktake.count-adjusted -> signed stock_movement (counted - current). */
    @Bean
    public Consumer<Message<CountAdjusted>> countAdjusted() {
        return message -> {
            CountAdjusted e = message.getPayload();
            MessageHeaders h = message.getHeaders();
            log.info("consume CountAdjusted sku={} wh={} counted={}",
                    e.sku(), e.warehouseCode(), e.countedQuantity());

            stockCommandService.adjust(new AdjustStockCommand(
                    eventId(h), tenancyId(h),
                    e.sku(), e.warehouseCode(), e.countedQuantity(), e.reason()));
        };
    }

    private static String eventId(MessageHeaders h) {
        return asString(h.get(EventHeaders.EVENT_ID));
    }

    private static String tenancyId(MessageHeaders h) {
        return asString(h.get(EventHeaders.TENANCY_ID));
    }

    private static String asString(Object v) {
        return v == null ? null : v.toString();
    }
}

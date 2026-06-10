package com.bbd.inventory.stock.command;

import com.bbd.inventory.global.error.ApiException;
import com.bbd.inventory.global.error.dto.ErrorCode;
import com.bbd.inventory.stock.ItemClient;
import com.bbd.inventory.stock.ItemInfo;
import com.bbd.inventory.stock.Stock;
import com.bbd.inventory.stock.StockRepository;
import com.bbd.inventory.stock.command.dto.AdjustStockCommand;
import com.bbd.inventory.stock.command.dto.CreateStockCommand;
import com.bbd.inventory.stock.command.dto.ReceiveStockCommand;
import com.bbd.inventory.stock.command.dto.StockLocationResponse;
import com.bbd.inventory.stock.command.dto.TransferStockCommand;
import com.bbd.inventory.stock.command.dto.UpdateLocationCommand;
import com.bbd.inventory.warehouse.Warehouse;
import com.bbd.inventory.warehouse.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StockCommandService {

    private final StockRepository stockRepository;
    private final WarehouseRepository warehouseRepository;      // cross-context read
    private final ItemClient itemClient;                        // cross-service read

    // TODO: implement stock command operations (inbound, outbound, adjust, reserve).

    @Transactional
    public Long createStock(CreateStockCommand cmd) {
        // resolve the warehouse (local, authoritative to inventory)
        Warehouse warehouse = warehouseRepository.findByCode(cmd.warehouseCode())
                .orElseThrow(() -> new ApiException(ErrorCode.WAREHOUSE_NOT_FOUND));

        // one stock row per (warehouse, sku) -- guard duplicates
        if (stockRepository.existsByWarehouseAndSku(warehouse, cmd.sku())) {
            throw new ApiException(ErrorCode.STOCK_ALREADY_EXISTS);
        }

        // SYNC cross-service validation + fetch the catalog facts to denormalize
        ItemInfo item = itemClient.getBySku(cmd.sku())
                .orElseThrow(() -> new ApiException(ErrorCode.SKU_NOT_FOUND));
        return 0L;
    }

    @Transactional
    public StockLocationResponse changeLocation(
            String sku, String warehouseCode, UpdateLocationCommand cmd) {

        Stock stock = stockRepository.findBySkuAndWarehouseCode(sku, warehouseCode)
                .orElseThrow(() -> new ApiException(ErrorCode.STOCK_NOT_FOUND));

        // entity method; dirty-checked, no movement
        stock.changeLocation(cmd.location());

        stockRepository.flush();

        return new StockLocationResponse(
                sku, warehouseCode, stock.getLocation(), stock.getUpdatedAt()
        );
    }

    // ---------------------------------------------------------------------------
    // Event-driven commands — triggered by StockEventConsumers, not by any REST POST.
    // The fact is owned upstream; inventory only RECORDS the resulting stock_movement.
    //
    // Each method is ONE @Transactional unit that must (CONVENTION §6, §11, §12):
    //   0. de-dupe: insert processed_events(eventId) — unique constraint rejects a replay
    //      (at-least-once delivery means the same event can arrive twice). Skip if present.
    //   1. resolve the authoritative stock row(s) (already local to inventory)
    //   2. mutate through an entity method that enforces availableStock >= 0 + @Version
    //      and produces an append-only StockMovement (the strong source)
    //   3. outbox.append(StockMovementRecorded.from(movement)) in this SAME tx
    // No broker / HTTP / @Async call here — the relay publishes out of band (§8).
    // ---------------------------------------------------------------------------

    /** GoodsInbound -> +quantity at warehouse. Vendor delivery, owned by Procurement. */
    @Transactional
    public void receive(ReceiveStockCommand cmd) {
        Stock stock = stockRepository.findBySkuAndWarehouseCode(cmd.sku(), cmd.warehouseCode())
                .orElseThrow(() -> new ApiException(ErrorCode.STOCK_NOT_FOUND));
        // TODO §12 de-dupe on cmd.eventId(); skip if already processed.
        // TODO entity method: stock.receive(cmd.quantity()) -> append-only StockMovement(+qty).
        // TODO outbox.append(StockMovementRecorded.from(movement)) in this tx.
    }

    /** StockTransferRequested -> -qty at source, +qty at target. Owned by Sales (HQ). */
    @Transactional
    public void transfer(TransferStockCommand cmd) {
        Stock from = stockRepository.findBySkuAndWarehouseCode(cmd.sku(), cmd.fromWarehouseCode())
                .orElseThrow(() -> new ApiException(ErrorCode.STOCK_NOT_FOUND));
        Stock to = stockRepository.findBySkuAndWarehouseCode(cmd.sku(), cmd.toWarehouseCode())
                .orElseThrow(() -> new ApiException(ErrorCode.STOCK_NOT_FOUND));
        // TODO §12 de-dupe on cmd.eventId().
        // TODO from.issue(cmd.quantity()) (enforces availableStock >= 0) + to.receive(cmd.quantity());
        //      two paired append-only StockMovements (OUT then IN).
        // TODO outbox.append(...) for each movement, this tx.
    }

    /** CountAdjusted -> set stock to the counted figure via a signed correction movement. */
    @Transactional
    public void adjust(AdjustStockCommand cmd) {
        Stock stock = stockRepository.findBySkuAndWarehouseCode(cmd.sku(), cmd.warehouseCode())
                .orElseThrow(() -> new ApiException(ErrorCode.STOCK_NOT_FOUND));
        // TODO §12 de-dupe on cmd.eventId().
        // int amountDiff = cmd.countedQuantity() - stock.getCurrentStock();
        // TODO stock.adjust(amountDiff, cmd.reason()) -> signed append-only StockMovement.
        // TODO outbox.append(StockMovementRecorded.from(movement)) in this tx.
    }

}

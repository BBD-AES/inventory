package com.bbd.inventory.stock.command;

import com.bbd.inventory.global.error.ApiException;
import com.bbd.inventory.global.error.dto.ErrorCode;
import com.bbd.inventory.stock.*;
import com.bbd.inventory.stock.command.dto.AdjustStockCommand;
import com.bbd.inventory.stock.command.dto.StockAdjustResponse;
import com.bbd.inventory.stock.command.dto.StockLocationResponse;
import com.bbd.inventory.stock.command.dto.StockOutboundRequest;
import com.bbd.inventory.stock.command.dto.UpdateLocationCommand;
import com.bbd.inventory.stock.event.StockInRequested;
import com.bbd.inventory.stock.event.StockOutRequested;
import com.bbd.inventory.warehouse.Warehouse;
import com.bbd.inventory.warehouse.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockCommandService {

    private final StockRepository stockRepository;
    private final WarehouseRepository warehouseRepository;      // cross-context read
    private final StockMovementRepository stockMovementRepository;

    @Transactional
    public StockLocationResponse changeLocation(
            String sku, String warehouseCode, UpdateLocationCommand cmd) {

        Stock stock = stockRepository.findForUpdate(sku, warehouseCode)
                .orElseThrow(() -> new ApiException(ErrorCode.STOCK_NOT_FOUND));

        // entity method; dirty-checked, no movement
        stock.changeLocation(cmd.location());

        stockRepository.flush();

        return new StockLocationResponse(
                sku, warehouseCode, stock.getLocation(), stock.getUpdatedAt()
        );
    }

    /**
     * Internal stock correction: set currentStock to the target and record an ADJUST movement
     * (signed delta) so the ledger stays consistent (Σ IN − Σ OUT + Σ ADJUST = currentStock).
     */
    @Transactional
    public StockAdjustResponse adjust(
            String sku, String warehouseCode, AdjustStockCommand cmd) {

        Stock stock = stockRepository.findForUpdate(sku, warehouseCode)
                .orElseThrow(() -> new ApiException(ErrorCode.STOCK_NOT_FOUND));

        int delta = stock.correctTo(cmd.currentStock());

        stockMovementRepository.save(StockMovement.builder()
                .sku(sku)
                .warehouseCode(warehouseCode)
                .type(MovementType.ADJUST)
                .quantity(delta)                       // signed: +/- correction
                .unitPrice(stock.getUnitPrice())       // current replica price as snapshot
                .referenceNumber(cmd.referenceNumber())
                .build());

        stockRepository.flush();

        return new StockAdjustResponse(
                sku, warehouseCode, stock.getCurrentStock(),
                stock.getAvailableStock(), delta, stock.getUpdatedAt());
    }

    @Transactional
    public void outbound(StockOutRequested e) {
        // for each outbound lines
        for (StockOutRequested.Line line : e.lines()) {
            Stock stock = stockRepository.findForUpdate(line.sku(), line.warehouseCode())
                    .orElseThrow(() -> new ApiException(ErrorCode.STOCK_NOT_FOUND));

            stock.applyOut(line.quantity());
            stockMovementRepository.save(StockMovement.builder()
                    .sku(line.sku())
                    .warehouseCode(line.warehouseCode())
                    .type(MovementType.OUT)
                    .quantity(line.quantity())
                    .unitPrice(line.unitPrice())
                    .referenceNumber(e.soNumber())
                    .build());
        }
    }

    /**
     * 수주 종료 출고(#69, 동기 REST). sales 가 CO 종료 시 지정 창고 재고를 차감.
     * - referenceNumber(coNumber) 기준 멱등: 이미 OUT 기록이 있으면 재처리 안 함(재시도/중복 안전).
     * - findForUpdate 비관락으로 라인별 차감, 부족 시 applyOut 이 INSUFFICIENT_STOCK(409) → 전체 롤백.
     * 예약(reservation) 없는 직접 출고라 applyOut(current·available 동시 차감)을 사용 — 예약 기반 issue 와 구분.
     */
    @Transactional
    public void outbound(StockOutboundRequest req) {
        if (stockMovementRepository.existsByReferenceNumberAndType(req.referenceNumber(), MovementType.OUT)) {
            return; // 이미 출고됨 — 멱등 재시도(no-op)
        }
        for (StockOutboundRequest.Line line : req.lines()) {
            Stock stock = stockRepository.findForUpdate(line.sku(), line.warehouseCode())
                    .orElseThrow(() -> new ApiException(ErrorCode.STOCK_NOT_FOUND));

            stock.applyOut(line.quantity());
            stockMovementRepository.save(StockMovement.builder()
                    .sku(line.sku())
                    .warehouseCode(line.warehouseCode())
                    .type(MovementType.OUT)
                    .quantity(line.quantity())
                    .unitPrice(line.unitPrice())
                    .referenceNumber(req.referenceNumber())
                    .build());
        }
    }

    /*
        procurement.stock-in-requested -> +qty (create if missing) +
        movement(IN).
     */
    @Transactional
    public void inbound(StockInRequested e) {

        for (StockInRequested.Line line : e.lines()) {
            Stock stock = stockRepository.findForUpdate(line.sku(), line.warehouseCode())
                    .orElseGet(() -> createForInbound(line));

            stock.applyIn(line.quantity());
            stockMovementRepository.save(StockMovement.builder()
                    .sku(line.sku())
                    .warehouseCode(line.warehouseCode())
                    .type(MovementType.IN)
                    .quantity(line.quantity())
                    .unitPrice(line.unitPrice())
                    .referenceNumber(e.poNumber())
                    .build());
        }

    }

    private Stock createForInbound(StockInRequested.Line line) {

        Warehouse warehouse = warehouseRepository.findByCode(line.warehouseCode())
                .orElseThrow(() -> {
                    // Inbound references a warehouse inventory doesn't know — receive will roll back
                    // (-> DLT). Name the bad code so the procurement contract mismatch is diagnosable.
                    log.warn("inbound rejected: unknown warehouseCode={} (sku={}); "
                                    + "register the warehouse before receiving into it",
                            line.warehouseCode(), line.sku());
                    return new ApiException(ErrorCode.WAREHOUSE_NOT_FOUND);
                });

        // Item metadata is event-carried (procurement enriches the line); no cross-service call.
        return stockRepository.save(Stock.builder()
                .warehouse(warehouse)
                .sku(line.sku())
                .name(line.name())
                .unit(line.unit())
                .currentStock(0)
                .availableStock(0)
                .safetyStock(line.safetyStock())
                .category(line.category())
                .location(null)
                .unitPrice(line.unitPrice())
                .build());
    }

    @Transactional
    public void updateUnitPrice(String sku, int unitPrice) {
        stockRepository.updateUnitPriceBySku(sku, unitPrice);
    }

    @Transactional
    public void updateActive(String sku, boolean active) {
        stockRepository.updateActiveBySku(sku, active);
    }
}

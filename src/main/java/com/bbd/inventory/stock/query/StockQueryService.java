package com.bbd.inventory.stock.query;

import com.bbd.inventory.common.BranchScope;
import com.bbd.inventory.common.PageResponse;
import com.bbd.inventory.global.error.ApiException;
import com.bbd.inventory.global.error.dto.ErrorCode;
import com.bbd.inventory.stock.Stock;
import com.bbd.inventory.stock.query.dto.*;
import com.bbd.inventory.warehouse.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StockQueryService {

    private final StockQueryRepository stockQueryRepository;
    // cross-context read, filter validation only
    private final WarehouseRepository warehouseRepository;
    private final BranchScope branchScope;


    // 재고 조회
    @Transactional(readOnly = true)
    public PageResponse<StockListItemResponse> search(StockSearchCondition cond, Pageable pageable) {

        // BRANCH caller is locked to its own warehouse; HQ/ADMIN keep the client filter.
        String scope = branchScope.warehouseCodeFilter();
        String warehouseCode = scope != null ? scope : cond.warehouseCode();

        if (scope == null && warehouseCode != null
                && !warehouseRepository.existsByCode(warehouseCode)) {
            throw new ApiException(ErrorCode.INVALID_FILTER);
        }

        boolean belowSafety = Boolean.TRUE.equals(cond.belowSafety());

        return PageResponse.from(
                stockQueryRepository.search(
                        warehouseCode,
                        cond.sku(),
                        cond.category(),
                        belowSafety,
                        pageable
                ).map(StockListItemResponse::from)
        );
    }

    @Transactional(readOnly = true)
    public StockResponse getOne(String sku, String warehouseCode) {
        return stockQueryRepository
                .findBySkuAndWarehouseCode(sku, warehouseCode)
                .map(StockResponse::from)
                .orElseThrow(() -> new ApiException(ErrorCode.STOCK_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public StockSummaryResponse summary() {
        List<WarehouseStockView> warehouse = stockQueryRepository.warehouseStockSummary();
        StockKpiView kpi = stockQueryRepository.stockKpis();

        List<StockSummaryResponse.WarehouseStock> warehouses = warehouse.stream()
                .map(v -> new StockSummaryResponse.WarehouseStock(
                        v.getCode(), v.getName(), v.getType(), true,
                        v.getSkuCount(), v.getCurrentStock()))
                .toList();

        return new StockSummaryResponse(
                warehouseRepository.count(),        // all
                warehouses.size(),                  // active
                kpi.getTotalSkus(),
                kpi.getTotalCurrentStock(),
                kpi.getTotalAvailableStock(),
                kpi.getBelowSafetyCount(),
                warehouses
        );
    }

    /**
     * Dashboard aggregation (#41): safety-stock shortages (availableStock < safetyStock)
     * plus per-warehouse available-stock sum and below-safety SKU count. Two grouped/filtered
     * queries, no per-row work here.
     */
    @Transactional(readOnly = true)
    public StockStatsResponse stats() {
        List<SafetyShortageView> shortages = stockQueryRepository.safetyShortages();
        List<WarehouseAvailabilityView> byWarehouse = stockQueryRepository.warehouseAvailability();

        List<StockStatsResponse.SafetyShortage.Item> items = shortages.stream()
                .map(v -> new StockStatsResponse.SafetyShortage.Item(
                        v.getSku(), v.getWarehouseCode(), v.getCurrentStock(),
                        v.getAvailableStock(), v.getSafetyStock(), v.getShortage()))
                .toList();

        List<StockStatsResponse.ByWarehouse> warehouses = byWarehouse.stream()
                .map(v -> new StockStatsResponse.ByWarehouse(
                        v.getWarehouseCode(), v.getAvailableSum(), v.getShortageCount()))
                .toList();

        return new StockStatsResponse(
                new StockStatsResponse.SafetyShortage(items.size(), items),
                warehouses
        );
    }

    @Transactional(readOnly = true)
    public StockDetailResponse getDetail(String sku) {
        List<Stock> rows = stockQueryRepository.findAllBySku(sku);
        if (rows.isEmpty()) throw new ApiException(ErrorCode.STOCK_NOT_FOUND);

        return StockDetailResponse.from(rows);
    }

    /*
        gets the list of available skus in a list.
     */
    @Transactional(readOnly = true)
    public List<AvailabilityResponse> availability(List<String> skus) {
        return skus.stream()
                .map(sku -> AvailabilityResponse.from(sku, stockQueryRepository.findAllBySku(sku)))
                .toList();
    }

}

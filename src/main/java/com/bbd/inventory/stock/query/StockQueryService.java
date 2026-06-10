package com.bbd.inventory.stock.query;

import com.bbd.inventory.common.PageResponse;
import com.bbd.inventory.global.error.ApiException;
import com.bbd.inventory.global.error.dto.ErrorCode;
import com.bbd.inventory.stock.query.dto.StockResponse;
import com.bbd.inventory.stock.query.dto.StockSearchCondition;
import com.bbd.inventory.warehouse.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StockQueryService {

    private final StockQueryRepository stockQueryRepository;
    // cross-context read, filter validation only
    private final WarehouseRepository warehouseRepository;

    // TODO: implement stock query operations (list by warehouse, get by sku) with
    //       @Transactional(readOnly = true), returning StockResponse / PageResponse.


    // 재고 조회
    @Transactional(readOnly = true)
    public PageResponse<StockResponse> search(StockSearchCondition cond, Pageable pageable) {

        if (cond.warehouseCode() != null
                && !warehouseRepository.existsByCode(cond.warehouseCode())) {
            throw new ApiException(ErrorCode.INVALID_FILTER);
        }

        boolean belowSafety = Boolean.TRUE.equals(cond.belowSafety());

        return PageResponse.from(
                stockQueryRepository.search(
                        cond.warehouseCode(),
                        cond.sku(),
                        cond.category(),
                        belowSafety,
                        pageable
                ).map(StockResponse::from)
        );
    }

    @Transactional(readOnly = true)
    public StockResponse getOne(String sku, String warehouseCode) {
        return stockQueryRepository
                .findBySkuAndWarehouseCode(sku, warehouseCode)
                .map(StockResponse::from)
                .orElseThrow(() -> new ApiException(ErrorCode.STOCK_NOT_FOUND));
    }
}

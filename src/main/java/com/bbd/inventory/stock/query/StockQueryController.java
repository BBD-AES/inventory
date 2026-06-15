package com.bbd.inventory.stock.query;

import com.bbd.inventory.common.PageResponse;
import com.bbd.inventory.stock.query.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/stocks")
@RequiredArgsConstructor
public class StockQueryController {

    private final StockQueryService queryService;

    @GetMapping
    public PageResponse<StockListItemResponse> search(
            @ModelAttribute StockSearchCondition cond,
            @PageableDefault(size = 20)Pageable pageable) {
        return queryService.search(cond, pageable);
    }

    // 단건 조회
    @GetMapping("/{warehouseCode}/{sku}")
    public StockResponse getOne(
            @PathVariable String sku,
            @PathVariable String warehouseCode) {

        return queryService.getOne(sku, warehouseCode);
    }

    @GetMapping("/summary")
    public StockSummaryResponse summary() {
        return queryService.summary();
    }

    @GetMapping("/{sku}")
    public StockDetailResponse getDetail(@PathVariable String sku) {
        return queryService.getDetail(sku);
    }

}

package com.bbd.inventory.stock.query;

import com.bbd.inventory.common.PageResponse;
import com.bbd.inventory.stock.query.dto.*;
//import com.bbd.securitycore.adapter.in.annotation.RequireRole;
//import com.bbd.securitycore.domain.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    // literal /stats outranks /{sku} in Spring path matching — no ordering needed
    @GetMapping("/stats")
    public StockStatsResponse stats() {
        return queryService.stats();
    }

    @GetMapping("/{sku}")
    public StockDetailResponse getDetail(@PathVariable String sku) {
        return queryService.getDetail(sku);
    }

    @GetMapping("/availability")
    public List<AvailabilityResponse> availability(@RequestParam List<String> sku) {
        return queryService.availability(sku);
    }
}

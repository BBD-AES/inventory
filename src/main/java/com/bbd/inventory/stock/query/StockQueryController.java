package com.bbd.inventory.stock.query;

import com.bbd.inventory.common.PageResponse;
import com.bbd.inventory.stock.query.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// 재고 조회 역할 게이트 복구 — 인증·ACTIVE 상태 + 역할 검증(security-core). 미부착 시 PENDING/INACTIVE도 조회 가능했음.
// 전 역할 읽기 허용(스코핑 아님). 지점=자기 창고만은 별도 후속(서비스단 이름축 스코핑).
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

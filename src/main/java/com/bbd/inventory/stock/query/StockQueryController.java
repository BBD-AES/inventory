package com.bbd.inventory.stock.query;

import com.bbd.inventory.common.PageResponse;
import com.bbd.inventory.stock.query.dto.StockResponse;
import com.bbd.inventory.stock.query.dto.StockSearchCondition;
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
    public PageResponse<StockResponse> search(
            @ModelAttribute StockSearchCondition cond,
            @PageableDefault(size = 20)Pageable pageable) {
        return queryService.search(cond, pageable);
    }
}

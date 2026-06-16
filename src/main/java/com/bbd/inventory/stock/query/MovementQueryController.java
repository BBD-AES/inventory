package com.bbd.inventory.stock.query;

import com.bbd.inventory.common.PageResponse;
import com.bbd.inventory.stock.query.dto.MovementResponse;
import com.bbd.inventory.stock.query.dto.MovementSearchCondition;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.awt.print.Pageable;

@RestController
@RequestMapping("/api/v1/stock-movements")
@RequiredArgsConstructor
public class MovementQueryController {

    private final MovementQueryService queryService;

    @GetMapping
    public PageResponse<MovementResponse> search(
            @ModelAttribute MovementSearchCondition cond,
            @PageableDefault(size = 20, sort = "occurredAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return queryService.search(cond, pageable);
    }

    @GetMapping("/{id}")
    public MovementResponse getById(@PathVariable Long id) {
        return queryService.getById(id);
    }
}

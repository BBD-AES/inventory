package com.bbd.inventory.warehouse.query;

import com.bbd.inventory.common.PageResponse;
import com.bbd.inventory.warehouse.WarehouseType;
import com.bbd.inventory.warehouse.query.dto.WarehouseResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/warehouses")
@RequiredArgsConstructor
public class WarehouseQueryController {

    private final WarehouseQueryService queryService;

    @GetMapping("/{code}")
    public WarehouseResponse get(@PathVariable String code) {
        return queryService.getByCode(code);
    }

    @GetMapping
    public PageResponse<WarehouseResponse> list(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false)WarehouseType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
            ) {
        return queryService.list(active, type, PageRequest.of(page, size));
    }
}

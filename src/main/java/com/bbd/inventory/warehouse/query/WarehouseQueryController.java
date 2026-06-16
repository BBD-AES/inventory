package com.bbd.inventory.warehouse.query;

import com.bbd.inventory.common.PageResponse;
import com.bbd.inventory.warehouse.WarehouseType;
import com.bbd.inventory.warehouse.query.dto.WarehouseResponse;
import com.bbd.securitycore.adapter.in.annotation.RequireRole;
import com.bbd.securitycore.domain.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/warehouses")
@RequiredArgsConstructor
public class WarehouseQueryController {

    private final WarehouseQueryService queryService;

    // literal /active outranks /{code} in Spring path matching — no ordering needed
    @GetMapping("/active")
    public List<WarehouseResponse> listActive() {
        return queryService.listActive();
    }

    @GetMapping("/{code}")
    public WarehouseResponse get(@PathVariable String code) {
        return queryService.getByCode(code);
    }

    @RequireRole(UserRole.BRANCH_MANAGER)
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

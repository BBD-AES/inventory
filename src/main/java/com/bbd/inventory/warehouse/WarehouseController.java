package com.bbd.inventory.warehouse;

import com.bbd.inventory.common.PageResponse;
import com.bbd.inventory.global.error.dto.WarehouseCreateRequest;
import com.bbd.inventory.global.error.dto.WarehouseUpdateRequest;
import com.bbd.inventory.warehouse.dto.WarehouseResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/warehouses")
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseService warehouseService;

    @GetMapping
    public PageResponse<WarehouseResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return warehouseService.list(PageRequest.of(page, size));
    }

    @GetMapping("/{code}")
    public WarehouseResponse get(@PathVariable String code) {
        return warehouseService.getByCode(code);
    }

    /*
        @Valid validifies the WarehouseCreateRequest req once it is created.

     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WarehouseResponse create(@Valid @RequestBody WarehouseCreateRequest req) {
        return warehouseService.create(req);
    }

    @PutMapping("/{code}")
    public WarehouseResponse update(@PathVariable String code,
                                    @Valid @RequestBody WarehouseUpdateRequest req) {
        return warehouseService.update(code, req);
    }

    @PatchMapping("/{code}/deactivate")
    public WarehouseResponse deactivate(@PathVariable String code) {
        return warehouseService.deactivate(code);
    }

    @PatchMapping("/{code}/activate")
    public WarehouseResponse activate(@PathVariable String code) {
        return warehouseService.activate(code);
    }
}

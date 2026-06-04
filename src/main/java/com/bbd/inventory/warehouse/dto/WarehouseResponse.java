package com.bbd.inventory.warehouse.dto;

import com.bbd.inventory.warehouse.Warehouse;
import com.bbd.inventory.warehouse.WarehouseType;

public record WarehouseResponse (
        String code,
        String name,
        WarehouseType type,
        String address,
        boolean active
) {
    public static WarehouseResponse from(Warehouse w) {
        return new WarehouseResponse(
                w.getCode(), w.getName(), w.getType(), w.getAddress(), w.isActive()
        );
    }
}

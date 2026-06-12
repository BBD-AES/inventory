package com.bbd.inventory.stock.query.dto;

import com.bbd.inventory.warehouse.WarehouseType;

public interface WarehouseStockView {
    String getCode();
    String getName();
    WarehouseType getType();
    long getSkuCount();
    long getCurrentStock();
}

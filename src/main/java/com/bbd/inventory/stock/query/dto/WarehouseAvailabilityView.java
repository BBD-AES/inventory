package com.bbd.inventory.stock.query.dto;

/**
 * Per-warehouse aggregate for the stats dashboard:
 * available-stock sum and the count of SKUs below safety (availableStock < safetyStock).
 */
public interface WarehouseAvailabilityView {
    String getWarehouseCode();
    long getAvailableSum();
    long getShortageCount();
}

package com.bbd.inventory.stock.query.dto;

/**
 * Row-level projection for safety-stock shortage (availableStock < safetyStock).
 * shortage = safetyStock - availableStock, computed in SQL so the read returns ready-to-render.
 */
public interface SafetyShortageView {
    String getSku();
    String getWarehouseCode();
    int getCurrentStock();
    int getAvailableStock();
    int getSafetyStock();
    int getShortage();
}

package com.bbd.inventory.stock.query.dto;

public interface StockKpiView {
    long getTotalSkus();
    long getTotalCurrentStock();
    long getTotalAvailableStock();
    long getBelowSafetyCount();
}

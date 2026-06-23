package com.bbd.inventory.stock.query.dto;


import com.bbd.inventory.stock.Stock;

import java.time.Instant;

/**
 * 재고 목록 행 응답. 단건 {@link StockResponse} 와 동일 필드(parity) — 모바일/콘솔 재고 목록 화면이
 * 단건 조회 없이도 카테고리·단위·가용/안전재고·단가·로케이션을 바로 표시할 수 있게 보강.
 * (기존엔 sku·name·currentStock·warehouseCode 4개뿐이라 목록 화면 연동 불가)
 */
public record StockListItemResponse(
        String sku,
        String name,
        String category,
        String unit,
        String warehouseCode,
        int currentStock,
        int availableStock,
        int safetyStock,
        int unitPrice,
        String location,
        Instant updatedAt
) {
    public static StockListItemResponse from(Stock s) {
        return new StockListItemResponse(
                s.getSku(),
                s.getName(),
                s.getCategory(),
                s.getUnit(),
                s.getWarehouse().getCode(),
                s.getCurrentStock(),
                s.getAvailableStock(),
                s.getSafetyStock(),
                s.getUnitPrice(),
                s.getLocation(),
                s.getUpdatedAt()
        );
    }
}

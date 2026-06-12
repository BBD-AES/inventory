package com.bbd.inventory.stock.query.dto;


import com.bbd.inventory.stock.Stock;

import java.time.Instant;
import java.util.List;

/*
    아이템 이랑 재고 가 Inventory/Item 서비스로 분리되있음.
    인벤토리가 아이템을 조회할때마다 호출 하면 느려서 이벤트 기반으로 dto 를 받아,
    Stock 테이블에 복사해놈.

    내부 record 를 써서 Stock 자체를 노출 안시킴.
 */

public record StockDetailResponse(
        String sku,
        String name,
        String category,
        String unit,
        int safetyStock,
        int unitPrice,
        List<WarehouseLine> warehouses
) {
    public record WarehouseLine(
            String warehouseCode,
            int currentStock,
            int availableStock,
            String location,
            Instant updatedAt
    ) {}

    public static StockDetailResponse from(List<Stock> rows) {
        // get(0) => getFirst() item-level fields are the same across warehouses (비정규화)
        Stock first = rows.getFirst();
        List<WarehouseLine> lines = rows.stream()
                .map(s -> new WarehouseLine(s.getWarehouse().getCode(),
                        s.getCurrentStock(), s.getAvailableStock(), s.getLocation(),
                        s.getUpdatedAt())).toList();

        return new StockDetailResponse(first.getSku(), first.getName(),
                first.getCategory(), first.getUnit(), first.getSafetyStock(),
                first.getUnitPrice(), lines);
    }
}

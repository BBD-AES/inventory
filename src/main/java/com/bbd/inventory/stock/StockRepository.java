package com.bbd.inventory.stock;

import com.bbd.inventory.warehouse.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockRepository extends JpaRepository<Stock, Long> {

    /*
        SELECT EXISTS (
        SELECT 1
        FROM stock
        WHERE warehouse_code = ?
        AND current_stock > ?
        );
     */
    boolean existsByWarehouseCodeAndCurrentStockGreaterThan(String warehouseCode, int threshold);
    boolean existsByWarehouseAndSku(Warehouse warehouse, String sku);
}

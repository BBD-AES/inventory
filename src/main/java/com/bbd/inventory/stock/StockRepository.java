package com.bbd.inventory.stock;

import com.bbd.inventory.warehouse.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

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

    @Query("""
            SELECT s FROM Stock s
            WHERE s.sku = :sku AND s.warehouse.code = :warehouseCode
            """)
    Optional<Stock> findBySkuAndWarehouseCode(
            @Param("sku") String sku,
            @Param("warehouseCode") String warehouseCode);

}

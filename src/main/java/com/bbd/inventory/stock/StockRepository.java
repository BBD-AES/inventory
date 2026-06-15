package com.bbd.inventory.stock;

import com.bbd.inventory.warehouse.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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


    /*
        because the QUERY changes data, and Spring Data normally assumes that
        queries are SELECT, so in order to do UPDATE, DELETE, INSERT, @Modifying is used.
     */

    @Modifying
    @Query("""
            UPDATE Stock s
            SET s.unitPrice = :unitPrice
            WHERE s.sku = :sku
            """)
    int updateUnitPriceBySku(@Param("sku") String sku,
                             @Param("unitPrice") int unitPrice);

}

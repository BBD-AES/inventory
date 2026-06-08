package com.bbd.inventory.stock.query;

import com.bbd.inventory.stock.Stock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface
StockQueryRepository extends JpaRepository<Stock, Long> {

    /**
     * All filters pushed to SQL so pagination counts stay correct and the
     * <60ms p99 budget holds (no per-request cross-service call).
     * A null filter param disables that predicate; belowSafety=false disables the safety filter.
     * JOIN FETCH on warehouse avoids N+1 when mapping warehouseCode; separate countQuery
     * because a fetch join cannot be counted.
     */
    @Query(value = """
            SELECT s FROM Stock s JOIN FETCH s.warehouse w
            WHERE (:warehouseCode IS NULL OR w.code = :warehouseCode)
              AND (:sku IS NULL OR s.sku = :sku)
              AND (:category IS NULL OR s.category = :category)
              AND (:belowSafety = false OR s.currentStock < s.safetyStock)
            """,
            countQuery = """
            SELECT COUNT(s) FROM Stock s
            WHERE (:warehouseCode IS NULL OR s.warehouse.code = :warehouseCode)
              AND (:sku IS NULL OR s.sku = :sku)
              AND (:category IS NULL OR s.category = :category)
              AND (:belowSafety = false OR s.currentStock < s.safetyStock)
            """)
    Page<Stock> search(
            @Param("warehouseCode") String warehouseCode,
            @Param("sku") String sku,
            @Param("category") String category,
            @Param("belowSafety") boolean belowSafety,
            Pageable pageable
    );

    /** One keyed row; warehouse fetched to avoid N+1 when mapping warehouseCode. */
    @Query("""
            SELECT s FROM Stock s JOIN FETCH s.warehouse w
            WHERE s.sku = :sku AND w.code = :warehouseCode
            """)
    Optional<Stock> findBySkuAndWarehouseCode(
            @Param("sku") String sku,
            @Param("warehouseCode") String warehouseCode);
}

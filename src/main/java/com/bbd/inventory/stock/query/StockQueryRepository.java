package com.bbd.inventory.stock.query;

import com.bbd.inventory.stock.Stock;
import com.bbd.inventory.stock.query.dto.SafetyShortageView;
import com.bbd.inventory.stock.query.dto.StockKpiView;
import com.bbd.inventory.stock.query.dto.WarehouseAvailabilityView;
import com.bbd.inventory.stock.query.dto.WarehouseStockView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
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

    /**
     *  stock summary.
     *  retrieves warehouse information and aggregates stock data.
     *  selecting warehouse code, name, and type (HQ, BRANCH)
     *
     *  COALESCE => returns first non-null value
     *  initial LEFT JOIN shows null warehouses as well.
     */

    @Query("""
            SELECT w.code AS code, w.name AS name, w.type AS type,
                COUNT(DISTINCT s.sku) AS skuCount,
                COALESCE(SUM(s.currentStock), 0) AS currentStock
            FROM Warehouse w LEFT JOIN Stock s ON s.warehouse = w
            WHERE w.active = true
              AND (:scope IS NULL OR w.code = :scope)
            GROUP BY w.code, w.name, w.type
            ORDER BY w.code
            """)
    List<WarehouseStockView> warehouseStockSummary(@Param("scope") String scope);

    /**
     * stock KPIs
     */
    @Query("""
            SELECT COUNT(DISTINCT s.sku) AS totalSkus,
                COALESCE(SUM(s.currentStock), 0) AS totalCurrentStock,
                COALESCE(SUM(s.availableStock), 0) AS totalAvailableStock,
                COALESCE(SUM(CASE WHEN s.currentStock < s.safetyStock
                            THEN 1 ELSE 0 END), 0) AS belowSafetyCount
            FROM Stock s
            WHERE s.warehouse.active = true
              AND (:scope IS NULL OR s.warehouse.code = :scope)
            """)
    StockKpiView stockKpis(@Param("scope") String scope);

    @Query("""
            SELECT s FROM Stock s JOIN FETCH s.warehouse w
            WHERE s.sku = :sku
            ORDER BY w.code
            """)
    List<Stock> findAllBySku(@Param("sku") String sku);

    /**
     * Dashboard (#41) — rows below safety on the available axis (availableStock < safetyStock).
     * shortage = safetyStock - availableStock computed in SQL so the read returns ready-to-render.
     */
    @Query("""
            SELECT s.sku AS sku, w.code AS warehouseCode,
                s.currentStock AS currentStock,
                s.availableStock AS availableStock,
                s.safetyStock AS safetyStock,
                (s.safetyStock - s.availableStock) AS shortage
            FROM Stock s JOIN s.warehouse w
            WHERE w.active = true
              AND s.availableStock < s.safetyStock
              AND (:scope IS NULL OR w.code = :scope)
            ORDER BY (s.safetyStock - s.availableStock) DESC, w.code, s.sku
            """)
    List<SafetyShortageView> safetyShortages(@Param("scope") String scope);

    /**
     * Dashboard (#41) — per-warehouse available-stock sum and below-safety SKU count
     * (availableStock < safetyStock). Grouped in SQL so no per-row work in the service.
     */
    @Query("""
            SELECT w.code AS warehouseCode,
                COALESCE(SUM(s.availableStock), 0) AS availableSum,
                SUM(CASE WHEN s.availableStock < s.safetyStock
                            THEN 1 ELSE 0 END) AS shortageCount
            FROM Stock s JOIN s.warehouse w
            WHERE w.active = true
              AND (:scope IS NULL OR w.code = :scope)
            GROUP BY w.code
            ORDER BY w.code
            """)
    List<WarehouseAvailabilityView> warehouseAvailability(@Param("scope") String scope);

}

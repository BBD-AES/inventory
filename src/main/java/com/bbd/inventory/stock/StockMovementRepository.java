package com.bbd.inventory.stock;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;


public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    /**
     * Paged movement ledger search. A null filter param disables that predicate;
     * from/to bound occurredAt inclusively. warehouseCode is a plain column (no relation),
     * so no JOIN FETCH and no separate countQuery — Spring derives the count.
     */
    @Query("""
            SELECT m FROM StockMovement m
            WHERE (:type IS NULL OR m.type = :type)
              AND (:sku IS NULL OR m.sku = :sku)
              AND (:warehouseCode IS NULL OR m.warehouseCode = :warehouseCode)
              AND (CAST(:from AS Instant) IS NULL OR m.occurredAt >= :from)
              AND (CAST(:to   AS Instant) IS NULL OR m.occurredAt <= :to)
            """)
    Page<StockMovement> search(
            @Param("type") MovementType type,
            @Param("sku") String sku,
            @Param("warehouseCode") String warehouseCode,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);
}

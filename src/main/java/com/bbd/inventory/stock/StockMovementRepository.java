package com.bbd.inventory.stock;



import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


import java.time.Instant;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    @Query("""
            SELECT m FROM StockMovement m
            WHERE (:movementType IS NULL OR m.type = :movementType)
            AND (:sku IS NULL OR m.sku = :sku)
            AND (:warehouseCode IS NULL OR m.warehouseCode = :warehouseCode)
            AND(:from IS NULL OR m.occurredAt >= :from)
            AND(:to IS NULL OR m.occurredAt <= :to)
            """)
    Page<StockMovement> search(
            @Param("movementType") MovementType movementType,
            @Param("sku") String sku,
            @Param("warehouseCode") String warehouseCode,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable
    );

    // 수주 종료 출고 멱등(#69): 같은 referenceNumber(coNumber)로 이미 OUT 기록이 있으면 재처리 차단.
    boolean existsByReferenceNumberAndType(String referenceNumber, MovementType type);

}

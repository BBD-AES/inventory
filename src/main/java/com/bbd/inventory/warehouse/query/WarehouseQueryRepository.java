package com.bbd.inventory.warehouse.query;

import com.bbd.inventory.warehouse.Warehouse;
import com.bbd.inventory.warehouse.WarehouseType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WarehouseQueryRepository extends JpaRepository<Warehouse, Long> {

    Optional<Warehouse> findByCode(String code);

    /** Active warehouses, code-ordered — non-paged convenience list (UI selectors). */
    List<Warehouse> findByActiveTrueOrderByCodeAsc();

    @Query("""
            SELECT w FROM Warehouse w
            WHERE (:active IS NULL OR w.active = :active)
            AND (:type IS NULL OR w.type = :type)
            """)
    Page<Warehouse> search(@Param("active") Boolean active,
                           @Param("type") WarehouseType type,
                           Pageable pageable);
}

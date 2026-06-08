package com.bbd.inventory.warehouse.query;

import com.bbd.inventory.warehouse.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WarehouseQueryRepository extends JpaRepository<Warehouse, Long> {

    Optional<Warehouse> findByCode(String code);
}

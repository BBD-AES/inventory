package com.bbd.inventory.warehouse;


import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {

    Optional<Warehouse> findByCode(String code);

    // BRANCH scoping: a branch user's tenancyName is the warehouse display name (e.g. "판교지점").
    Optional<Warehouse> findFirstByName(String name);

    boolean existsByCode(String code);
}

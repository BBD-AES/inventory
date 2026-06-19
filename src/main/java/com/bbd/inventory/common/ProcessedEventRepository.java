package com.bbd.inventory.common;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {

    @Modifying
    @Query(value = "INSERT INTO processed_event(event_id, processed_at) "
            +
            "VALUES (:id, now()) ON CONFLICT DO NOTHING",
            nativeQuery = true)
    int claim(@Param("id") String id);          // 1 = first time,  0 = duplicate
}

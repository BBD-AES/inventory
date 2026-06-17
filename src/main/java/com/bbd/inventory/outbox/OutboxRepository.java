package com.bbd.inventory.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    /*
        fetching oldest 100 unsent events..
     */
    List<OutboxEvent> findTop100ByOutboxStatusOrderByIdAsc(OutboxStatus status);
}

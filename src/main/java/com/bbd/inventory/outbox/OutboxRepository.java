package com.bbd.inventory.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    /*
        Oldest rows in the given status that are due now (nextRetryAt past, or null
        for legacy rows). Serves both the PENDING relay (backoff) and the FAILED
        re-drain (cooldown). Caller passes Pageable for the batch limit.
     */
    @Query("select o from OutboxEvent o "
            + "where o.outboxStatus = :status "
            + "and (o.nextRetryAt is null or o.nextRetryAt <= :cutoff) "
            + "order by o.id asc")
    List<OutboxEvent> findDue(@Param("status") OutboxStatus status,
                              @Param("cutoff") Instant cutoff,
                              Pageable pageable);
}

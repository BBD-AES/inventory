package com.bbd.inventory.stock.reservation;


import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {

    // idempotency replay
    Optional<StockReservation> findByRequestId(String requestId);

    // release/issue whole SO
    List<StockReservation> findBySoNumberAndStatus(String soNumber, ReservationStatus status);

    // reconcile.
    List<StockReservation> findByStatusAndExpiresAtBefore(ReservationStatus status, Instant cutoff);

}

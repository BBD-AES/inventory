package com.bbd.inventory.stock.reservation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationReconciler {

    private final ReservationService reservationService;

    /*
        Auto-release expired, un-issued reservations so abandoned SOs don't lock stock
     */
    @Scheduled(fixedDelay = 60000)
    public void releaseExpired() {
        int released = reservationService.releaseExpired();
        if (released > 0) log.info("reconcile: released {} expired reservations", released);
    }
    
}

package com.bbd.inventory.stock.reservation;

import com.bbd.inventory.stock.reservation.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/stocks/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping
    public ReserveResponse reserve(@Valid @RequestBody ReserveRequest req)
    {
        log.info("req 가 도착했습니다 !!! ⭐️⭐️⭐️⭐️⭐️ {}, {}, {}, {}", req.sku(), req.warehouseCode(), req.soNumber(), req.quantity());
        return reservationService.reserve(req);
    }

    @PostMapping("/{reservationId}/release")
    public ReleaseResponse release(@PathVariable Long reservationId) {
        return reservationService.releaseOne(reservationId);
    }

    @PostMapping("/release")
    public ReleaseResponse releaseBySo(@RequestParam String soNumber) {
        return reservationService.releaseBySoNumber(soNumber);
    }

    @PostMapping("/issue")
    public IssueResponse issue(@Valid @RequestBody IssueRequest req) {
        return reservationService.issue(req.soNumber());
    }
}

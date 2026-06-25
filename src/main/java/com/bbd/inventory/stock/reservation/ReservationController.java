package com.bbd.inventory.stock.reservation;

import com.bbd.inventory.stock.reservation.dto.*;
import com.bbd.securitycore.idempotency.Idempotent;
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

    // 멱등(공통 표준): @Idempotent = Idempotency-Key 헤더 재요청 Redis 빠른길.
    //   정확성 보루는 DB UNIQUE(uq_reservation_request) — 헤더 키를 dedup 키로 서비스에 넘긴다.
    //   (레거시 호출: 헤더 미전송 시 바디 requestId 폴백.)
    @PostMapping
    @Idempotent
    public ReserveResponse reserve(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ReserveRequest req)
    {
        log.info("req 가 도착했습니다 !!! ⭐️⭐️⭐️⭐️⭐️ {}, {}, {}, {}", req.sku(), req.warehouseCode(), req.soNumber(), req.quantity());
        return reservationService.reserve(req, idempotencyKey);
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
        return reservationService.issue(req.soNumber(), req.toWarehouseCode());
    }
}

package com.bbd.inventory.stock.reservation;

import com.bbd.inventory.global.error.ApiException;
import com.bbd.inventory.global.error.dto.ErrorCode;
import com.bbd.inventory.stock.*;
import com.bbd.inventory.stock.reservation.dto.IssueResponse;
import com.bbd.inventory.stock.reservation.dto.ReleaseResponse;
import com.bbd.inventory.stock.reservation.dto.ReserveRequest;
import com.bbd.inventory.stock.reservation.dto.ReserveResponse;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final StockRepository stockRepository;
    private final StockMovementRepository stockMovementRepository;
    private final StockReservationRepository stockReservationRepository;

    // STUDY: ISO-8601 duration, e.g. PT24H. Field injection
    // so @RequiredArgConstructor ignores it
    @Value("${inventory.reservation.ttl:PT24H}")
    private Duration ttl;

    /*
        Atomic single-warehouse reserve; returns the amount actually taken.
        멱등 키 일원화(공통 표준): Idempotency-Key 헤더를 dedup 키로 우선 사용.
        레거시 호출(헤더 미전송)은 바디 requestId 폴백. DB UNIQUE(uq_reservation_request)가 정확성 보루.
     */
    @Transactional
    public ReserveResponse reserve(ReserveRequest req, String idempotencyKey) {
        // 멱등 키 일원화(공통 표준 §2): Idempotency-Key 헤더만 dedup 키로 사용(바디 requestId 폴백 제거).
        String dedupKey = idempotencyKey;
        if (dedupKey == null || dedupKey.isBlank()) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }

        // 중복 재요청(공통 표준 §6): 409 반환 — 원본 응답을 재생/반환하지 않는다.
        if (stockReservationRepository.findByRequestId(dedupKey).isPresent()) {
            throw new ApiException(ErrorCode.IDEMPOTENT_DUPLICATE);
        }

        Stock stock = stockRepository.findForUpdate(req.sku(), req.warehouseCode())
                .orElseThrow(() -> new ApiException(ErrorCode.STOCK_NOT_FOUND));

        int granted = stock.reserve(req.quantity());

        Long reservationId = null;
        if (granted > 0) {
            StockReservation saved =
                    stockReservationRepository.save(StockReservation.builder()
                            .requestId(dedupKey)
                            .soNumber(req.soNumber())
                            .sku(req.sku())
                            .warehouseCode(req.warehouseCode())
                            .quantity(granted)
                            .expiresAt(Instant.now().plus(ttl))
                            .build());

            reservationId = saved.getId();
        }
        stockRepository.flush();

        return new ReserveResponse(
                reservationId, req.soNumber(), req.sku(), req.warehouseCode(),
                req.quantity(), granted, req.quantity() - granted, stock.getAvailableStock()
        );
    }

    @Transactional
    public ReleaseResponse releaseOne(Long reservationId) {
        StockReservation r =
                stockReservationRepository.findById(reservationId)
                        .orElseThrow(() -> new ApiException(ErrorCode.RESERVATION_NOT_FOUND));
        releaseRow(r);

        return new ReleaseResponse(r.getSoNumber(), List.of(line(r)));
    }

    @Transactional
    public ReleaseResponse releaseBySoNumber(String soNumber) {
        List<StockReservation> rows =
                stockReservationRepository.findBySoNumberAndStatus(soNumber, ReservationStatus.RESERVED);
        rows.forEach(this::releaseRow);

        return new ReleaseResponse(soNumber, rows.stream().map(this::line).toList());
    }

    /*
        Idempotent: only a RESERVED row gives stock back
     */
    private void releaseRow(StockReservation r) {
        if (r.getStatus() != ReservationStatus.RESERVED) return;
        Stock stock = stockRepository.findForUpdate(r.getSku(), r.getWarehouseCode())
                .orElseThrow(() -> new ApiException(ErrorCode.STOCK_NOT_FOUND));
        stock.release(r.getQuantity());
        r.markReleased();
    }

    private ReleaseResponse.Line line(StockReservation r) {
        return new ReleaseResponse.Line(
                r.getWarehouseCode(), r.getSku(), r.getQuantity(), r.getStatus().name()
        );
    }

    /*
        Issue every RESERVED row of the SO: physical OUT + movement.
        Idempotent (ISSUED rows excluded)
     */
    @Transactional
    public IssueResponse issue(String soNumber) {
        List<StockReservation> rows =
                stockReservationRepository.findBySoNumberAndStatus(soNumber, ReservationStatus.RESERVED);

        List<IssueResponse.Line> lines = new ArrayList<>();
        for (StockReservation r : rows) {
            Stock stock = stockRepository.findForUpdate(r.getSku(), r.getWarehouseCode())
                    .orElseThrow(() -> new ApiException(ErrorCode.STOCK_NOT_FOUND));

            stock.issue(r.getQuantity());
            stockMovementRepository.save(StockMovement.builder()
                    .sku(r.getSku())
                    .warehouseCode(r.getWarehouseCode())
                    .type(MovementType.OUT)
                    .quantity(r.getQuantity())
                    .unitPrice(stock.getUnitPrice())
                    .referenceNumber(soNumber)
                    .build());
            r.markIssued();
            lines.add(new IssueResponse.Line(r.getWarehouseCode(), r.getSku(), r.getQuantity()));
        }

        return new IssueResponse(soNumber, lines);
    }

    /*
        Reconcile: auto-release RESERVED rows past expiry. Called by ReservationReconciler
     */
    @Transactional
    public int releaseExpired() {
        List<StockReservation> expired = stockReservationRepository
                .findByStatusAndExpiresAtBefore(ReservationStatus.RESERVED, Instant.now());

        expired.forEach(this::releaseRow);
        return expired.size();
    }
}

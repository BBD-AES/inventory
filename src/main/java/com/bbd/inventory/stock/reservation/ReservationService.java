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
        Idempotent on requestId.
     */
    @Transactional
    public ReserveResponse reserve(ReserveRequest req) {
        var existing =
                stockReservationRepository.findByRequestId(req.requestId());
        if (existing.isPresent()) {
            return replay(existing.get(), req.quantity());
        }

        Stock stock = stockRepository.findForUpdate(req.sku(), req.warehouseCode())
                .orElseThrow(() -> new ApiException(ErrorCode.STOCK_NOT_FOUND));

        int granted = stock.reserve(req.quantity());

        Long reservationId = null;
        if (granted > 0) {
            StockReservation saved =
                    stockReservationRepository.save(StockReservation.builder()
                            .requestId(req.requestId())
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

    private ReserveResponse replay(StockReservation r, int requested) {
        int available =
                stockRepository.findBySkuAndWarehouseCode(r.getSku(), r.getWarehouseCode())
                        .map(Stock::getAvailableStock).orElse(0);

        return new ReserveResponse(
                r.getId(), r.getSoNumber(), r.getSku(), r.getWarehouseCode(),
                requested, r.getQuantity(), requested - r.getQuantity(), available);
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

package com.bbd.inventory.stock.reservation;

import com.bbd.inventory.global.error.ApiException;
import com.bbd.inventory.global.error.dto.ErrorCode;
import com.bbd.inventory.stock.*;
import com.bbd.inventory.stock.reservation.dto.IssueResponse;
import com.bbd.inventory.stock.reservation.dto.ReleaseResponse;
import com.bbd.inventory.stock.reservation.dto.ReserveRequest;
import com.bbd.inventory.stock.reservation.dto.ReserveResponse;
import com.bbd.inventory.warehouse.Warehouse;
import com.bbd.inventory.warehouse.WarehouseRepository;
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
    private final WarehouseRepository warehouseRepository; // 도착 지점 입고(IN) 시 목적지 Stock 생성에 필요(Stock.builder 가 Warehouse 엔티티 요구)

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
        Issue every RESERVED row of the SO: source OUT + movement, then (when a destination is given)
        load the same quantity INTO the destination warehouse: dest IN + movement.
        toWarehouseCode = the SO's destination branch (sales.order.received). The destination Stock row
        is found-or-created from the source Stock's catalog snapshot (name/unit/category/unitPrice; safetyStock 0).
        Idempotent (ISSUED rows excluded): a redelivery/re-call finds no RESERVED rows, so neither OUT nor IN repeats.
        toWarehouseCode == null/blank (legacy 4-field event / sync caller w/o dest) -> OUT only (backward-compatible).
     */
    @Transactional
    public IssueResponse issue(String soNumber, String toWarehouseCode) {
        List<StockReservation> rows =
                stockReservationRepository.findBySoNumberAndStatus(soNumber, ReservationStatus.RESERVED);

        boolean inboundToDestination = toWarehouseCode != null && !toWarehouseCode.isBlank();
        // 목적지 창고는 사전 등록돼 있어야 함(Stock 의 warehouse FK). 미등록이면 WAREHOUSE_NOT_FOUND → tx 롤백 → claim un-claim → 재시도.
        // rows 가 비면(이미 ISSUED = 재전달/중복) IN 할 일이 없으므로 창고 조회도 건너뛴다(no-op 재처리가 창고 조회로 실패하지 않게).
        Warehouse destWarehouse = (inboundToDestination && !rows.isEmpty())
                ? warehouseRepository.findByCode(toWarehouseCode)
                        .orElseThrow(() -> new ApiException(ErrorCode.WAREHOUSE_NOT_FOUND))
                : null;

        List<IssueResponse.Line> lines = new ArrayList<>();
        for (StockReservation r : rows) {
            // 1) 출처 출고(OUT): 예약된 창고에서 현재고 차감(가용은 예약 때 이미 차감됨)
            Stock source = stockRepository.findForUpdate(r.getSku(), r.getWarehouseCode())
                    .orElseThrow(() -> new ApiException(ErrorCode.STOCK_NOT_FOUND));
            source.issue(r.getQuantity());
            stockMovementRepository.save(StockMovement.builder()
                    .sku(r.getSku())
                    .warehouseCode(r.getWarehouseCode())
                    .type(MovementType.OUT)
                    .quantity(r.getQuantity())
                    .unitPrice(source.getUnitPrice())
                    .referenceNumber(soNumber)
                    .build());
            r.markIssued();

            // 2) 도착 지점 입고(IN): 목적지 창고에 적재(없으면 출처 Stock 의 카탈로그 스냅샷으로 생성).
            //    ★반드시 이 RESERVED 루프 안(markIssued 게이트 안쪽)에 둔다 — 재전달/재호출 시 RESERVED 0건 → IN 도 1회만.
            if (inboundToDestination) {
                Stock dest = stockRepository.findForUpdate(r.getSku(), toWarehouseCode)
                        .orElseGet(() -> stockRepository.save(Stock.builder()
                                .warehouse(destWarehouse)
                                .sku(source.getSku())
                                .name(source.getName())
                                .unit(source.getUnit())
                                .currentStock(0)
                                .availableStock(0)
                                .safetyStock(0)            // 지점 안전재고 기본 0 — 운영자가 별도 설정
                                .unitPrice(source.getUnitPrice())
                                .category(source.getCategory())
                                .location(null)
                                .build()));
                dest.applyIn(r.getQuantity());
                stockMovementRepository.save(StockMovement.builder()
                        .sku(r.getSku())
                        .warehouseCode(toWarehouseCode)
                        .type(MovementType.IN)
                        .quantity(r.getQuantity())
                        .unitPrice(dest.getUnitPrice())
                        .referenceNumber(soNumber)
                        .build());
            }

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

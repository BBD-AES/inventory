package com.bbd.inventory.stock.reservation;

import com.bbd.inventory.global.error.ApiException;
import com.bbd.inventory.global.error.dto.ErrorCode;
import com.bbd.inventory.stock.MovementType;
import com.bbd.inventory.stock.Stock;
import com.bbd.inventory.stock.StockMovement;
import com.bbd.inventory.stock.StockMovementRepository;
import com.bbd.inventory.stock.StockRepository;
import com.bbd.inventory.stock.reservation.dto.IssueResponse;
import com.bbd.inventory.warehouse.Warehouse;
import com.bbd.inventory.warehouse.WarehouseRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit test for {@link ReservationService#issue(String, String)} — the SO 수령(receive) 출고 경로.
 * 핵심 회귀 방지: 수령 시 출처 출고(OUT) 뿐 아니라 도착 지점(toWarehouseCode)에 입고(IN)가 일어나
 * 지점 재고가 실제로 증가하는지. 멱등/ backward-compat/ 목적지 창고 미등록도 함께 고정한다.
 */
@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock private StockRepository stockRepository;
    @Mock private StockMovementRepository stockMovementRepository;
    @Mock private StockReservationRepository stockReservationRepository;
    @Mock private WarehouseRepository warehouseRepository;

    @InjectMocks private ReservationService service;

    private static final String SO = "SO-1";
    private static final String SKU = "OIL-FLT-001";
    private static final String SRC = "WH-HQ-001";   // 출처(예약된 창고)
    private static final String DEST = "WH-BR-001";  // 도착 지점(목적지)

    private Stock sourceStock() {
        // 현재고 100, 가용 90(=10 예약됨), 단가 5000
        return Stock.builder()
                .warehouse(mock(Warehouse.class))
                .sku(SKU).name("오일필터").unit("EA")
                .currentStock(100).availableStock(90).safetyStock(20)
                .unitPrice(5000).category("필터").location("A-1")
                .build();
    }

    private StockReservation reserved(int qty) {
        return StockReservation.builder()
                .requestId("req-1").soNumber(SO).sku(SKU)
                .warehouseCode(SRC).quantity(qty)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    @Test
    @DisplayName("issue(dest): 출처 OUT + 목적지 신규 Stock 생성 후 IN, 예약 ISSUED")
    void issue_createsDestinationAndLoadsIn() {
        Stock source = sourceStock();
        StockReservation r = reserved(10);
        Warehouse destWh = mock(Warehouse.class);

        when(stockReservationRepository.findBySoNumberAndStatus(SO, ReservationStatus.RESERVED))
                .thenReturn(List.of(r));
        when(stockRepository.findForUpdate(SKU, SRC)).thenReturn(Optional.of(source));
        when(warehouseRepository.findByCode(DEST)).thenReturn(Optional.of(destWh));
        when(stockRepository.findForUpdate(SKU, DEST)).thenReturn(Optional.empty());
        when(stockRepository.save(any(Stock.class))).thenAnswer(inv -> inv.getArgument(0));

        IssueResponse res = service.issue(SO, DEST);

        // 출처: 현재고만 차감(가용은 예약 때 이미 차감 → 불변)
        assertThat(source.getCurrentStock()).isEqualTo(90);
        assertThat(source.getAvailableStock()).isEqualTo(90);
        assertThat(r.getStatus()).isEqualTo(ReservationStatus.ISSUED);

        // 목적지: 출처 카탈로그 스냅샷으로 생성 + applyIn(10)
        ArgumentCaptor<Stock> destCap = ArgumentCaptor.forClass(Stock.class);
        verify(stockRepository).save(destCap.capture());
        Stock dest = destCap.getValue();
        assertThat(dest.getWarehouse()).isSameAs(destWh);
        assertThat(dest.getSku()).isEqualTo(SKU);
        assertThat(dest.getName()).isEqualTo("오일필터");
        assertThat(dest.getUnit()).isEqualTo("EA");
        assertThat(dest.getCategory()).isEqualTo("필터");
        assertThat(dest.getUnitPrice()).isEqualTo(5000);
        assertThat(dest.getSafetyStock()).isZero();          // 지점 안전재고 기본 0
        assertThat(dest.getCurrentStock()).isEqualTo(10);    // 0 + applyIn(10)
        assertThat(dest.getAvailableStock()).isEqualTo(10);

        // 이동 2건: OUT(출처) → IN(목적지), 둘 다 ref=soNumber
        ArgumentCaptor<StockMovement> mvCap = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository, times(2)).save(mvCap.capture());
        StockMovement out = mvCap.getAllValues().get(0);
        StockMovement in = mvCap.getAllValues().get(1);
        assertThat(out.getType()).isEqualTo(MovementType.OUT);
        assertThat(out.getWarehouseCode()).isEqualTo(SRC);
        assertThat(out.getQuantity()).isEqualTo(10);
        assertThat(out.getReferenceNumber()).isEqualTo(SO);
        assertThat(in.getType()).isEqualTo(MovementType.IN);
        assertThat(in.getWarehouseCode()).isEqualTo(DEST);
        assertThat(in.getQuantity()).isEqualTo(10);
        assertThat(in.getReferenceNumber()).isEqualTo(SO);

        assertThat(res.lines()).hasSize(1);
    }

    @Test
    @DisplayName("issue(dest): 목적지 Stock 이 이미 있으면 생성 없이 applyIn")
    void issue_existingDestination_appliesIn_noCreate() {
        Stock source = sourceStock();
        Stock existingDest = Stock.builder()
                .warehouse(mock(Warehouse.class))
                .sku(SKU).name("오일필터").unit("EA")
                .currentStock(5).availableStock(5).safetyStock(0)
                .unitPrice(5000).category("필터").location(null)
                .build();
        StockReservation r = reserved(10);
        Warehouse destWh = mock(Warehouse.class);

        when(stockReservationRepository.findBySoNumberAndStatus(SO, ReservationStatus.RESERVED))
                .thenReturn(List.of(r));
        when(stockRepository.findForUpdate(SKU, SRC)).thenReturn(Optional.of(source));
        when(warehouseRepository.findByCode(DEST)).thenReturn(Optional.of(destWh));
        when(stockRepository.findForUpdate(SKU, DEST)).thenReturn(Optional.of(existingDest));

        service.issue(SO, DEST);

        assertThat(existingDest.getCurrentStock()).isEqualTo(15);   // 5 + 10
        assertThat(existingDest.getAvailableStock()).isEqualTo(15);
        verify(stockRepository, never()).save(any(Stock.class));    // 생성 안 함
        verify(stockMovementRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("issue: RESERVED 행이 없으면(재전달/중복) no-op — OUT/IN/창고조회 없음(멱등)")
    void issue_noReservedRows_noOp() {
        when(stockReservationRepository.findBySoNumberAndStatus(SO, ReservationStatus.RESERVED))
                .thenReturn(List.of());

        IssueResponse res = service.issue(SO, DEST);

        assertThat(res.lines()).isEmpty();
        verify(warehouseRepository, never()).findByCode(any());
        verify(stockRepository, never()).findForUpdate(any(), any());
        verify(stockMovementRepository, never()).save(any());
        verify(stockRepository, never()).save(any(Stock.class));
    }

    @Test
    @DisplayName("issue(null dest): 출처 OUT 만 — 목적지 조회/IN 없음(backward-compat)")
    void issue_nullDestination_outOnly() {
        Stock source = sourceStock();
        StockReservation r = reserved(10);

        when(stockReservationRepository.findBySoNumberAndStatus(SO, ReservationStatus.RESERVED))
                .thenReturn(List.of(r));
        when(stockRepository.findForUpdate(SKU, SRC)).thenReturn(Optional.of(source));

        service.issue(SO, null);

        assertThat(source.getCurrentStock()).isEqualTo(90);
        assertThat(r.getStatus()).isEqualTo(ReservationStatus.ISSUED);
        verify(warehouseRepository, never()).findByCode(any());
        verify(stockRepository, never()).save(any(Stock.class));

        ArgumentCaptor<StockMovement> mvCap = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository, times(1)).save(mvCap.capture());
        assertThat(mvCap.getValue().getType()).isEqualTo(MovementType.OUT);
    }

    @Test
    @DisplayName("issue(dest): 목적지 창고 미등록 → WAREHOUSE_NOT_FOUND, 출처 OUT 도 안 일어남(원자성)")
    void issue_destinationWarehouseMissing_throwsBeforeOut() {
        StockReservation r = reserved(10);

        when(stockReservationRepository.findBySoNumberAndStatus(SO, ReservationStatus.RESERVED))
                .thenReturn(List.of(r));
        when(warehouseRepository.findByCode(DEST)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.issue(SO, DEST))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.WAREHOUSE_NOT_FOUND);

        verify(stockMovementRepository, never()).save(any());
        verify(stockRepository, never()).save(any(Stock.class));
        assertThat(r.getStatus()).isEqualTo(ReservationStatus.RESERVED);  // 변경 없음
    }
}

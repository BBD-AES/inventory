package com.bbd.inventory.stock.query;

import com.bbd.inventory.stock.query.dto.SafetyShortageView;
import com.bbd.inventory.stock.query.dto.StockStatsResponse;
import com.bbd.inventory.stock.query.dto.WarehouseAvailabilityView;
import com.bbd.inventory.warehouse.WarehouseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Pure unit test (no Spring/DB) for the #41 dashboard aggregation mapping in
 * {@link StockQueryService#stats()}. Projection rows are stubbed; we assert the
 * service builds the response shape and copies fields faithfully.
 */
@ExtendWith(MockitoExtension.class)
class StockQueryServiceStatsTest {

    @Mock
    private StockQueryRepository stockQueryRepository;

    // unused by stats() but required by the constructor
    @Mock
    private WarehouseRepository warehouseRepository;

    @InjectMocks
    private StockQueryService service;

    @Test
    void stats_mapsShortagesAndWarehouseAggregates() {
        when(stockQueryRepository.safetyShortages()).thenReturn(List.of(
                shortage("SKU-1", "WH-A", 8, 3, 10, 7),
                shortage("SKU-2", "WH-B", 0, 0, 5, 5)
        ));
        when(stockQueryRepository.warehouseAvailability()).thenReturn(List.of(
                warehouse("WH-A", 120L, 1L),
                warehouse("WH-B", 0L, 1L)
        ));

        StockStatsResponse res = service.stats();

        assertThat(res.safetyShortage().count()).isEqualTo(2);
        assertThat(res.safetyShortage().items()).hasSize(2);

        StockStatsResponse.SafetyShortage.Item first = res.safetyShortage().items().getFirst();
        assertThat(first.sku()).isEqualTo("SKU-1");
        assertThat(first.warehouseCode()).isEqualTo("WH-A");
        assertThat(first.currentStock()).isEqualTo(8);
        assertThat(first.availableStock()).isEqualTo(3);
        assertThat(first.safetyStock()).isEqualTo(10);
        assertThat(first.shortage()).isEqualTo(7);

        assertThat(res.byWarehouse()).hasSize(2);
        StockStatsResponse.ByWarehouse whA = res.byWarehouse().getFirst();
        assertThat(whA.warehouseCode()).isEqualTo("WH-A");
        assertThat(whA.availableSum()).isEqualTo(120L);
        assertThat(whA.shortageCount()).isEqualTo(1L);
    }

    @Test
    void stats_emptyWhenNoShortages() {
        when(stockQueryRepository.safetyShortages()).thenReturn(List.of());
        when(stockQueryRepository.warehouseAvailability()).thenReturn(List.of());

        StockStatsResponse res = service.stats();

        assertThat(res.safetyShortage().count()).isZero();
        assertThat(res.safetyShortage().items()).isEmpty();
        assertThat(res.byWarehouse()).isEmpty();
    }

    private static SafetyShortageView shortage(String sku, String wh,
                                               int current, int available,
                                               int safety, int shortage) {
        return new SafetyShortageView() {
            public String getSku() { return sku; }
            public String getWarehouseCode() { return wh; }
            public int getCurrentStock() { return current; }
            public int getAvailableStock() { return available; }
            public int getSafetyStock() { return safety; }
            public int getShortage() { return shortage; }
        };
    }

    private static WarehouseAvailabilityView warehouse(String code, long availableSum, long shortageCount) {
        return new WarehouseAvailabilityView() {
            public String getWarehouseCode() { return code; }
            public long getAvailableSum() { return availableSum; }
            public long getShortageCount() { return shortageCount; }
        };
    }
}

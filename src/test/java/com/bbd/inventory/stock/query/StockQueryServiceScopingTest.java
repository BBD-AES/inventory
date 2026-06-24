package com.bbd.inventory.stock.query;

import com.bbd.inventory.common.BranchScope;
import com.bbd.inventory.global.error.ApiException;
import com.bbd.inventory.stock.query.dto.StockSearchCondition;
import com.bbd.inventory.warehouse.WarehouseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * QA #1 (재고현황 탭에서 다른 창고 보이면 안됨): a BRANCH caller is locked to its own
 * warehouse and cannot widen the filter; an HQ caller keeps the client filter.
 * Pure Mockito unit test — no Spring/DB.
 */
@ExtendWith(MockitoExtension.class)
class StockQueryServiceScopingTest {

    @Mock StockQueryRepository stockQueryRepository;
    @Mock WarehouseRepository warehouseRepository;
    @Mock BranchScope branchScope;
    @InjectMocks StockQueryService service;

    @Test
    void branchCaller_forcedToOwnWarehouse_ignoringClientFilter() {
        when(branchScope.warehouseCodeFilter()).thenReturn("WH001");
        when(stockQueryRepository.search(any(), any(), any(), anyBoolean(), any()))
                .thenReturn(Page.empty());

        // client tries to peek at someone else's warehouse
        service.search(new StockSearchCondition("WH002", null, null, null), Pageable.unpaged());

        ArgumentCaptor<String> wh = ArgumentCaptor.forClass(String.class);
        verify(stockQueryRepository).search(wh.capture(), any(), any(), anyBoolean(), any());
        assertThat(wh.getValue()).isEqualTo("WH001");                 // overridden to own
        verify(warehouseRepository, never()).existsByCode(any());     // branch path skips filter validation
    }

    @Test
    void hqCaller_keepsClientFilter() {
        when(branchScope.warehouseCodeFilter()).thenReturn(null);
        when(warehouseRepository.existsByCode("WH002")).thenReturn(true);
        when(stockQueryRepository.search(any(), any(), any(), anyBoolean(), any()))
                .thenReturn(Page.empty());

        service.search(new StockSearchCondition("WH002", null, null, null), Pageable.unpaged());

        verify(stockQueryRepository).search(eq("WH002"), any(), any(), anyBoolean(), any());
    }

    @Test
    void hqCaller_invalidWarehouseFilter_throws() {
        when(branchScope.warehouseCodeFilter()).thenReturn(null);
        when(warehouseRepository.existsByCode("BAD")).thenReturn(false);

        assertThatThrownBy(() ->
                service.search(new StockSearchCondition("BAD", null, null, null), Pageable.unpaged()))
                .isInstanceOf(ApiException.class);
    }
}

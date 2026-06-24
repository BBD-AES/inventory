package com.bbd.inventory.stock.query;

import com.bbd.inventory.common.BranchScope;
import com.bbd.inventory.stock.StockMovementRepository;
import com.bbd.inventory.stock.query.dto.MovementSearchCondition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * QA #2/#3 (입출고내역 내 지점만 / 지점 사용자 입출고 보임): a BRANCH caller is locked to
 * its own warehouse's movements; an HQ caller keeps the client filter.
 * Pure Mockito unit test — no Spring/DB.
 */
@ExtendWith(MockitoExtension.class)
class MovementQueryServiceScopingTest {

    @Mock StockMovementRepository repo;
    @Mock BranchScope branchScope;
    @InjectMocks MovementQueryService service;

    @Test
    void branchCaller_forcedToOwnWarehouse() {
        when(branchScope.warehouseCodeFilter()).thenReturn("WH001");
        when(repo.search(any(), any(), any(), any(), any(), any())).thenReturn(Page.empty());

        service.search(new MovementSearchCondition(null, null, "WH002", null, null), Pageable.unpaged());

        ArgumentCaptor<String> wh = ArgumentCaptor.forClass(String.class);
        verify(repo).search(any(), any(), wh.capture(), any(), any(), any());
        assertThat(wh.getValue()).isEqualTo("WH001");
    }

    @Test
    void hqCaller_keepsClientFilter() {
        when(branchScope.warehouseCodeFilter()).thenReturn(null);
        when(repo.search(any(), any(), any(), any(), any(), any())).thenReturn(Page.empty());

        service.search(new MovementSearchCondition(null, null, "WH002", null, null), Pageable.unpaged());

        verify(repo).search(any(), any(), eq("WH002"), any(), any(), any());
    }
}

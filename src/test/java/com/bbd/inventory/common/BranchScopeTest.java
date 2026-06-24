package com.bbd.inventory.common;

import com.bbd.inventory.warehouse.Warehouse;
import com.bbd.inventory.warehouse.WarehouseRepository;
import com.bbd.inventory.warehouse.WarehouseType;
import com.bbd.securitycore.application.model.CurrentUserSnapshotResult;
import com.bbd.securitycore.application.port.in.GetCurrentUserSnapshotUseCase;
import com.bbd.securitycore.domain.TenancyType;
import com.bbd.securitycore.domain.UserRole;
import com.bbd.securitycore.domain.UserStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * BranchScope resolves the warehouseCode a caller is locked to.
 * HQ/ADMIN -> null (see all). BRANCH -> tenancyName resolved to a warehouse code,
 * matching by warehouse NAME first (live data: tenancyName "판교지점" == warehouse name),
 * falling back to the raw tenancyName when no name matches (covers tenancyName == code).
 */
@ExtendWith(MockitoExtension.class)
class BranchScopeTest {

    @Mock GetCurrentUserSnapshotUseCase currentUser;
    @Mock WarehouseRepository warehouseRepository;
    @InjectMocks BranchScope branchScope;

    @Test
    void hqCaller_returnsNull() {
        when(currentUser.getCurrentUserSnapshot()).thenReturn(snapshot(TenancyType.HQ, "본사"));
        assertThat(branchScope.warehouseCodeFilter()).isNull();
    }

    @Test
    void branchCaller_tenancyNameIsWarehouseName_resolvesToCode() {
        when(currentUser.getCurrentUserSnapshot()).thenReturn(snapshot(TenancyType.BRANCH, "판교지점"));
        when(warehouseRepository.findFirstByName("판교지점")).thenReturn(
                Optional.of(Warehouse.create("WH-DLR-004", "판교지점", WarehouseType.DEALER, "Seoul")));

        assertThat(branchScope.warehouseCodeFilter()).isEqualTo("WH-DLR-004");
    }

    @Test
    void branchCaller_tenancyNameIsAlreadyCode_fallsBackToRaw() {
        when(currentUser.getCurrentUserSnapshot()).thenReturn(snapshot(TenancyType.BRANCH, "WH-DLR-004"));
        when(warehouseRepository.findFirstByName("WH-DLR-004")).thenReturn(Optional.empty());

        assertThat(branchScope.warehouseCodeFilter()).isEqualTo("WH-DLR-004");
    }

    private static CurrentUserSnapshotResult snapshot(TenancyType tenancyType, String tenancyName) {
        return new CurrentUserSnapshotResult(
                1L, "sub", "emp", "user", "황인우", "e@x.com",
                UserStatus.ACTIVE, UserRole.BRANCH_STAFF, tenancyType, tenancyName, 0L);
    }
}

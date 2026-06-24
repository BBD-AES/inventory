package com.bbd.inventory.common;

import com.bbd.securitycore.application.port.in.GetCurrentUserSnapshotUseCase;
import com.bbd.securitycore.domain.TenancyType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resolves the warehouse a caller is restricted to.
 * BRANCH users (BRANCH_MANAGER/BRANCH_STAFF) are locked to their own warehouse,
 * identified by tenancyName == warehouse.code. HQ roles (HQ_MANAGER/HQ_STAFF/ADMIN)
 * are unrestricted and see every warehouse.
 */
@Component
@RequiredArgsConstructor
public class BranchScope {

    private final GetCurrentUserSnapshotUseCase currentUser;

    /**
     * @return the warehouseCode a BRANCH caller may see, or null if the caller
     *         is HQ/ADMIN and may see all warehouses.
     */
    public String warehouseCodeFilter() {
        var u = currentUser.getCurrentUserSnapshot();
        return u.tenancyType() == TenancyType.BRANCH ? u.tenancyName() : null;
    }
}

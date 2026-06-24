package com.bbd.inventory.common;

import com.bbd.inventory.warehouse.Warehouse;
import com.bbd.inventory.warehouse.WarehouseRepository;
import com.bbd.securitycore.application.port.in.GetCurrentUserSnapshotUseCase;
import com.bbd.securitycore.domain.TenancyType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resolves the warehouse a caller is restricted to.
 * BRANCH users (BRANCH_MANAGER/BRANCH_STAFF) are locked to their own warehouse;
 * HQ roles (HQ_MANAGER/HQ_STAFF/ADMIN) are unrestricted and see every warehouse.
 *
 * The user snapshot's tenancyName carries the branch's warehouse display name
 * (e.g. "판교지점"), so it is resolved to that warehouse's code (stock/movement rows
 * are keyed by warehouseCode). If no name matches, tenancyName is used as-is — this
 * also covers a deployment where tenancyName already equals the warehouse code.
 */
@Component
@RequiredArgsConstructor
public class BranchScope {

    private final GetCurrentUserSnapshotUseCase currentUser;
    private final WarehouseRepository warehouseRepository;

    /**
     * @return the warehouseCode a BRANCH caller may see, or null if the caller
     *         is HQ/ADMIN and may see all warehouses.
     */
    public String warehouseCodeFilter() {
        var u = currentUser.getCurrentUserSnapshot();
        if (u.tenancyType() != TenancyType.BRANCH) return null;   // HQ/ADMIN -> no filter

        String tenancyName = u.tenancyName();
        return warehouseRepository.findFirstByName(tenancyName)
                .map(Warehouse::getCode)
                .orElse(tenancyName);   // fall back to treating tenancyName as the code
    }
}

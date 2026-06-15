package com.bbd.inventory.warehouse.query;

import com.bbd.inventory.common.PageResponse;
import com.bbd.inventory.global.error.ApiException;
import com.bbd.inventory.global.error.dto.ErrorCode;
import com.bbd.inventory.warehouse.WarehouseType;
import com.bbd.inventory.warehouse.query.dto.WarehouseResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WarehouseQueryService {

    private final WarehouseQueryRepository warehouseQueryRepository;

    @Transactional(readOnly = true)
    public WarehouseResponse getByCode(String code) {
        return warehouseQueryRepository.findByCode(code)
                .map(WarehouseResponse::from)
                .orElseThrow(() -> new ApiException(ErrorCode.WAREHOUSE_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public PageResponse<WarehouseResponse> list(Boolean active, WarehouseType type, Pageable pageable) {

        return PageResponse.from(
                warehouseQueryRepository.search(active, type, pageable)
                        .map(WarehouseResponse::from));

    }
}

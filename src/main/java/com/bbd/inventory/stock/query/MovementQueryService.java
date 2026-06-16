package com.bbd.inventory.stock.query;

import com.bbd.inventory.common.PageResponse;
import com.bbd.inventory.global.error.ApiException;
import com.bbd.inventory.global.error.dto.ErrorCode;
import com.bbd.inventory.stock.StockMovementRepository;
import com.bbd.inventory.stock.query.dto.MovementResponse;
import com.bbd.inventory.stock.query.dto.MovementSearchCondition;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.print.Pageable;

@Service
@RequiredArgsConstructor
public class MovementQueryService {

    private final StockMovementRepository repo;

    @Transactional(readOnly = true)
    public PageResponse<MovementResponse> search(MovementSearchCondition c, Pageable pageable) {
        if (c.from() != null && c.to() != null && c.from().isAfter(c.to())) {
            throw new ApiException(ErrorCode.INVALID_DATE_RANGE);
        }
        return PageResponse.from(
                repo.search(c.movementType(), c.sku(), c.warehouseCode(),
                        c.from(), c.to(), pageable).map(MovementResponse::from));
    }

    @Transactional(readOnly = true)
    public MovementResponse getById(Long id) {
        return repo.findById(id)
                .map(MovementResponse::from)
                .orElseThrow(() -> new ApiException(ErrorCode.MOVEMENT_NOT_FOUND));
    }
}

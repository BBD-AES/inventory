package com.bbd.inventory.warehouse.command;

import com.bbd.inventory.global.error.ApiException;
import com.bbd.inventory.global.error.dto.ErrorCode;
import com.bbd.inventory.stock.StockRepository;
import com.bbd.inventory.warehouse.Warehouse;
import com.bbd.inventory.warehouse.WarehouseRepository;
import com.bbd.inventory.warehouse.command.dto.CreateWarehouseCommand;
import com.bbd.inventory.warehouse.command.dto.UpdateWarehouseCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WarehouseCommandService {

    private final WarehouseRepository warehouseRepository;
    private final StockRepository stockRepository;

    @Transactional
    public String create(CreateWarehouseCommand cmd) {
        if (warehouseRepository.existsByCode(cmd.code())) {
            throw new ApiException(ErrorCode.WAREHOUSE_CODE_DUPLICATE);
        }
        Warehouse saved = warehouseRepository.save(
                Warehouse.create(cmd.code(), cmd.name(), cmd.type(), cmd.address())
        );
        return saved.getCode();
    }

    @Transactional
    public void update(String code, UpdateWarehouseCommand cmd) {
        Warehouse w = warehouseRepository.findByCode(code)
                .orElseThrow(() -> new ApiException(ErrorCode.WAREHOUSE_NOT_FOUND));
        w.update(cmd.name(), cmd.type(), cmd.address());
    }

    @Transactional
    public void activate(String code) {
        Warehouse w = warehouseRepository.findByCode(code)
                .orElseThrow(() -> new ApiException(ErrorCode.WAREHOUSE_NOT_FOUND));
        w.activate();
    }

    @Transactional
    public void deactivate(String code) {
        Warehouse w = warehouseRepository.findByCode(code)
                .orElseThrow(() -> new ApiException(ErrorCode.WAREHOUSE_NOT_FOUND));

        // 재고확인
        if (stockRepository.existsByWarehouseCodeAndCurrentStockGreaterThan(code, 0)) {
            throw new ApiException(ErrorCode.WAREHOUSE_HAS_STOCK);
        }

        w.deactivate();
    }
}

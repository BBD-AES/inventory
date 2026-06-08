package com.bbd.inventory.stock.command;

import com.bbd.inventory.global.error.ApiException;
import com.bbd.inventory.global.error.dto.ErrorCode;
import com.bbd.inventory.stock.ItemClient;
import com.bbd.inventory.stock.ItemInfo;
import com.bbd.inventory.stock.Stock;
import com.bbd.inventory.stock.StockRepository;
import com.bbd.inventory.stock.command.dto.CreateStockCommand;
import com.bbd.inventory.stock.command.dto.StockLocationResponse;
import com.bbd.inventory.stock.command.dto.UpdateLocationCommand;
import com.bbd.inventory.warehouse.Warehouse;
import com.bbd.inventory.warehouse.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StockCommandService {

    private final StockRepository stockRepository;
    private final WarehouseRepository warehouseRepository;      // cross-context read
    private final ItemClient itemClient;                        // cross-service read

    // TODO: implement stock command operations (inbound, outbound, adjust, reserve).

    @Transactional
    public Long createStock(CreateStockCommand cmd) {
        // resolve the warehouse (local, authoritative to inventory)
        Warehouse warehouse = warehouseRepository.findByCode(cmd.warehouseCode())
                .orElseThrow(() -> new ApiException(
                        ErrorCode.WAREHOUSE_NOT_FOUND, cmd.warehouseCode()
                ));

        // one stock row per (warehouse, sku) -- guard duplicates
        if (stockRepository.existsByWarehouseAndSku(warehouse, cmd.sku())) {
            // TODO: fix the error code to STOCK_ALREADY_EXISTS
            throw new ApiException(ErrorCode.WAREHOUSE_HAS_STOCK);
        }

        // SYNC cross-service validation + fetch the catalog facts to denormalize
        // TODO: fix error code to SKU_NOT_FOUND
        ItemInfo item = itemClient.getBySku(cmd.sku())
                .orElseThrow(() -> new ApiException(ErrorCode.WAREHOUSE_NOT_FOUND, cmd.sku()));
        return 0L;
    }

    @Transactional
    public StockLocationResponse changeLocation(
            String sku, String warehouseCode, UpdateLocationCommand cmd) {

        Stock stock = stockRepository.findBySkuAndWarehouseCode(sku, warehouseCode)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.WAREHOUSE_NOT_FOUND, sku, warehouseCode
                ));
    }


}

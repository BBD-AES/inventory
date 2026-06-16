package com.bbd.inventory.stock.command;

import com.bbd.inventory.stock.command.dto.AdjustStockCommand;
import com.bbd.inventory.stock.command.dto.StockAdjustResponse;
import com.bbd.inventory.stock.command.dto.StockLocationResponse;
import com.bbd.inventory.stock.command.dto.UpdateLocationCommand;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/stocks")
@RequiredArgsConstructor
public class StockCommandController {

    private final StockCommandService commandService;

    @PatchMapping("/{warehouseCode}/{sku}/location")
    public StockLocationResponse changeLocation(
            @PathVariable String sku,
            @PathVariable String warehouseCode,
            @Valid @RequestBody UpdateLocationCommand cmd) {
        return commandService.changeLocation(sku, warehouseCode, cmd);
    }

    // 내부적인 재고 수정 (실사 보정) — corrected from spec's /warehouses/{sku}/edit,
    // which couldn't identify a row (stock is keyed by warehouse + sku).
    @PatchMapping("/{warehouseCode}/{sku}")
    public StockAdjustResponse adjust(
            @PathVariable String warehouseCode,
            @PathVariable String sku,
            @Valid @RequestBody AdjustStockCommand cmd) {
        return commandService.adjust(sku, warehouseCode, cmd);
    }

}

package com.bbd.inventory.stock.command;

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

    // TODO: implement stock command endpoints (POST/PUT/PATCH).
    @PatchMapping("/{sku}/{warehouseCode}/location")
    public StockLocationResponse changeLocation(
            @PathVariable String sku,
            @PathVariable String warehouseCode,
            @Valid @RequestBody UpdateLocationCommand cmd) {
        return commandService.changeLocation(sku, warehouseCode, cmd);
    }
}

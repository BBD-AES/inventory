package com.bbd.inventory.stock.command;

import com.bbd.inventory.common.IdempotencyStore;
import com.bbd.inventory.stock.command.dto.AdjustStockCommand;
import com.bbd.inventory.stock.command.dto.StockAdjustResponse;
import com.bbd.inventory.stock.command.dto.StockLocationResponse;
import com.bbd.inventory.stock.command.dto.StockOutboundRequest;
import com.bbd.inventory.stock.command.dto.UpdateLocationCommand;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/stocks")
@RequiredArgsConstructor
public class StockCommandController {

    private final StockCommandService commandService;
    private final IdempotencyStore idempotencyStore;

    @PatchMapping("/{warehouseCode}/{sku}/location")
    public StockLocationResponse changeLocation(
            @PathVariable String sku,
            @PathVariable String warehouseCode,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody UpdateLocationCommand cmd) {

        return idempotencyStore.execute(key, () -> commandService.changeLocation(sku, warehouseCode, cmd));
    }

    // 내부적인 재고 수정 (실사 보정) — corrected from spec's /warehouses/{sku}/edit,
    // which couldn't identify a row (stock is keyed by warehouse + sku).
    @PatchMapping("/{warehouseCode}/{sku}")
    public StockAdjustResponse adjust(
            @PathVariable String warehouseCode,
            @PathVariable String sku,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody AdjustStockCommand cmd) {


        return idempotencyStore.execute(key, () -> commandService.adjust(sku, warehouseCode, cmd));
    }

    // 수주 종료 출고(#69): sales 가 CO 종료 시 지정 창고 재고 차감 + movement OUT.
    // 멱등은 referenceNumber(coNumber) 기준으로 서비스에서 처리(서비스 간 직접 호출이라 Idempotency-Key 헤더 없음).
    // 부족 시 INSUFFICIENT_STOCK(409) → sales 가 종료 차단(CO007).
    @PostMapping("/outbound")
    public void outbound(@Valid @RequestBody StockOutboundRequest request) {
        commandService.outbound(request);
    }

}

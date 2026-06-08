package com.bbd.inventory.stock.command;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stocks")
@RequiredArgsConstructor
public class StockCommandController {

    private final StockCommandService commandService;

    // TODO: implement stock command endpoints (POST/PUT/PATCH).
}

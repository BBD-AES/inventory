package com.bbd.inventory.config;
import com.bbd.inventory.stock.client.ItemResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
/*
    Item service 랑 API 통신.
 */
@HttpExchange("/api/v1/item")
public interface InventoryHttpService {

    @GetExchange("/{sku}")
    ItemResponse getItem(@PathVariable String sku);

}

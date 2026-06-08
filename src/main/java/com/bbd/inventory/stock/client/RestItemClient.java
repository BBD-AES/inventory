package com.bbd.inventory.stock.client;

import com.bbd.inventory.stock.ItemClient;
import com.bbd.inventory.stock.ItemInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Optional;


@Component
@RequiredArgsConstructor
public class RestItemClient implements ItemClient {

    private final RestClient itemRestClient;

    /*
        1. getBySku('SKU-1001') -> GET .../items/SKU-1001
        2. HTTP JSON
        3. ItemResponse -> ItemInfo translate
     */
    @Override
    public Optional<ItemInfo> getBySku(String sku) {
        try {
            ItemResponse r = itemRestClient.get()
                    .uri("/api/v1/items/{sku}", sku)
                    .retrieve()
                    .body(ItemResponse.class);

            return Optional.ofNullable(r).map(this::toInfo);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }

    // wire DTO -> domain DTO
    private ItemInfo toInfo(ItemResponse r) {
        return new ItemInfo(r.sku(), r.name(), r.category(), r.safetyStock());
    }
}

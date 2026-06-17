package com.bbd.inventory.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.service.registry.ImportHttpServices;

@Configuration
@ImportHttpServices(
        group = "bbd-user-service",
        types = InventoryHttpService.class
)
@ImportHttpServices(
        group = "bbd-item-service",
        types = InventoryHttpService.class
)
@ImportHttpServices(
        group = "bbd-inventory-service",
        types = InventoryHttpService.class
)
@ImportHttpServices(
        group = "bbd-procurement-service",
        types = InventoryHttpService.class
)
@ImportHttpServices(
        group = "bbd-sales-service",
        types = InventoryHttpService.class
)

public class HttpServiceConfig {
}
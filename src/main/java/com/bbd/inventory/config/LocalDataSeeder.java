package com.bbd.inventory.config;

import com.bbd.inventory.stock.Stock;
import com.bbd.inventory.stock.StockRepository;
import com.bbd.inventory.warehouse.Warehouse;
import com.bbd.inventory.warehouse.WarehouseRepository;
import com.bbd.inventory.warehouse.WarehouseType;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Component
@Profile("local")       // never runs against prod
@RequiredArgsConstructor
public class LocalDataSeeder implements ApplicationRunner {

    private final WarehouseRepository warehouseRepository;
    private final StockRepository stockRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (warehouseRepository.count() > 0) {
            return;
        }   // idempotent - already seeded

        /*
            saving one HQ + two BRANCH warehouses to warehouseRepository
         */
        Warehouse hq = Warehouse.create("WH-HQ-001",  "본사 중앙창고",
                WarehouseType.HQ,"서울시 강남구");

        Warehouse dlr1 = Warehouse.create("WH-DLR-001", "강북 대리점창고",
                WarehouseType.DEALER, "서울시 강북구");

        Warehouse dlr2 = Warehouse.create("WH-DLR-002", "부산 대리점창고",
                WarehouseType.DEALER, "부산시 해운대구");

        warehouseRepository.saveAll(List.of(hq, dlr1, dlr2));

        /*
            implementing 50sku across each warehouse, -> 150 rows of load test
         */

        List<Warehouse> warehouses = List.of(hq, dlr1, dlr2);
        List<Stock> stocks = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            // SKU-1001 ~ SKU-1050
            String sku = String.format("SKU-%04d", 1000 + i);

            for (Warehouse w : warehouses) {
                int qty = 100 + i;
                stocks.add(Stock.builder()
                        .warehouse(w)
                        .sku(sku)
                        .name("부품 " + sku)
                        .unit("EA")
                        .currentStock(qty)
                        .availableStock(qty)
                        .safetyStock(30)
                        .category("엔진/오일")
                        .location(String.format("A-%d-%02d", (i % 9) + 1, i))
                        .build());
            }
        }

        stockRepository.saveAll(stocks);
    }
}

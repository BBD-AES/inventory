package simulations;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/*
    More realistic simulation rather than a smoke test, utilizing feeder
 */
public class StockReadSimulation extends Simulation {

    HttpProtocolBuilder httpProtocol = http
            .baseUrl("http://localhost:8080/inventory")
            .acceptHeader("application/json");

    // feeder: every seeded (sku, warehouse) pair -> randomized per request

    static List<Map<String, Object>> rows() {
        String[] warehouses = {"WH-HQ-001", "WH-DLR-001", "WH-DLR-002"};
        List<Map<String, Object>> rows = new ArrayList<>();

        for (int i = 1; i <= 50; i++) {
            String sku = String.format("SKU-%04d", 1000 + i);

            for (String wh : warehouses) rows.add(Map.of("sku", sku, "wh", wh));
        }

        return rows;
    }
    FeederBuilder<Object> feeder = listFeeder(rows()).random();

    ScenarioBuilder scn = scenario("Stock reads")
            .feed(feeder)

            .exec(http("search by warehouse")
                    .get("/api/v1/stocks?warehouseCode=#{wh}").check(status().is(200)))
            .pause(Duration.ofMillis(200))
            .exec(http("stock detail")

                    .get("/api/v1/stocks/#{sku}/#{wh}").check(status().is(200)))
            .pause(Duration.ofMillis(200))
            .exec(http("list warehouses")
                    .get("/api/v1/warehouses").check(status().is(200)));

    {
        setUp(
                scn.injectOpen(
                        rampUsersPerSec(1).to(100).during(Duration.ofSeconds(30)),
                        constantUsersPerSec(50).during(Duration.ofSeconds(60))
                )
        ).protocols(httpProtocol)
                .assertions(
                        // p99 < 800ms
                        global().responseTime().percentile(99).lt(800),
                        global().failedRequests().percent().lt(1.0)     // <1%
                );
    }
}

package simulations;


import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

public class StockSmokeSimulation extends Simulation {

    HttpProtocolBuilder httpProtocol = http
            .baseUrl("http://localhost:8080/inventory")
            .acceptHeader("application/json");

    ScenarioBuilder scn = scenario("Stock reads (smoke)")
            .exec(http("list warehouses")
                    .get("/api/v1/warehouses").check(status().is(200)))
            .pause(1)

            .exec(http("search stocks")
                    .get("/api/v1/stocks?warehouseCode=WH-HQ-001").check(status().is(200)))
            .pause(1)

            .exec(http("stock detail")
                    .get("/api/v1/stocks/SKU-1001/WH-HQ-001").check(status().is(200)))
            .pause(1);
    {

        setUp(
                // 10 users over 10s
                scn.injectOpen(rampUsers(250).during(10))
        ).protocols(httpProtocol);
    }
}

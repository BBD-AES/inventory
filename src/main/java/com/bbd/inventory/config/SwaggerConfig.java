package com.bbd.inventory.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("inventory API")
                        .description("inventory Application API Documentation")
                        .version("v1.0"))
                .addServersItem(new Server()
                        .url("http://localhost:8083/inventory")
                        .description("Local Direct"))

                .addServersItem(new Server()
                        .url("http://192.168.201.110/inventory")
                        .description("Nginx"))

                .addServersItem(new Server()
                        .url("http://100.73.142.41:8083/inventory")
                        .description("bbd tailscale"))

                .addServersItem(new Server()
                        .url("http://112.218.95.58/inventory")
                        .description("External Nginx"));


    }
}

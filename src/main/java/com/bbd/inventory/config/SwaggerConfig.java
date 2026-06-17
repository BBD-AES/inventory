package com.bbd.inventory.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    private static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("inventory API")
                        .description("inventory Application API Documentation - Keycloak JWT Bearer Token 인증")
                        .version("v1.0"))

                .addServersItem(new Server()
                        .url("http://localhost:8080/inventory")
                        .description("로컬 스웨거")
                )

                .addServersItem(new Server()
                        .url("http://192.168.200.220/inventory")
                        .description("강의실 노트북")
                )

                .addServersItem(new Server()
                        .url("http://bbd.inwoohub.com/inventory")
                        .description("ECS Swagger")
                )

                .addServersItem(new Server()
                        .url("http://100.73.142.41/inventory")
                        .description("TailScale 강의실 노트북")
                )

//                .addServersItem(new Server()
//                        .url("http://localhost:8083/inventory")
//                        .description("Local Direct"))
//
//                .addServersItem(new Server()
//                        .url("http://localhost:8080")
//                        .description("Local Local"))
//
//                .addServersItem(new Server()
//                        .url("http://192.168.201.110/inventory")
//                        .description("Nginx"))
//
//                .addServersItem(new Server()
//                        .url("http://100.73.142.41:8083/inventory")
//                        .description("bbd tailscale"))
//
//                .addServersItem(new Server()
//                        .url("http://112.218.95.58/inventory")
//                        .description("External Nginx"))

                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Keycloak Access Token 입력")))

                .addSecurityItem(new SecurityRequirement()
                        .addList(BEARER_AUTH));
    }
}
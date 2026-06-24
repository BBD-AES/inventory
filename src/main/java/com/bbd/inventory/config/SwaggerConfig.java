package com.bbd.inventory.config;

import com.bbd.securitycore.idempotency.Idempotent;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;

import java.util.List;

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
                        .url("/inventory")
                        .description("현재 호스트"))

                // ===== 인증 스킴: JWT Bearer + 공통 멱등 헤더 정의 =====
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Keycloak Access Token 입력"))

                        // Swagger 문서용 공통 헤더 (operation 에서 $ref 로 조립)
                        .addParameters("IdempotencyKeyHeader",
                                new Parameter()
                                        .in("header")
                                        .name("Idempotency-Key")
                                        .required(false)
                                        .description("멱등 처리를 위한 요청 고유 키")
                                        .schema(new StringSchema().example("멱등키 입력"))))

                .security(List.of(
                        new SecurityRequirement().addList(BEARER_AUTH)));
    }
}

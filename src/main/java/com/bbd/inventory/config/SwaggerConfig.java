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

                // ===== 서버 목록 =====
                .addServersItem(new Server()
                        .url("http://localhost:8080/inventory")
                        .description("로컬 스웨거"))

                .addServersItem(new Server()
                        .url("http://192.168.200.220/inventory")
                        .description("강의실 노트북"))

                .addServersItem(new Server()
                        .url("https://bbd.inwoohub.com/inventory")
                        .description("ECS Swagger"))

                .addServersItem(new Server()
                        .url("http://100.73.142.41/inventory")
                        .description("TailScale 강의실 노트북"))

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

    /**
     * @Idempotent 가 붙은 모든 엔드포인트에 Idempotency-Key 헤더 파라미터를 자동으로 노출한다.
     * components.parameters("IdempotencyKeyHeader") 정의는 "부품 등록"일 뿐이라, 이렇게
     * 각 operation 에 $ref 로 조립해줘야 Swagger 에 입력란이 뜬다.
     * 이미 @RequestHeader("Idempotency-Key") 로 직접 선언한 경우는 중복 추가하지 않는다.
     */
    @Bean
    public OperationCustomizer idempotencyKeyHeaderCustomizer() {
        return (Operation operation, HandlerMethod handlerMethod) -> {
            if (!handlerMethod.hasMethodAnnotation(Idempotent.class)) {
                return operation;
            }
            boolean alreadyDeclared = operation.getParameters() != null
                    && operation.getParameters().stream().anyMatch(p ->
                    "Idempotency-Key".equals(p.getName()) && "header".equals(p.getIn()));
            if (!alreadyDeclared) {
                operation.addParametersItem(
                        new Parameter().$ref("#/components/parameters/IdempotencyKeyHeader"));
            }
            return operation;
        };
    }
}

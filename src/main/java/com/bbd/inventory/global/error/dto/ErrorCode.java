package com.bbd.inventory.global.error.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    // 공통
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했습니다."),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 자원을 찾을 수 없습니다."),

    TEST_ERROR(HttpStatus.NOT_FOUND,  "찾을 수 없습니다."),

    WAREHOUSE_NOT_FOUND(HttpStatus.NOT_FOUND,
            "Warehouse not found"),

    WAREHOUSE_CODE_DUPLICATE(HttpStatus.CONFLICT, "창고 부품이 존재합니다."),

    WAREHOUSE_HAS_STOCK(HttpStatus.CONFLICT,"창고에 물품이 있기 때문에 비활성화가 불가능합니다."),

    INVALID_FILTER(HttpStatus.BAD_REQUEST,"Unknown warehouseCode"),

    // 재고 (stock)
    STOCK_NOT_FOUND(HttpStatus.NOT_FOUND, "Stock not found"),
    STOCK_ALREADY_EXISTS(HttpStatus.CONFLICT, "Stock already exists"),
    SKU_NOT_FOUND(HttpStatus.NOT_FOUND, "Unknown sku in catalog"),
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "Insufficient stock"),

    // 외부 서비스 (catalog)
    ITEM_SERVICE_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "Item catalog service unavailable");


    private final HttpStatus status;
    private final String defaultMessage;
}
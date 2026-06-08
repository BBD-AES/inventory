package com.bbd.inventory.global.error.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    TEST_ERROR(HttpStatus.NOT_FOUND, "T001", "찾을 수 없습니다."),

    WAREHOUSE_NOT_FOUND(HttpStatus.NOT_FOUND, "WAREHOUSE_NOT_FOUND",
            "Warehouse not found: %s"),

    WAREHOUSE_CODE_DUPLICATE(HttpStatus.CONFLICT, "WAREHOUSE_CODE_DUPLICATE",
            "창고 부품이 존재합니다."),

    WAREHOUSE_HAS_STOCK(HttpStatus.CONFLICT, "WAREHOUSE_HAS_STOCK",
            "창고에 물품이 있기 때문에 비활성화가 불가능합니다."),

    INVALID_FILTER(HttpStatus.BAD_REQUEST, "INVALID_FILTER",
            "Unknown warehouseCode: %s");


    private final HttpStatus status;
    private final String code;
    private final String message;
}
package com.bbd.inventory.global.error.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    // 공통
    INTERNAL_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "일시적인 오류가 발생했습니다."
    ),
    VALIDATION_ERROR(
            HttpStatus.BAD_REQUEST,
            "입력값이 올바르지 않습니다."
    ),
    RESOURCE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "요청한 자원을 찾을 수 없습니다."
    ),

    TEST_ERROR(
            HttpStatus.NOT_FOUND,
            "찾을 수 없습니다."
    ),

    WAREHOUSE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "창고를 찾을수 없습니다."
    ),

    WAREHOUSE_CODE_DUPLICATE(
            HttpStatus.CONFLICT,
            "창고 부품이 존재합니다."
    ),

    WAREHOUSE_HAS_STOCK(
            HttpStatus.CONFLICT,
            "창고에 물품이 있기 때문에 비활성화가 불가능합니다."
    ),

    INVALID_FILTER(
            HttpStatus.BAD_REQUEST,
            "알수없는 창고 번호 입니다."
    ),

    MOVEMENT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "재고 이동 내역을 찾을 수 없습니다."
    ),

    INVALID_DATE_RANGE(
            HttpStatus.BAD_REQUEST,
            "'from' 날짜는 'to' 날짜보다 이전이거나 같아야 합니다."
    ),

    // 재고 (stock)
    STOCK_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "부품을 찾을 수 없습니다."
    ),

    STOCK_ALREADY_EXISTS(
            HttpStatus.CONFLICT,
            "재고가 이미 존재합니다."
    ),

    SKU_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "부품을 찾을 수 없습니다."
    ),

    INSUFFICIENT_STOCK(
            HttpStatus.CONFLICT,
            "재고가 부족합니다."
    ),

    RESERVATION_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "예약 내역을 찾을 수 없습니다."
    ),

    // 멱등성 (Idempotency)
    IDEMPOTENCY_KEY_REQUIRED(
            HttpStatus.BAD_REQUEST,
            "Idempotency-Key 헤더가 필요합니다."
    ),

    IDEMPOTENCY_KEY_INVALID(
            HttpStatus.BAD_REQUEST,
            "유효하지 않은 Idempotency-Key 형식입니다."
    ),

    IDEMPOTENCY_REQUEST_IN_PROGRESS(
            HttpStatus.CONFLICT,
            "동일한 Idempotency-Key를 가진 요청이 현재 처리 중입니다."
    ),

    IDEMPOTENT_DUPLICATE(
            HttpStatus.CONFLICT,
            "이미 처리된 요청입니다. (멱등 중복 차단)"
    ),

    IDEMPOTENCY_PAYLOAD_MISMATCH(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "동일한 Idempotency-Key에 대해 최초 요청과 다른 요청 본문이 전달되었습니다."
    ),

    // 외부 서비스 (catalog)
    ITEM_SERVICE_UNAVAILABLE(
            HttpStatus.BAD_GATEWAY,
            "부품 카탈로그 서비스를 사용할 수 없습니다."
    );

    private final HttpStatus status;
    private final String defaultMessage;
}
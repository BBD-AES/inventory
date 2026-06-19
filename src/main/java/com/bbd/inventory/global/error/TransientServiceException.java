package com.bbd.inventory.global.error;

import com.bbd.inventory.global.error.dto.ErrorCode;

/**
 * 일시적이고 재시도하면 회복 가능한 다운스트림 장애를 나타낸다
 * (예: 카탈로그 서비스 일시 다운).
 *
 * <p>비즈니스/데이터 오류({@link ApiException})와 타입으로 구분되므로,
 * 재시도 분류기({@code DefaultErrorHandler} / {@code @RetryableTopic})가
 * 이 예외만 재시도하고 나머지 데이터 오류는 곧바로 DLT로 보낼 수 있다.
 */
public class TransientServiceException extends ApiException {

    public TransientServiceException(ErrorCode errorCode) {
        super(errorCode);
    }
}

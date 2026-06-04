package com.bbd.inventory.global.error;

import com.bbd.inventory.global.error.dto.ErrorCode;
import lombok.Getter;

@Getter
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    /**
     * 디버깅 용이 아니라면, 이걸로 사용
     */
    public ApiException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

}
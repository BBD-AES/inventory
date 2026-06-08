package com.bbd.inventory.global.error;


import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 전역 예외처리. 모든 갈래를 ProblemDetail 로 통일한다.
 *  1) ApiException                : 권한/조회 실패/인증 등(ErrorCode 보유)
 *  2) SalesOrderStateException    : 도메인 상태 규칙 위반 -> 여기서 web 관심사로 번역
 *  3) IllegalArgumentException    : 도메인/입력값 검증 실패 -> 400 (없으면 500 으로 샘)
 *  (@Valid 실패=MethodArgumentNotValidException 은 상위 ResponseEntityExceptionHandler 가 이미 ProblemDetail 로 처리)
 */

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApiException(ApiException e) {
        return ResponseEntity.status(e.getStatusCode()).body(e.getBody());
    }
    // 이 아래로 각자의 서비스에 맞는 에러 핸들러를 추가하여 사용


}
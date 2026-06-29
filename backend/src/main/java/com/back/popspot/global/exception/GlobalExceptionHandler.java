package com.back.popspot.global.exception;

import com.back.popspot.global.response.CommonApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 비즈니스 예외 처리
    @ExceptionHandler(BusinessException.class)
    protected ResponseEntity<CommonApiResponse<Void>> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(CommonApiResponse.error(
                        errorCode.name(),
                        errorCode.getMessage()
                ));
    }

    // 요청 본문 검증 실패 처리
    @ExceptionHandler(MethodArgumentNotValidException.class)
    protected ResponseEntity<CommonApiResponse<Void>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException e
    ) {
        ErrorCode errorCode = ErrorCode.INVALID_INPUT_VALUE;

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(CommonApiResponse.error(
                        errorCode.name(),
                        errorCode.getMessage()
                ));
    }

    // 처리하지 못한 예외 처리
    @ExceptionHandler(Exception.class)
    protected ResponseEntity<CommonApiResponse<Void>> handleException(Exception e) {
        log.error("Unexpected Exception", e);

        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(CommonApiResponse.error(
                        errorCode.name(),
                        errorCode.getMessage()
                ));
    }
}

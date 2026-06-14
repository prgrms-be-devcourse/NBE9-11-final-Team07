package com.back.popspot.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "대상을 찾을 수 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    GOODS_OUT_OF_STOCK(HttpStatus.CONFLICT, "재고가 부족합니다."),
    GOODS_NOT_ON_SALE(HttpStatus.BAD_REQUEST, "현재 판매 중인 굿즈가 아닙니다."),
    GOODS_ORDER_REFUND_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "환불할 수 없는 주문 상태입니다.");

    private final HttpStatus status;
    private final String message;
}

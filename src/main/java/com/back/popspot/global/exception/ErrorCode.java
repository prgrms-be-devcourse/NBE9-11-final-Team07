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
    RESERVATION_SLOT_NOT_FOUND(HttpStatus.NOT_FOUND, "예약 슬롯을 찾을 수 없습니다."),
    RESERVATION_SLOT_ALREADY_STARTED(HttpStatus.BAD_REQUEST, "이미 시작한 슬롯은 예약할 수 없습니다."),
    POPUP_RESERVATION_NOT_AVAILABLE(HttpStatus.BAD_REQUEST, "현재 예약 가능한 팝업이 아닙니다."),
    RESERVATION_CAPACITY_EXCEEDED(HttpStatus.CONFLICT, "예약 가능한 인원이 가득 찼습니다."),
    RESERVATION_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 예약한 슬롯입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;
}

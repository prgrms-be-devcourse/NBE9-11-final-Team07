package com.back.popspot.global.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

	INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),

	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
	FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
	INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
	OAUTH2_LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "소셜 로그인에 실패했습니다."),

	RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "대상을 찾을 수 없습니다."),
	POPUP_STORE_NOT_FOUND(HttpStatus.NOT_FOUND, "팝업스토어를 찾을 수 없습니다."),
	GOODS_NOT_FOUND(HttpStatus.NOT_FOUND, "굿즈를 찾을 수 없습니다."),
	RESERVATION_SLOT_NOT_FOUND(HttpStatus.NOT_FOUND, "예약 슬롯을 찾을 수 없습니다."),

	POPUP_RESERVATION_NOT_AVAILABLE(HttpStatus.BAD_REQUEST, "현재 예약 가능한 팝업이 아닙니다."),
	RESERVATION_SLOT_ALREADY_STARTED(HttpStatus.BAD_REQUEST, "이미 시작한 슬롯은 예약할 수 없습니다."),
	RESERVATION_CANCEL_NOT_ALLOWED_STATUS(HttpStatus.BAD_REQUEST, "취소할 수 없는 예약 상태입니다."),
	RESERVATION_CANCEL_DEADLINE_PASSED(HttpStatus.BAD_REQUEST, "예약 취소 가능 기한이 지났습니다."),
	RESERVATION_PAYMENT_NOT_ALLOWED_STATUS(HttpStatus.BAD_REQUEST, "결제를 진행할 수 없는 예약 상태입니다."),
	RESERVATION_PAYMENT_EXPIRED(HttpStatus.BAD_REQUEST, "예약 선점 시간이 만료되었습니다."),

	COUPON_ALREADY_ISSUED(HttpStatus.CONFLICT, "이미 발급받은 쿠폰입니다."),
	RESERVATION_CAPACITY_EXCEEDED(HttpStatus.CONFLICT, "예약 가능한 인원이 가득 찼습니다."),
	RESERVATION_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 예약한 슬롯입니다."),
	RESERVATION_PAYMENT_ALREADY_COMPLETED(HttpStatus.CONFLICT, "이미 승인 완료된 예약 결제입니다."),

	GOODS_OUT_OF_STOCK(HttpStatus.CONFLICT, "재고가 부족합니다."),
	GOODS_NOT_ON_SALE(HttpStatus.BAD_REQUEST, "현재 판매 중인 굿즈가 아닙니다."),
	GOODS_ORDER_REFUND_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "환불할 수 없는 주문 상태입니다."),

	INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

	private final HttpStatus status;
	private final String message;
}

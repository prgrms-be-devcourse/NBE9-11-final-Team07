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
    GOODS_PRODUCT_IMAGE_REQUIRED(HttpStatus.BAD_REQUEST, "대표이미지는 필수입니다."),
    GOODS_DETAIL_IMAGE_REQUIRED(HttpStatus.BAD_REQUEST, "상세이미지는 필수입니다."),
    INVALID_IMAGE_TEMP_KEY(HttpStatus.BAD_REQUEST, "유효하지 않은 임시 이미지 키입니다."),
    GOODS_IMAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "굿즈 이미지를 찾을 수 없습니다."),

	RESERVATION_SLOT_NOT_FOUND(HttpStatus.NOT_FOUND, "예약 슬롯을 찾을 수 없습니다."),
	PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "결제 정보를 찾을 수 없습니다."),

	POPUP_RESERVATION_NOT_AVAILABLE(HttpStatus.BAD_REQUEST, "현재 예약 가능한 팝업이 아닙니다."),
	RESERVATION_ADMISSION_REQUIRED(HttpStatus.FORBIDDEN, "입장 허가 없이 예약할 수 없습니다."),
	RESERVATION_SLOT_ALREADY_STARTED(HttpStatus.BAD_REQUEST, "이미 시작한 슬롯은 예약할 수 없습니다."),
	RESERVATION_CANCEL_NOT_ALLOWED_STATUS(HttpStatus.BAD_REQUEST, "취소할 수 없는 예약 상태입니다."),
	RESERVATION_CANCEL_DEADLINE_PASSED(HttpStatus.BAD_REQUEST, "예약 취소 가능 기한이 지났습니다."),
	RESERVATION_PAYMENT_NOT_ALLOWED_STATUS(HttpStatus.BAD_REQUEST, "결제를 진행할 수 없는 예약 상태입니다."),
	RESERVATION_PAYMENT_EXPIRED(HttpStatus.BAD_REQUEST, "예약 선점 시간이 만료되었습니다."),

	COUPON_ALREADY_ISSUED(HttpStatus.CONFLICT, "이미 발급받은 쿠폰입니다."),
	RESERVATION_CAPACITY_EXCEEDED(HttpStatus.CONFLICT, "예약 가능한 인원이 가득 찼습니다."),
	RESERVATION_CAPACITY_OVERBOOKING_SUSPECTED(HttpStatus.CONFLICT, "DB 기준 활성 예약 수가 슬롯 정원을 초과했습니다."),
	RESERVATION_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 예약한 슬롯입니다."),
	RESERVATION_PAYMENT_ALREADY_COMPLETED(HttpStatus.CONFLICT, "이미 승인 완료된 예약 결제입니다."),

	GOODS_OUT_OF_STOCK(HttpStatus.CONFLICT, "재고가 부족합니다."),
	GOODS_NOT_ON_SALE(HttpStatus.BAD_REQUEST, "현재 판매 중인 굿즈가 아닙니다."),
	GOODS_ORDER_REFUND_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "환불할 수 없는 주문 상태입니다."),
	PAYMENT_AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, "결제 금액이 일치하지 않습니다."),
	PAYMENT_CONFIRM_NOT_ALLOWED_STATUS(HttpStatus.BAD_REQUEST, "승인할 수 없는 결제 상태입니다."),
	PAYMENT_KEY_MISMATCH(HttpStatus.BAD_REQUEST, "결제 키가 일치하지 않습니다."),
	PAYMENT_CONFIRM_RESPONSE_MISMATCH(HttpStatus.BAD_REQUEST, "결제 승인 응답이 일치하지 않습니다."),
	PAYMENT_CANCEL_NOT_ALLOWED_STATUS(HttpStatus.BAD_REQUEST, "취소할 수 없는 결제 상태입니다."),
	PAYMENT_CANCEL_ALREADY_REQUESTED(HttpStatus.CONFLICT, "다른 요청으로 결제 취소가 진행 중입니다."),
	PAYMENT_CANCEL_RESPONSE_MISMATCH(HttpStatus.BAD_GATEWAY, "결제 취소 응답이 일치하지 않습니다."),
	PAYMENT_CANCEL_FAILED(HttpStatus.BAD_GATEWAY, "결제 취소 요청에 실패했습니다."),
	PAYMENT_CONFIRM_ALREADY_REQUESTED(HttpStatus.CONFLICT, "이미 다른 결제 승인 요청이 진행 중입니다."),
	PAYMENT_CONFIRM_IN_PROGRESS(HttpStatus.CONFLICT, "결제 승인이 처리 중입니다."),
	PAYMENT_IDEMPOTENCY_KEY_MISMATCH(HttpStatus.CONFLICT, "결제 승인 멱등성 키가 일치하지 않습니다."),

	RESERVATION_TEMPORARILY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "예약이 일시 중단됩니다. 잠시 후 다시 시도해주세요."),
	INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

	private final HttpStatus status;
	private final String message;
}

package com.back.popspot.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

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
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;
}

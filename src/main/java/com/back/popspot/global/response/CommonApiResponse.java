package com.back.popspot.global.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class CommonApiResponse<T> {

    private String code;
    private String message;
    private T data;

    // 조회/일반 성공 응답
    // 사용 예시: return ResponseEntity.ok(CommonApiResponse.success(data));
    public static <T> CommonApiResponse<T> success(T data) {
        return new CommonApiResponse<>(
                "SUCCESS",
                "요청에 성공했습니다.",
                data
        );
    }

    // 데이터 없는 성공 응답
    // 사용 예시: return ResponseEntity.ok(CommonApiResponse.successMessage("삭제가 완료되었습니다."));
    public static CommonApiResponse<Void> successMessage(String message) {
        return new CommonApiResponse<>(
                "SUCCESS",
                message,
                null
        );
    }

    // 생성 성공 응답
    // 사용 예시: return ResponseEntity.status(HttpStatus.CREATED)
    //         .body(CommonApiResponse.created("생성이 완료되었습니다.", data));
    public static <T> CommonApiResponse<T> created(String message, T data) {
        return new CommonApiResponse<>(
                "SUCCESS",
                message,
                data
        );
    }

    public static CommonApiResponse<Void> error(String code, String message) {
        return new CommonApiResponse<>(
                code,
                message,
                null
        );
    }

    public static <T> CommonApiResponse<T> error(String code, String message, T data) {
        return new CommonApiResponse<>(
                code,
                message,
                data
        );
    }
}

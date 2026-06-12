package com.back.popspot.global.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class CommonApiResponse<T> {

    // HTTP 상태코드는 ResponseEntity에서 관리하고, 이 클래스는 응답 바디만 담당한다.
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

    // 실패 응답
    // 사용 예시: return ResponseEntity.status(errorCode.getStatus())
    //         .body(CommonApiResponse.error(errorCode.name(), errorCode.getMessage()));
    public static CommonApiResponse<Void> error(String code, String message) {
        return new CommonApiResponse<>(
                code,
                message,
                null
        );
    }

    // 상세 데이터 포함 실패 응답
    // 사용 예시: return ResponseEntity.badRequest()
    //         .body(CommonApiResponse.error("INVALID_INPUT_VALUE", "잘못된 요청입니다.", errors));
    public static <T> CommonApiResponse<T> error(String code, String message, T data) {
        return new CommonApiResponse<>(
                code,
                message,
                data
        );
    }
}

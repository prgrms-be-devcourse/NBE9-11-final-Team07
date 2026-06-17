package com.back.popspot.domain.popupStore.dto;

/**
 * 이미지 업로드용 presigned PUT URL 발급 응답.
 * tempKey 는 업로드 후 등록/수정 요청 시 imageKey 로 전달한다.
 */
public record PopupImagePresignResponse(
		String tempKey,
		String presignedUrl
) {
}

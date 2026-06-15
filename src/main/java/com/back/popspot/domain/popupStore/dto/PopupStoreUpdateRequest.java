package com.back.popspot.domain.popupStore.dto;

import java.time.LocalDateTime;

import com.back.popspot.domain.popupStore.entity.PopupFeeType;

/**
 * 팝업스토어 부분 수정 요청. 모든 필드가 null 허용이며, null 이 아닌 필드만 반영된다.
 */
public record PopupStoreUpdateRequest(
		String title,
		String location,
		PopupFeeType feeType,
		Integer price,
		LocalDateTime reservationStartAt,
		LocalDateTime reservationEndAt,
		LocalDateTime openDate,
		LocalDateTime closeDate,
		String imageKey,
		String description
) {
}

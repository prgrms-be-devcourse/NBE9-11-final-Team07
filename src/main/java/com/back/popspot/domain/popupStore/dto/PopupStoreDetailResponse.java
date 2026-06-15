package com.back.popspot.domain.popupStore.dto;

import java.time.LocalDateTime;

import com.back.popspot.domain.popupStore.entity.PopupFeeType;
import com.back.popspot.domain.popupStore.entity.PopupStatus;
import com.back.popspot.domain.popupStore.entity.PopupStore;

/**
 * 팝업스토어 상세 조회 응답. status 는 조회 시점 기준으로 계산된 값이다.
 */
public record PopupStoreDetailResponse(
		Long id,
		String title,
		String location,
		String imageKey,
		String description,
		PopupFeeType feeType,
		Integer price,
		LocalDateTime reservationStartAt,
		LocalDateTime reservationEndAt,
		LocalDateTime openDate,
		LocalDateTime closeDate,
		PopupStatus status
) {
	public static PopupStoreDetailResponse from(PopupStore popupStore, PopupStatus status) {
		return new PopupStoreDetailResponse(
				popupStore.getId(),
				popupStore.getTitle(),
				popupStore.getLocation(),
				popupStore.getImageKey(),
				popupStore.getDescription(),
				popupStore.getFeeType(),
				popupStore.getPrice(),
				popupStore.getReservationStartAt(),
				popupStore.getReservationEndAt(),
				popupStore.getOpenDate(),
				popupStore.getCloseDate(),
				status
		);
	}
}

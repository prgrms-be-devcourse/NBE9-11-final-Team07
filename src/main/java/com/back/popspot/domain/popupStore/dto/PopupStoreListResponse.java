package com.back.popspot.domain.popupStore.dto;

import java.time.LocalDateTime;

import com.back.popspot.domain.popupStore.entity.PopupStatus;
import com.back.popspot.domain.popupStore.entity.PopupStore;

/**
 * 팝업스토어 목록 조회 응답. status 는 조회 시점 기준으로 계산된 값이다.
 */
public record PopupStoreListResponse(
		Long id,
		String title,
		String location,
		String imageKey,
		LocalDateTime openDate,
		LocalDateTime closeDate,
		PopupStatus status
) {
	public static PopupStoreListResponse from(PopupStore popupStore, PopupStatus status) {
		return new PopupStoreListResponse(
				popupStore.getId(),
				popupStore.getTitle(),
				popupStore.getLocation(),
				popupStore.getImageKey(),
				popupStore.getOpenDate(),
				popupStore.getCloseDate(),
				status
		);
	}
}

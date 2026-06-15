package com.back.popspot.domain.popupStore.dto;

import java.time.LocalDateTime;

import com.back.popspot.domain.popupStore.entity.PopupFeeType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 팝업스토어 등록 요청. price 는 PAID 일 때만 필수이며, 해당 검증은 Service 에서 수행한다.
 */
public record
PopupStoreCreateRequest(
	@NotBlank String title,
	@NotBlank String location,
	@NotNull PopupFeeType feeType,
	Integer price,
	@NotNull LocalDateTime reservationStartAt,
	@NotNull LocalDateTime reservationEndAt,
	@NotNull LocalDateTime openDate,
	@NotNull LocalDateTime closeDate,
	String imageKey,
	String description
) {
}

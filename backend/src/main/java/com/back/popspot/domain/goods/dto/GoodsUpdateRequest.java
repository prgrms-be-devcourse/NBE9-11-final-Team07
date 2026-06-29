package com.back.popspot.domain.goods.dto;

import java.util.List;

import com.back.popspot.domain.goods.entity.GoodsImageType;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record GoodsUpdateRequest(
	String name,
	@Min(0) Integer price,
	@Min(0) Integer stock,
	String description,
	List<ImageKeyEntry> imageKeys
) {
	public record ImageKeyEntry(
		@NotBlank String imageKey,
		@NotNull GoodsImageType imageType
	) {
	}
}

package com.back.popspot.domain.goods.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class GoodsOrderCreateRequest {

	@NotEmpty
	@Valid
	private List<OrderItemRequest> items;

	private Long couponId;

	@NotBlank
	private String idempotencyKey;

	@NotBlank
	private String receiverName;

	@NotBlank
	private String receiverPhone;

	@NotBlank
	private String postalCode;

	@NotBlank
	private String address;

	private String addressDetail;

	@Getter
	@Setter
	@NoArgsConstructor
	public static class OrderItemRequest {

		@NotNull
		private Long goodsId;

		@Positive
		private int quantity;
	}
}

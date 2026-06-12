package com.back.popspot.domain.goods.entity;

import com.back.popspot.domain.coupon.entity.Coupon;
import com.back.popspot.domain.user.entity.User;
import com.back.popspot.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "goods_order")
public class GoodsOrder extends BaseEntity {
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	// nullable: 쿠폰 미사용 주문 가능
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "coupon_id")
	private Coupon coupon;

	@Column(name = "total_amount", nullable = false)
	private int totalAmount;

	@Column(name = "discount_amount")
	private Integer discountAmount;

	@Column(name = "final_amount", nullable = false)
	private int finalAmount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private GoodsOrderStatus status;

	@Column(name = "receiver_name", length = 50, nullable = false)
	private String receiverName;

	@Column(name = "receiver_phone", length = 20, nullable = false)
	private String receiverPhone;

	@Column(name = "postal_code", length = 10, nullable = false)
	private String postalCode;

	@Column(length = 255, nullable = false)
	private String address;

	@Column(name = "address_detail", length = 255)
	private String addressDetail;
}

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

import java.time.LocalDateTime;

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

	@Column(name = "expires_at")
	private LocalDateTime expiresAt;

	public GoodsOrder(User user, int totalAmount, Integer discountAmount, int finalAmount,
			GoodsOrderStatus status, String receiverName, String receiverPhone,
			String postalCode, String address, String addressDetail) {
		this.user = user;
		this.totalAmount = totalAmount;
		this.discountAmount = discountAmount;
		this.finalAmount = finalAmount;
		this.status = status;
		this.receiverName = receiverName;
		this.receiverPhone = receiverPhone;
		this.postalCode = postalCode;
		this.address = address;
		this.addressDetail = addressDetail;
	}

	public void updateStatus(GoodsOrderStatus newStatus) {
		this.status = newStatus;
	}

	// 주문 생성 시 만료 시각 세팅 (now + 30분)
	public void setExpiresAt(LocalDateTime expiresAt) {
		this.expiresAt = expiresAt;
	}

	// 스케줄러가 만료 처리 시 호출
	public void expire() {
		this.status = GoodsOrderStatus.EXPIRED;
	}

	// PENDING이고 만료 시각이 지났는지 확인 (경합 가드용)
	public boolean isExpired(LocalDateTime now) {
		return this.status == GoodsOrderStatus.PENDING
				&& this.expiresAt != null
				&& !this.expiresAt.isAfter(now);
	}
}

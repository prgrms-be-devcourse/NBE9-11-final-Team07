package com.back.popspot.domain.coupon.entity;

import java.time.LocalDateTime;

import com.back.popspot.domain.goods.entity.GoodsOrder;
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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@Table(
	name = "user_coupon",
	uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "coupon_id"})
)
public class UserCoupon extends BaseEntity {
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "coupon_id", nullable = false)
	private Coupon coupon;

	// goods_orders.id 참조. nullable (쿠폰 미사용 상태일 수 있음)
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "order_id")
	private GoodsOrder order;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private UserCouponStatus status;

	@Column(name = "used_at")
	private LocalDateTime usedAt;
}

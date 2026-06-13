package com.back.popspot.domain.popupStore.entity;

import java.time.LocalDateTime;

import com.back.popspot.domain.popupStore.dto.PopupStoreCreateRequest;
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
@Table(name = "popup_store")
public class PopupStore extends BaseEntity {
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(nullable = false)
	private String title;

	@Column(nullable = false)
	private String location;

	@Enumerated(EnumType.STRING)
	@Column(name = "fee_type", nullable = false)
	private PopupFeeType feeType;

	@Column
	private Integer price;

	@Column(name = "reservation_start_at")
	private LocalDateTime reservationStartAt;

	@Column(name = "reservation_end_at")
	private LocalDateTime reservationEndAt;

	@Column(name = "open_date")
	private LocalDateTime openDate;

	@Column(name = "close_date")
	private LocalDateTime closeDate;

	@Column(name = "image_key", length = 255)
	private String imageKey;

	@Column(length = 255)
	private String description;

	private PopupStore(User user, PopupStoreCreateRequest request) {
		this.user = user;
		this.title = request.title();
		this.location = request.location();
		this.feeType = request.feeType();
		this.price = request.price();
		this.reservationStartAt = request.reservationStartAt();
		this.reservationEndAt = request.reservationEndAt();
		this.openDate = request.openDate();
		this.closeDate = request.closeDate();
		this.imageKey = request.imageKey();
		this.description = request.description();
	}

	public static PopupStore of(User user, PopupStoreCreateRequest request) {
		return new PopupStore(user, request);
	}

	/**
	 * 예약 기간(reservationStartAt ~ reservationEndAt)과 기준 시각을 비교해 진행 상태를 계산한다.
	 * <ul>
	 *     <li>예약 시작 전(start &gt; now) → UPCOMING</li>
	 *     <li>예약 진행 중(start &le; now &lt; end) → OPEN</li>
	 *     <li>예약 종료(end &le; now) → CLOSED</li>
	 * </ul>
	 */
	public PopupStatus calculateStatus(LocalDateTime now) {
		if (reservationStartAt != null && reservationStartAt.isAfter(now)) {
			return PopupStatus.UPCOMING;
		}
		if (reservationEndAt != null && reservationEndAt.isAfter(now)) {
			return PopupStatus.OPEN;
		}
		return PopupStatus.CLOSED;
	}
}

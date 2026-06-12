package com.back.popspot.domain.popupStore.entity;

import java.time.LocalDateTime;

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
}

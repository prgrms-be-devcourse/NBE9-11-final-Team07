package com.back.popspot.domain.reservation.entity;

import java.time.LocalDateTime;

import com.back.popspot.domain.popupStore.entity.ReservationSlot;
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
	name = "reservation",
	uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "slot_id"})
)
public class Reservation extends BaseEntity {
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "member_id", nullable = false)
	private User member;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "slot_id", nullable = false)
	private ReservationSlot slot;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ReservationStatus status;

	@Column(name = "held_until")
	private LocalDateTime heldUntil;

	@Column(name = "reserved_at")
	private LocalDateTime reservedAt;

	@Column(name = "canceled_at")
	private LocalDateTime canceledAt;
}

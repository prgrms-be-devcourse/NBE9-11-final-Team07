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
	uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "slot_id"})
)
public class Reservation extends BaseEntity {
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

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

	@Column(name = "reservation_name", length = 50)
	private String reservationName;

	@Column(name = "reservation_phone", length = 20)
	private String reservationPhone;

	private Reservation(
		User user,
		ReservationSlot slot,
		ReservationStatus status,
		LocalDateTime heldUntil,
		LocalDateTime reservedAt
	) {
		this.user = user;
		this.slot = slot;
		this.status = status;
		this.heldUntil = heldUntil;
		this.reservedAt = reservedAt;
	}

	public static Reservation createHeld(User user, ReservationSlot slot, LocalDateTime now, LocalDateTime heldUntil) {
		return new Reservation(user, slot, ReservationStatus.HELD, heldUntil, now);
	}

	public void cancel(LocalDateTime canceledAt) {
		this.status = ReservationStatus.CANCELED;
		this.canceledAt = canceledAt;
	}

	public void updateReservationInfo(String reservationName, String reservationPhone) {
		this.reservationName = reservationName;
		this.reservationPhone = reservationPhone;
	}

	public void confirm() {
		this.status = ReservationStatus.CONFIRMED;
	}

	public void expire() {
		this.status = ReservationStatus.EXPIRED;
	}
}

package com.back.popspot.domain.reservation.entity;

import com.back.popspot.domain.popupStore.entity.ReservationSlot;
import com.back.popspot.domain.user.entity.User;
import com.back.popspot.global.entity.BaseEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
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
	name = "reservation_waitlist",
	uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "slot_id"}),
	indexes = @Index(name = "idx_reservation_waitlist_slot_id", columnList = "slot_id")
)
public class ReservationWaitlist extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "slot_id", nullable = false)
	private ReservationSlot slot;

	private ReservationWaitlist(User user, ReservationSlot slot) {
		this.user = user;
		this.slot = slot;
	}

	public static ReservationWaitlist of(User user, ReservationSlot slot) {
		return new ReservationWaitlist(user, slot);
	}
}

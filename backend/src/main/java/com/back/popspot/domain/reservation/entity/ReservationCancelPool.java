package com.back.popspot.domain.reservation.entity;

import java.time.LocalDateTime;

import com.back.popspot.domain.popupStore.entity.ReservationSlot;
import com.back.popspot.global.entity.BaseEntity;
import com.back.popspot.global.exception.BusinessException;
import com.back.popspot.global.exception.ErrorCode;

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
	name = "reservation_cancel_pool",
	uniqueConstraints = @UniqueConstraint(columnNames = {"slot_id", "reopen_at"})
)
public class ReservationCancelPool extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "slot_id", nullable = false)
	private ReservationSlot slot;

	@Column(name = "reopen_at", nullable = false)
	private LocalDateTime reopenAt;

	@Column(name = "pending_count", nullable = false)
	private int pendingCount;

	@Column(name = "opened_count", nullable = false)
	private int openedCount;

	@Enumerated(EnumType.STRING)
	@Column(name = "reopen_status", nullable = false)
	private ReservationCancelPoolStatus reopenStatus;

	private ReservationCancelPool(ReservationSlot slot, LocalDateTime reopenAt) {
		this.slot = slot;
		this.reopenAt = reopenAt;
		this.pendingCount = 0;
		this.openedCount = 0;
		this.reopenStatus = ReservationCancelPoolStatus.SCHEDULED;
	}

	public static ReservationCancelPool create(ReservationSlot slot, LocalDateTime reopenAt) {
		return new ReservationCancelPool(slot, reopenAt);
	}

	public void increasePending() {
		if (reopenStatus != ReservationCancelPoolStatus.SCHEDULED) {
			throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
		}
		this.pendingCount++;
	}

	public int openPending() {
		if (pendingCount < 0) {
			throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
		}
		int opened = pendingCount;
		this.pendingCount = 0;
		this.openedCount += opened;
		this.reopenStatus = ReservationCancelPoolStatus.OPENED;
		return opened;
	}

	public void fail() {
		this.reopenStatus = ReservationCancelPoolStatus.FAILED;
	}
}

package com.back.popspot.domain.popupStore.entity;

import java.time.LocalDate;
import java.time.LocalTime;

import com.back.popspot.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "reservation_slot")
public class ReservationSlot extends BaseEntity {
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "popup_store_id", nullable = false)
	private PopupStore popupStore;

	@Column(name = "slot_date", nullable = false)
	private LocalDate slotDate;

	@Column(name = "start_time", nullable = false)
	private LocalTime startTime;

	@Column(nullable = false)
	private int capacity;

	@Column(name = "reserved_count", nullable = false)
	private int reservedCount;
}

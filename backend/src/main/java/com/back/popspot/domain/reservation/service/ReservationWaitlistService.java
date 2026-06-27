package com.back.popspot.domain.reservation.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.back.popspot.domain.popupStore.entity.ReservationSlot;
import com.back.popspot.domain.reservation.entity.ReservationWaitlist;
import com.back.popspot.domain.reservation.repository.ReservationWaitlistRepository;
import com.back.popspot.domain.user.entity.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReservationWaitlistService {

	private final ReservationWaitlistRepository reservationWaitlistRepository;

	@Transactional
	public void registerIfAvailable(User user, ReservationSlot slot) {
		if (reservationWaitlistRepository.existsByUserIdAndSlotId(user.getId(), slot.getId())) {
			return;
		}

		if (reservationWaitlistRepository.countBySlotId(slot.getId()) >= slot.getCapacity()) {
			return;
		}

		reservationWaitlistRepository.save(ReservationWaitlist.of(user, slot));
	}

	@Transactional
	public void deleteByConfirmedReservation(User user, ReservationSlot slot) {
		reservationWaitlistRepository.deleteByUserIdAndSlotId(user.getId(), slot.getId());
	}
}

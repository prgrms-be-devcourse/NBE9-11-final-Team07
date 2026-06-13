package com.back.popspot.domain.reservation.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.back.popspot.domain.popupStore.entity.PopupStore;
import com.back.popspot.domain.popupStore.entity.ReservationSlot;
import com.back.popspot.domain.popupStore.repository.ReservationSlotRepository;
import com.back.popspot.domain.reservation.dto.request.ReservationCreateRequest;
import com.back.popspot.domain.reservation.dto.response.ReservationCreateResponse;
import com.back.popspot.domain.reservation.entity.Reservation;
import com.back.popspot.domain.reservation.repository.ReservationRepository;
import com.back.popspot.domain.user.entity.User;
import com.back.popspot.domain.user.repository.UserRepository;
import com.back.popspot.global.exception.BusinessException;
import com.back.popspot.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReservationService {

	private static final long HOLD_MINUTES = 5L;

	private final ReservationRepository reservationRepository;
	private final ReservationSlotRepository reservationSlotRepository;
	private final UserRepository userRepository;

	@Transactional
	public ReservationCreateResponse createReservation(ReservationCreateRequest request) {
		ReservationSlot slot = reservationSlotRepository.findById(request.slotId())
			.orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_SLOT_NOT_FOUND));

		LocalDateTime now = LocalDateTime.now();
		LocalDateTime slotDateTime = LocalDateTime.of(slot.getSlotDate(), slot.getStartTime());
		// 이미 지난 슬롯 (정책 회의후 제거 검토)
		if (!slotDateTime.isAfter(now)) {
			throw new BusinessException(ErrorCode.RESERVATION_SLOT_ALREADY_STARTED);
		}

		PopupStore popupStore = slot.getPopupStore();
		// 예약 가능 기간 검증
		if (now.isBefore(popupStore.getReservationStartAt()) || now.isAfter(popupStore.getReservationEndAt())) {
			throw new BusinessException(ErrorCode.POPUP_RESERVATION_NOT_AVAILABLE);
		}

		User user = userRepository.findById(request.userId())
			.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

		// 중복 예약 방지
		if (reservationRepository.existsByMemberIdAndSlotId(user.getId(), slot.getId())) {
			throw new BusinessException(ErrorCode.RESERVATION_ALREADY_EXISTS);
		}


		int updatedCount = reservationSlotRepository.increaseReservedCountIfAvailable(slot.getId());
		if (updatedCount == 0) {
			throw new BusinessException(ErrorCode.RESERVATION_CAPACITY_EXCEEDED);
		}

		LocalDateTime heldUntil = now.plusMinutes(HOLD_MINUTES);
		Reservation reservation = Reservation.createHeld(user, slot, now, heldUntil);
		reservationRepository.save(reservation);

		return ReservationCreateResponse.from(reservation);
	}
}

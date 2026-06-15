package com.back.popspot.domain.reservation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.back.popspot.domain.popupStore.entity.PopupStore;
import com.back.popspot.domain.popupStore.entity.ReservationSlot;
import com.back.popspot.domain.popupStore.repository.ReservationSlotRepository;
import com.back.popspot.domain.reservation.dto.request.ReservationCreateRequest;
import com.back.popspot.domain.reservation.dto.response.ReservationCreateResponse;
import com.back.popspot.domain.reservation.entity.Reservation;
import com.back.popspot.domain.reservation.entity.ReservationStatus;
import com.back.popspot.domain.reservation.repository.ReservationRepository;
import com.back.popspot.domain.user.entity.User;
import com.back.popspot.domain.user.repository.UserRepository;
import com.back.popspot.global.exception.BusinessException;
import com.back.popspot.global.exception.ErrorCode;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

	@Mock
	private ReservationRepository reservationRepository;

	@Mock
	private ReservationSlotRepository reservationSlotRepository;

	@Mock
	private UserRepository userRepository;

	@Test
	@DisplayName("예약 선점 성공")
	void createReservation_holdSuccess() {
		// given
		ReservationService reservationService = new ReservationService(
			reservationRepository,
			reservationSlotRepository,
			userRepository
		);
		ReservationCreateRequest request = new ReservationCreateRequest(1L, 2L);
		PopupStore popupStore = createPopupStore();
		ReservationSlot slot = createReservationSlot(popupStore);
		User user = createUser(2L);

		when(reservationSlotRepository.findById(1L)).thenReturn(Optional.of(slot));
		when(userRepository.findById(2L)).thenReturn(Optional.of(user));
		when(reservationRepository.existsByMemberIdAndSlotId(2L, 1L)).thenReturn(false);
		when(reservationSlotRepository.increaseReservedCountIfAvailable(1L)).thenReturn(1);
		when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> {
			Reservation reservation = invocation.getArgument(0);
			ReflectionTestUtils.setField(reservation, "id", 100L);
			return reservation;
		});

		// when
		ReservationCreateResponse response = reservationService.createReservation(request);

		// then
		assertEquals(100L, response.reservationId());
		assertEquals(ReservationStatus.HELD, response.status());
		assertEquals(1L, response.slotId());
		assertEquals(slot.getSlotDate(), response.slotDate());
		assertEquals(slot.getStartTime(), response.startTime());
		assertNotNull(response.heldUntil());
		verify(reservationRepository).save(any(Reservation.class));
	}

	@Test
	@DisplayName("같은 유저 같은 슬롯 중복 선점 실패")
	void createReservation_fail_duplicateHold() {
		// given
		ReservationService reservationService = new ReservationService(
			reservationRepository,
			reservationSlotRepository,
			userRepository
		);
		ReservationCreateRequest request = new ReservationCreateRequest(1L, 2L);
		PopupStore popupStore = createPopupStore();
		ReservationSlot slot = createReservationSlot(popupStore);
		User user = createUser(2L);

		when(reservationSlotRepository.findById(1L)).thenReturn(Optional.of(slot));
		when(userRepository.findById(2L)).thenReturn(Optional.of(user));
		when(reservationRepository.existsByMemberIdAndSlotId(2L, 1L)).thenReturn(true);

		// when
		BusinessException exception = assertThrows(
			BusinessException.class,
			() -> reservationService.createReservation(request)
		);

		// then
		assertEquals(ErrorCode.RESERVATION_ALREADY_EXISTS, exception.getErrorCode());
	}

	@Test
	@DisplayName("존재하지 않는 슬롯이면 선점 실패")
	void createReservation_fail_slotNotFound() {
		// given
		ReservationService reservationService = new ReservationService(
			reservationRepository,
			reservationSlotRepository,
			userRepository
		);
		ReservationCreateRequest request = new ReservationCreateRequest(1L, 2L);

		when(reservationSlotRepository.findById(1L)).thenReturn(Optional.empty());

		// when
		BusinessException exception = assertThrows(
			BusinessException.class,
			() -> reservationService.createReservation(request)
		);

		// then
		assertEquals(ErrorCode.RESERVATION_SLOT_NOT_FOUND, exception.getErrorCode());
	}

	@Test
	@DisplayName("예약 가능 기간이 아니면 선점 실패")
	void createReservation_fail_popupReservationNotAvailable() {
		// given
		ReservationService reservationService = new ReservationService(
			reservationRepository,
			reservationSlotRepository,
			userRepository
		);
		ReservationCreateRequest request = new ReservationCreateRequest(1L, 2L);
		PopupStore popupStore = createPopupStore();
		ReservationSlot slot = createReservationSlot(popupStore);

		ReflectionTestUtils.setField(popupStore, "reservationStartAt", LocalDateTime.now().plusDays(1));
		ReflectionTestUtils.setField(popupStore, "reservationEndAt", LocalDateTime.now().plusDays(2));

		when(reservationSlotRepository.findById(1L)).thenReturn(Optional.of(slot));

		// when
		BusinessException exception = assertThrows(
			BusinessException.class,
			() -> reservationService.createReservation(request)
		);

		// then
		assertEquals(ErrorCode.POPUP_RESERVATION_NOT_AVAILABLE, exception.getErrorCode());
	}

	@Test
	@DisplayName("슬롯 정원 초과로 선점 실패")
	void createReservation_fail_capacityExceeded() {
		// given
		ReservationService reservationService = new ReservationService(
			reservationRepository,
			reservationSlotRepository,
			userRepository
		);
		ReservationCreateRequest request = new ReservationCreateRequest(1L, 2L);
		PopupStore popupStore = createPopupStore();
		ReservationSlot slot = createReservationSlot(popupStore);
		User user = createUser(2L);

		when(reservationSlotRepository.findById(1L)).thenReturn(Optional.of(slot));
		when(userRepository.findById(2L)).thenReturn(Optional.of(user));
		when(reservationRepository.existsByMemberIdAndSlotId(2L, 1L)).thenReturn(false);
		when(reservationSlotRepository.increaseReservedCountIfAvailable(1L)).thenReturn(0);

		// when
		BusinessException exception = assertThrows(
			BusinessException.class,
			() -> reservationService.createReservation(request)
		);

		// then
		assertEquals(ErrorCode.RESERVATION_CAPACITY_EXCEEDED, exception.getErrorCode());
	}

	private PopupStore createPopupStore() {
		PopupStore popupStore = new PopupStore();
		LocalDateTime now = LocalDateTime.now();

		ReflectionTestUtils.setField(popupStore, "reservationStartAt", now.minusDays(1));
		ReflectionTestUtils.setField(popupStore, "reservationEndAt", now.plusDays(1));

		return popupStore;
	}

	private ReservationSlot createReservationSlot(PopupStore popupStore) {
		ReservationSlot slot = new ReservationSlot();

		ReflectionTestUtils.setField(slot, "id", 1L);
		ReflectionTestUtils.setField(slot, "popupStore", popupStore);
		ReflectionTestUtils.setField(slot, "slotDate", LocalDate.now().plusDays(1));
		ReflectionTestUtils.setField(slot, "startTime", LocalTime.of(10, 0));
		ReflectionTestUtils.setField(slot, "capacity", 10);
		ReflectionTestUtils.setField(slot, "reservedCount", 0);

		return slot;
	}

	private User createUser(Long userId) {
		User user = User.create("user@test.com", "user");
		ReflectionTestUtils.setField(user, "id", userId);

		return user;
	}
}

package com.back.popspot.domain.popupStore.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import com.back.popspot.domain.popupStore.dto.PopupStoreDetailResponse;
import com.back.popspot.domain.popupStore.dto.PopupStoreListResponse;
import com.back.popspot.domain.popupStore.dto.ReservationSlotResponse;
import com.back.popspot.domain.popupStore.entity.PopupFeeType;
import com.back.popspot.domain.popupStore.entity.PopupStatus;
import com.back.popspot.domain.popupStore.entity.PopupStore;
import com.back.popspot.domain.popupStore.entity.ReservationSlot;
import com.back.popspot.domain.popupStore.repository.PopupStoreRepository;
import com.back.popspot.domain.popupStore.repository.ReservationSlotRepository;
import com.back.popspot.global.exception.BusinessException;
import com.back.popspot.global.exception.ErrorCode;

/**
 * PopupStoreService 조회(목록/상세/슬롯) 분기·매핑 로직 단위 테스트 (repository 는 mock).
 * JPQL 자체는 검증하지 않으며, status 분기와 calculateStatus 매핑만 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class PopupStoreServiceTest {

	@Mock
	private PopupStoreRepository popupStoreRepository;

	@Mock
	private ReservationSlotRepository reservationSlotRepository;

	@InjectMocks
	private PopupStoreService popupStoreService;

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 13, 12, 0);
	private static final LocalDate DATE = LocalDate.of(2026, 6, 14);
	private static final Pageable PAGE = PageRequest.of(0, 10);

	@Test
	@DisplayName("status 가 null 이면 findAll 로 전체 조회한다")
	void getPopupStores_nullStatus_callsFindAll() {
		PopupStore openPopup = popupStore(1L, NOW.minusDays(1), NOW.plusDays(1)); // OPEN
		when(popupStoreRepository.findAll(PAGE)).thenReturn(new PageImpl<>(List.of(openPopup)));

		Page<PopupStoreListResponse> result = popupStoreService.getPopupStores(null, PAGE);

		verify(popupStoreRepository).findAll(PAGE);
		verify(popupStoreRepository, never()).findOpen(any(), any());
		verify(popupStoreRepository, never()).findUpcoming(any(), any());
		verify(popupStoreRepository, never()).findClosed(any(), any());
		assertThat(result.getContent()).hasSize(1);
		assertThat(result.getContent().get(0).status()).isEqualTo(PopupStatus.OPEN);
	}

	@Test
	@DisplayName("status 가 OPEN 이면 findOpen 을 호출한다")
	void getPopupStores_openStatus_callsFindOpen() {
		PopupStore openPopup = popupStore(2L, NOW.minusDays(1), NOW.plusDays(1));
		when(popupStoreRepository.findOpen(any(LocalDateTime.class), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(openPopup)));

		Page<PopupStoreListResponse> result = popupStoreService.getPopupStores(PopupStatus.OPEN, PAGE);

		verify(popupStoreRepository).findOpen(any(LocalDateTime.class), any(Pageable.class));
		verify(popupStoreRepository, never()).findAll(any(Pageable.class));
		assertThat(result.getContent()).hasSize(1);
	}

	@Test
	@DisplayName("조회 결과를 calculateStatus 로 매핑해 응답 필드를 채운다")
	void getPopupStores_mapsResponseFields() {
		PopupStore upcomingPopup = popupStore(3L, NOW.plusDays(1), NOW.plusDays(2)); // UPCOMING
		when(popupStoreRepository.findAll(PAGE)).thenReturn(new PageImpl<>(List.of(upcomingPopup)));

		PopupStoreListResponse response = popupStoreService.getPopupStores(null, PAGE)
				.getContent().get(0);

		assertThat(response.id()).isEqualTo(3L);
		assertThat(response.title()).isEqualTo("title3");
		assertThat(response.location()).isEqualTo("location3");
		assertThat(response.imageKey()).isEqualTo("image3");
		assertThat(response.status()).isEqualTo(PopupStatus.UPCOMING);
	}

	@Test
	@DisplayName("상세 조회: 존재하면 calculateStatus 로 status 를 채워 응답한다")
	void getPopupStore_found_returnsDetailWithStatus() {
		PopupStore openPopup = popupStore(10L, NOW.minusDays(1), NOW.plusDays(1)); // OPEN
		ReflectionTestUtils.setField(openPopup, "description", "설명");
		ReflectionTestUtils.setField(openPopup, "feeType", PopupFeeType.PAID);
		ReflectionTestUtils.setField(openPopup, "price", 10000);
		when(popupStoreRepository.findById(10L)).thenReturn(Optional.of(openPopup));

		PopupStoreDetailResponse response = popupStoreService.getPopupStore(10L);

		assertThat(response.id()).isEqualTo(10L);
		assertThat(response.title()).isEqualTo("title10");
		assertThat(response.description()).isEqualTo("설명");
		assertThat(response.feeType()).isEqualTo(PopupFeeType.PAID);
		assertThat(response.price()).isEqualTo(10000);
		assertThat(response.status()).isEqualTo(PopupStatus.OPEN);
	}

	@Test
	@DisplayName("상세 조회: 존재하지 않으면 RESOURCE_NOT_FOUND 예외")
	void getPopupStore_notFound_throwsBusinessException() {
		when(popupStoreRepository.findById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> popupStoreService.getPopupStore(99L))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
	}

	@Test
	@DisplayName("슬롯 조회: 팝업 존재 시 슬롯 목록 반환 + available 계산(reservedCount < capacity)")
	void getSlots_popupExists_returnsSlotsWithAvailability() {
		when(popupStoreRepository.existsById(1L)).thenReturn(true);
		when(reservationSlotRepository.findByPopupStoreIdAndSlotDate(1L, DATE))
				.thenReturn(List.of(slot(100L, 10, 3), slot(101L, 5, 5)));

		List<ReservationSlotResponse> result = popupStoreService.getSlots(1L, DATE);

		assertThat(result).hasSize(2);
		assertThat(result.get(0).slotId()).isEqualTo(100L);
		assertThat(result.get(0).available()).isTrue();  // 3 < 10
		assertThat(result.get(1).available()).isFalse(); // 5 == 5 (정원 마감)
	}

	@Test
	@DisplayName("슬롯 조회: 팝업은 있지만 그날 슬롯이 없으면 빈 리스트")
	void getSlots_popupExistsButNoSlots_returnsEmpty() {
		when(popupStoreRepository.existsById(1L)).thenReturn(true);
		when(reservationSlotRepository.findByPopupStoreIdAndSlotDate(1L, DATE))
				.thenReturn(List.of());

		List<ReservationSlotResponse> result = popupStoreService.getSlots(1L, DATE);

		assertThat(result).isEmpty();
	}

	@Test
	@DisplayName("슬롯 조회: 팝업이 없으면 RESOURCE_NOT_FOUND 이고 슬롯 조회는 하지 않는다")
	void getSlots_popupNotFound_throwsBusinessException() {
		when(popupStoreRepository.existsById(99L)).thenReturn(false);

		assertThatThrownBy(() -> popupStoreService.getSlots(99L, DATE))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

		verify(reservationSlotRepository, never()).findByPopupStoreIdAndSlotDate(any(), any());
	}

	private ReservationSlot slot(Long id, int capacity, int reservedCount) {
		ReservationSlot slot = new ReservationSlot();
		ReflectionTestUtils.setField(slot, "id", id);
		ReflectionTestUtils.setField(slot, "slotDate", DATE);
		ReflectionTestUtils.setField(slot, "startTime", LocalTime.of(10, 0));
		ReflectionTestUtils.setField(slot, "capacity", capacity);
		ReflectionTestUtils.setField(slot, "reservedCount", reservedCount);
		return slot;
	}

	private PopupStore popupStore(Long id, LocalDateTime reservationStartAt, LocalDateTime reservationEndAt) {
		PopupStore popupStore = new PopupStore();
		ReflectionTestUtils.setField(popupStore, "id", id);
		ReflectionTestUtils.setField(popupStore, "title", "title" + id);
		ReflectionTestUtils.setField(popupStore, "location", "location" + id);
		ReflectionTestUtils.setField(popupStore, "imageKey", "image" + id);
		ReflectionTestUtils.setField(popupStore, "openDate", reservationStartAt);
		ReflectionTestUtils.setField(popupStore, "closeDate", reservationEndAt);
		ReflectionTestUtils.setField(popupStore, "reservationStartAt", reservationStartAt);
		ReflectionTestUtils.setField(popupStore, "reservationEndAt", reservationEndAt);
		return popupStore;
	}
}

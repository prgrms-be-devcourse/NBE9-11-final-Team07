package com.back.popspot.domain.popupStore.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

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

import com.back.popspot.domain.popupStore.dto.PopupStoreListResponse;
import com.back.popspot.domain.popupStore.entity.PopupStatus;
import com.back.popspot.domain.popupStore.entity.PopupStore;
import com.back.popspot.domain.popupStore.repository.PopupStoreRepository;

/**
 * PopupStoreService 분기/매핑 로직 단위 테스트 (repository 는 mock).
 * JPQL 자체는 검증하지 않으며, status 분기와 calculateStatus 매핑만 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class PopupStoreServiceTest {

	@Mock
	private PopupStoreRepository popupStoreRepository;

	@InjectMocks
	private PopupStoreService popupStoreService;

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 13, 12, 0);
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

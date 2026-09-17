package com.back.popspot.domain.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;

import com.back.popspot.domain.popupStore.entity.PopupStore;
import com.back.popspot.domain.popupStore.repository.PopupStoreRepository;
import com.back.popspot.domain.popupStore.repository.ReservationSlotRepository;
import com.back.popspot.domain.reservation.dto.ReservationCapacityRebuildResult;
import com.back.popspot.global.exception.BusinessException;
import com.back.popspot.global.exception.ErrorCode;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReservationCapacityRecoveryService 단위 테스트")
class ReservationCapacityRecoveryServiceTest {

	@Mock
	private PopupStoreRepository popupStoreRepository;

	@Mock
	private ReservationSlotRepository reservationSlotRepository;

	@Mock
	private ReservationCapacityRebuildService reservationCapacityRebuildService;

	@InjectMocks
	private ReservationCapacityRecoveryService service;

	private Logger serviceLogger;
	private ListAppender<ILoggingEvent> logCaptor;

	@BeforeEach
	void setUpLogCaptor() {
		serviceLogger = (Logger) LoggerFactory.getLogger(ReservationCapacityRecoveryService.class);
		logCaptor = new ListAppender<>();
		logCaptor.start();
		serviceLogger.addAppender(logCaptor);
	}

	@AfterEach
	void tearDownLogCaptor() {
		serviceLogger.detachAppender(logCaptor);
	}

	@Test
	@DisplayName("열린 팝업의 모든 슬롯에 대해 rebuildSlotRemaining을 호출한다")
	void recoverAll_callsRebuildForEverySlot() {
		givenOpenPopups(1L, 2L);
		given(reservationSlotRepository.findIdsByPopupStoreIdIn(List.of(1L, 2L)))
			.willReturn(List.of(10L, 11L, 12L));

		service.recoverAll();

		verify(reservationCapacityRebuildService).rebuildSlotRemaining(10L);
		verify(reservationCapacityRebuildService).rebuildSlotRemaining(11L);
		verify(reservationCapacityRebuildService).rebuildSlotRemaining(12L);
	}

	@Test
	@DisplayName("한 슬롯의 rebuild가 예외를 던져도 나머지 슬롯 복구는 계속된다 (슬롯 단위 예외 격리)")
	void recoverAll_isolatesSlotFailure() {
		givenOpenPopups(1L);
		given(reservationSlotRepository.findIdsByPopupStoreIdIn(List.of(1L)))
			.willReturn(List.of(10L, 11L, 12L));
		// 10L, 12L 은 정상 처리, 가운데 11L 만 예외
		given(reservationCapacityRebuildService.rebuildSlotRemaining(anyLong()))
			.willReturn(sampleResult());
		given(reservationCapacityRebuildService.rebuildSlotRemaining(11L))
			.willThrow(new BusinessException(ErrorCode.RESERVATION_CAPACITY_OVERBOOKING_SUSPECTED));

		// 예외가 recoverAll() 밖으로 전파되지 않아야 한다.
		service.recoverAll();

		// 실패한 11L 앞뒤의 10L, 12L 모두 호출됨 (한 슬롯 실패가 나머지를 막지 않는다)
		verify(reservationCapacityRebuildService).rebuildSlotRemaining(10L);
		verify(reservationCapacityRebuildService).rebuildSlotRemaining(11L);
		verify(reservationCapacityRebuildService).rebuildSlotRemaining(12L);
		// 10L, 12L 은 성공 처리로 집계된다
		assertThat(logCaptor.list)
			.anyMatch(e -> e.getLevel() == Level.INFO
				&& e.getFormattedMessage().contains("복구 완료 — 성공 2건, 실패 1건"));
		assertThat(logCaptor.list)
			.anyMatch(e -> e.getLevel() == Level.ERROR
				&& e.getFormattedMessage().contains("슬롯 복구 실패 — slotId=11"));
	}

	@Test
	@DisplayName("열린 팝업이 없으면 슬롯 조회도 rebuild도 하지 않는다")
	void recoverAll_noOpenPopups_doesNothing() {
		given(popupStoreRepository.findOpen(any(), any())).willReturn(Page.empty());

		service.recoverAll();

		verify(reservationSlotRepository, never()).findIdsByPopupStoreIdIn(any());
		verifyNoInteractions(reservationCapacityRebuildService);
	}

	@Test
	@DisplayName("열린 팝업 전체 id를 한 번에 넘겨 슬롯 목록을 조회한다")
	void recoverAll_queriesSlotsForAllOpenPopupsAtOnce() {
		givenOpenPopups(1L, 2L, 3L);
		given(reservationSlotRepository.findIdsByPopupStoreIdIn(List.of(1L, 2L, 3L)))
			.willReturn(List.of(30L));

		service.recoverAll();

		verify(reservationSlotRepository, times(1)).findIdsByPopupStoreIdIn(List.of(1L, 2L, 3L));
	}

	private ReservationCapacityRebuildResult sampleResult() {
		return ReservationCapacityRebuildResult.from(0L, 0, 0L, null, 0L);
	}

	private void givenOpenPopups(Long... ids) {
		List<PopupStore> popups = Arrays.stream(ids)
			.map(id -> {
				PopupStore popup = new PopupStore();
				ReflectionTestUtils.setField(popup, "id", id);
				return popup;
			})
			.toList();
		given(popupStoreRepository.findOpen(any(), any())).willReturn(new PageImpl<>(popups));
	}
}

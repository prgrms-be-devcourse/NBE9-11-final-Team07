package com.back.popspot.domain.popupStore.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * PopupStore.calculateStatus 경계 로직 단위 테스트 (Spring 컨텍스트 없이).
 */
class PopupStoreTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 13, 12, 0);

	private PopupStore popupStore(LocalDateTime reservationStartAt, LocalDateTime reservationEndAt) {
		PopupStore popupStore = new PopupStore();
		ReflectionTestUtils.setField(popupStore, "reservationStartAt", reservationStartAt);
		ReflectionTestUtils.setField(popupStore, "reservationEndAt", reservationEndAt);
		return popupStore;
	}

	@Test
	@DisplayName("예약 시작 전(start > now)이면 UPCOMING")
	void upcoming() {
		PopupStore popupStore = popupStore(NOW.plusDays(1), NOW.plusDays(2));
		assertThat(popupStore.calculateStatus(NOW)).isEqualTo(PopupStatus.UPCOMING);
	}

	@Test
	@DisplayName("예약 진행 중(start <= now < end)이면 OPEN")
	void open() {
		PopupStore popupStore = popupStore(NOW.minusDays(1), NOW.plusDays(1));
		assertThat(popupStore.calculateStatus(NOW)).isEqualTo(PopupStatus.OPEN);
	}

	@Test
	@DisplayName("예약 종료(end <= now)면 CLOSED")
	void closed() {
		PopupStore popupStore = popupStore(NOW.minusDays(2), NOW.minusDays(1));
		assertThat(popupStore.calculateStatus(NOW)).isEqualTo(PopupStatus.CLOSED);
	}

	@Test
	@DisplayName("경계: start == now 이면 OPEN (start > now 가 아니므로)")
	void startEqualsNowIsOpen() {
		PopupStore popupStore = popupStore(NOW, NOW.plusDays(1));
		assertThat(popupStore.calculateStatus(NOW)).isEqualTo(PopupStatus.OPEN);
	}

	@Test
	@DisplayName("경계: end == now 이면 CLOSED (end > now 가 아니므로)")
	void endEqualsNowIsClosed() {
		PopupStore popupStore = popupStore(NOW.minusDays(1), NOW);
		assertThat(popupStore.calculateStatus(NOW)).isEqualTo(PopupStatus.CLOSED);
	}
}

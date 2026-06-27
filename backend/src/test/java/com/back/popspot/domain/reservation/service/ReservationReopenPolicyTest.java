package com.back.popspot.domain.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReservationReopenPolicyTest {

	private final ReservationReopenPolicy policy = new ReservationReopenPolicy();

	@Test
	@DisplayName("재오픈 마감 시각 전 취소는 당일 재오픈 시각에 배정한다")
	void calculateReopenAt_beforeCutoff_returnsTodayReopenAt() {
		LocalDateTime reopenAt = policy.calculateReopenAt(LocalDateTime.of(2026, 6, 27, 18, 54, 59));

		assertThat(reopenAt).isEqualTo(LocalDateTime.of(2026, 6, 27, 19, 0));
	}

	@Test
	@DisplayName("재오픈 마감 시각 이후 취소는 다음날 재오픈 시각에 배정한다")
	void calculateReopenAt_afterCutoff_returnsTomorrowReopenAt() {
		LocalDateTime reopenAt = policy.calculateReopenAt(LocalDateTime.of(2026, 6, 27, 18, 55));

		assertThat(reopenAt).isEqualTo(LocalDateTime.of(2026, 6, 28, 19, 0));
	}
}

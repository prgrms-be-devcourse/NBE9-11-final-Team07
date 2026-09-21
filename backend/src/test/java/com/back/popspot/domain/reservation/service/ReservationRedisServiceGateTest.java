package com.back.popspot.domain.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ReservationRedisService 복구 게이트 (인메모리 AtomicBoolean) 단위 테스트")
class ReservationRedisServiceGateTest {

	// 게이트 메서드는 redisTemplate 를 건드리지 않으므로 null 로 생성해도 된다.
	private final ReservationRedisService service = new ReservationRedisService(null);

	@Test
	@DisplayName("초기 상태는 복구 중이 아니다")
	void defaultsToNotRecovering() {
		assertThat(service.isRecovering()).isFalse();
	}

	@Test
	@DisplayName("setRecovering(true) 이후 isRecovering()은 true")
	void setRecoveringTrue() {
		service.setRecovering(true);

		assertThat(service.isRecovering()).isTrue();
	}

	@Test
	@DisplayName("게이트를 켰다가 내리면 다시 false")
	void setRecoveringBackToFalse() {
		service.setRecovering(true);
		service.setRecovering(false);

		assertThat(service.isRecovering()).isFalse();
	}
}

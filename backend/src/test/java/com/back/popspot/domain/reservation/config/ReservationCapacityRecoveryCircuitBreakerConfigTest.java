package com.back.popspot.domain.reservation.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.concurrent.ExecutorService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.back.popspot.domain.reservation.service.ReservationCapacityRecoveryCoordinator;
import com.back.popspot.domain.reservation.service.ReservationRedisService;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

@DisplayName("ReservationCapacityRecoveryCircuitBreakerConfig — redisReservation CB 상태 전이 리스너 단위 테스트")
class ReservationCapacityRecoveryCircuitBreakerConfigTest {

	private CircuitBreaker cb;
	private ReservationCapacityRecoveryCoordinator recoveryCoordinator;
	private ReservationRedisService reservationRedisService;
	private ExecutorService mockExecutor;

	@BeforeEach
	void setUp() {
		CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
		cb = registry.circuitBreaker(ReservationCapacityRecoveryCircuitBreakerConfig.CB_NAME);

		recoveryCoordinator = mock(ReservationCapacityRecoveryCoordinator.class);
		reservationRedisService = mock(ReservationRedisService.class);
		mockExecutor = mock(ExecutorService.class);

		ReservationCapacityRecoveryCircuitBreakerConfig config =
			new ReservationCapacityRecoveryCircuitBreakerConfig(registry, recoveryCoordinator, reservationRedisService);
		config.setRecoveryExecutor(mockExecutor);
		config.registerCircuitBreakerEventListeners();
	}

	@Test
	@DisplayName("CB OPEN 전이 → 복구 게이트를 켜고(setRecovering(true)), 복구 작업은 제출하지 않는다")
	void onOpen_turnsOnGate_doesNotSubmitRecovery() {
		cb.transitionToOpenState();

		verify(reservationRedisService).setRecovering(true);
		verify(mockExecutor, never()).submit(any(Runnable.class));
	}

	@Test
	@DisplayName("CB CLOSED 전이 → executor에 복구 작업이 제출되고 콜백이 즉시 리턴한다")
	void onClosed_submitsRecoveryToExecutor() {
		cb.transitionToOpenState();
		cb.transitionToHalfOpenState();

		long start = System.currentTimeMillis();
		cb.transitionToClosedState();
		long elapsed = System.currentTimeMillis() - start;

		assertThat(elapsed).isLessThan(1_000L); // mock executor — 제출만 하고 즉시 리턴
		verify(mockExecutor).submit(any(Runnable.class));
	}

	@Test
	@DisplayName("CB CLOSED 전이 → 제출된 Runnable이 coordinator.runRecovery()를 호출한다 (게이트 해제는 코디네이터 몫)")
	void onClosed_submittedRunnableDelegatesToCoordinator() {
		cb.transitionToOpenState();
		cb.transitionToHalfOpenState();
		cb.transitionToClosedState();

		ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
		verify(mockExecutor).submit(captor.capture());
		captor.getValue().run();

		verify(recoveryCoordinator).runRecovery();
	}
}

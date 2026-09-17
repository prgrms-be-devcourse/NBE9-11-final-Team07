package com.back.popspot.domain.reservation.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.context.annotation.Configuration;

import com.back.popspot.domain.reservation.service.ReservationCapacityRecoveryCoordinator;
import com.back.popspot.domain.reservation.service.ReservationRedisService;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * {@code redisReservation} 서킷브레이커 상태 전이를 예약 정원 복구 트리거로 연결한다.
 *
 * <p>대기열의 {@code QueueCircuitBreakerEventConfig}를 이식했다.
 *
 * <ul>
 *   <li><b>OPEN 전이</b>: Redis 장애 감지 → 복구 게이트를 켠다({@code setRecovering(true)}).
 *       이후 신규 예약은 DECR 직전 게이트 체크에서 거절된다.</li>
 *   <li><b>CLOSED 전이</b>: Redis가 회복됐다는 신호 → 복구 작업을 별도 스레드(데몬 executor)에 제출한다.
 *       CB 이벤트 콜백 스레드를 블로킹하지 않기 위함. 게이트는 복구가 성공한 뒤 코디네이터가 내린다.</li>
 * </ul>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class ReservationCapacityRecoveryCircuitBreakerConfig {

	static final String CB_NAME = "redisReservation";

	private final CircuitBreakerRegistry circuitBreakerRegistry;
	private final ReservationCapacityRecoveryCoordinator recoveryCoordinator;
	private final ReservationRedisService reservationRedisService;

	// CB 이벤트 콜백 스레드를 블로킹하지 않기 위해 별도 스레드에 복구 작업을 제출.
	// 패키지 전용 setter로 테스트에서 mock으로 교체 가능.
	private ExecutorService recoveryExecutor = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "reservation-capacity-recovery");
		t.setDaemon(true);
		return t;
	});

	void setRecoveryExecutor(ExecutorService executor) {
		this.recoveryExecutor = executor;
	}

	@PostConstruct
	public void registerCircuitBreakerEventListeners() {
		CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(CB_NAME);

		cb.getEventPublisher().onStateTransition(event -> {
			CircuitBreaker.State to = event.getStateTransition().getToState();

			if (to == CircuitBreaker.State.OPEN) {
				log.warn("[CB] redisReservation → OPEN: 예약 복구 게이트 활성화");
				reservationRedisService.setRecovering(true);
			} else if (to == CircuitBreaker.State.CLOSED) {
				log.info("[CB] redisReservation → CLOSED: 예약 정원 복구 작업 제출");
				recoveryExecutor.submit(recoveryCoordinator::runRecovery);
			}
		});
	}
}

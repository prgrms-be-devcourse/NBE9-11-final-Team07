package com.back.popspot.domain.reservation.service;

import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Component;

import com.back.popspot.domain.reservation.config.ReservationCapacityRecoveryProperties;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockingTaskExecutor.TaskResult;
import net.javacrumbs.shedlock.core.LockingTaskExecutor.TaskWithResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 예약 정원 복구를 "한 인스턴스만" 실행하도록 조정하고, 복구가 성공한 뒤에만 게이트를 내린다.
 *
 * <p>대기열의 {@code QueueRecoveryCoordinator.executeAndLowerGate()}를 이식했다. 다만 대기열의
 * "게이트 내림"(executeAndLowerGate) 어휘 대신 중립적으로 {@link #runRecovery()}로 둔다.
 *
 * <p>동작:
 * <ul>
 *   <li>기존 {@link LockingTaskExecutor}(ShedLock) 빈을 재사용하고, 락 이름은 예약 전용({@link #LOCK_NAME}).</li>
 *   <li>락 획득 성공 → 이 인스턴스가 {@code recoverAll()}을 직접 실행하고, 그 성공을 확인한 뒤에만
 *       게이트를 내린다({@code setRecovering(false)}).</li>
 *   <li>락 획득 실패(다른 인스턴스가 복구 중) → 락이 풀릴 때까지 폴링하며 재시도한다. 락 보유 인스턴스가
 *       복구 도중 죽어도 다른 인스턴스가 락을 이어받아 직접 재구축하고 게이트를 내릴 수 있다.</li>
 *   <li>{@code recoverAll()} 예외 / 재시도 한도·데드라인 초과 → 게이트는 켜진 채로 유지한다.
 *       CB가 다음 사이클에 다시 CLOSED로 전이하면서 복구를 재트리거하도록 둔다.</li>
 * </ul>
 *
 * <p><b>구조적 한계</b>: ShedLock 락은 Redis 기반인데, 이 복구는 Redis 장애 상황에서 트리거될 수 있다.
 * 즉 Redis가 불안정하면 복구가 정작 필요한 순간에 락 획득 자체가 실패할 수 있다(대기열 코디네이터도 동일).
 * 재시도로 일부 완화하지만 근본 해결은 아니다.
 * 후속 과제: 락 프로바이더를 Redis와 분리(예: DB 기반 ShedLock)하거나, Redis 복구 후 별도 사이클에서
 * 재실행되도록 보강한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationCapacityRecoveryCoordinator {

	static final String LOCK_NAME = "reservation-capacity-recovery";

	private final LockingTaskExecutor lockingTaskExecutor;
	private final ReservationCapacityRecoveryService recoveryService;
	private final ReservationRedisService reservationRedisService;
	private final ReservationCapacityRecoveryProperties properties;

	/**
	 * 분산 락을 획득한 인스턴스가 {@code recoverAll()}을 직접 실행하고, 그 성공을 확인한 뒤에만 게이트를 내린다.
	 * 락 경합 시에는 락이 풀릴 때까지 재시도하며, 재시도 한도/데드라인 초과 또는 {@code recoverAll()} 예외 시에는
	 * 게이트를 유지한 채 종료한다.
	 */
	public void runRecovery() {
		int attempts = 0;
		Instant deadline = Instant.now().plusSeconds(properties.retryDeadlineSeconds());

		while (attempts < properties.maxAttempts() && Instant.now().isBefore(deadline)) {
			attempts++;
			try {
				LockConfiguration lockConfig = new LockConfiguration(
					Instant.now(),
					LOCK_NAME,
					Duration.ofSeconds(properties.lockAtMostForSeconds()),
					Duration.ZERO
				);

				TaskResult<Void> result = lockingTaskExecutor.executeWithLock(
					(TaskWithResult<Void>) () -> {
						recoveryService.recoverAll();
						return null;
					},
					lockConfig
				);

				if (result.wasExecuted()) {
					// 자신이 직접 실행한 복구가 성공했을 때만 게이트를 내린다.
					reservationRedisService.setRecovering(false);
					log.info("[ReservationCapacityRecovery] 복구 완료 — 게이트 해제 (attempt={})", attempts);
					return;
				}

				// 다른 인스턴스가 락 보유 중 — 락이 풀리면 자신이 직접 재구축하고 게이트를 내려야 하므로 재시도
				log.debug("[ReservationCapacityRecovery] 락 획득 실패, 재시도 ({}/{})",
					attempts, properties.maxAttempts());
				Thread.sleep(properties.pollIntervalSeconds() * 1000L);

			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				log.warn("[ReservationCapacityRecovery] 재시도 대기 중 인터럽트 — 게이트 유지");
				return;
			} catch (Throwable e) {
				// recoverAll() 실패 → 게이트 유지 → CB가 다음 사이클에 다시 트리거하도록 둠
				log.error("[ReservationCapacityRecovery] 복구 실패 — 게이트 유지, CB 다음 사이클 대기", e);
				return;
			}
		}

		log.error("[ReservationCapacityRecovery] 재시도 횟수/타임아웃 초과 (attempts={}) — 게이트 유지", attempts);
	}
}

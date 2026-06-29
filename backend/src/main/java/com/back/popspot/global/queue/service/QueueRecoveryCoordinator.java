package com.back.popspot.global.queue.service;

import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Component;

import com.back.popspot.global.queue.config.QueueRecoveryProperties;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockingTaskExecutor.TaskResult;
import net.javacrumbs.shedlock.core.LockingTaskExecutor.TaskWithResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueueRecoveryCoordinator {

    static final String LOCK_NAME = "queue-recovery-scheduler";

    private final LockingTaskExecutor lockingTaskExecutor;
    private final QueueRecoveryService recoveryService;
    private final WaitingQueueRedisService queueRedisService;
    private final QueueRecoveryProperties properties;

    /**
     * 분산 락을 획득한 인스턴스만 recoverAll()을 직접 실행하고, 그 성공을 확인한 뒤에만 게이트를 내린다.
     * 락 획득 실패(다른 인스턴스가 보유 중) 시에는 락이 풀릴 때까지 폴링한 뒤 자신이 직접 rebuild를 수행한다.
     * 재시도 횟수·타임아웃 초과 또는 recoverAll() 예외 발생 시에는 recovering 플래그를 유지한 채 종료한다.
     */
    public void executeAndLowerGate() {
        int attempts = 0;
        Instant deadline = Instant.now().plusSeconds(properties.lockAtMostForSeconds());

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
                    queueRedisService.setRecovering(false);
                    log.info("[RecoveryCoordinator] 복구 완료 — recovering 해제 (attempt={})", attempts);
                    return;
                }

                // 다른 인스턴스가 락 보유 중 — 락이 풀리길 기다렸다가 직접 rebuild를 수행해야 함
                log.debug("[RecoveryCoordinator] 락 획득 실패, 재시도 ({}/{})", attempts, properties.maxAttempts());
                Thread.sleep(properties.pollIntervalSeconds() * 1000L);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[RecoveryCoordinator] 재시도 대기 중 인터럽트 — recovering 유지");
                return;
            } catch (Throwable e) {
                // recoverAll() 실패 → recovering 유지 → CB가 다음 사이클에 다시 트리거하도록 둠
                log.error("[RecoveryCoordinator] 복구 실패 — recovering 유지, CB 다음 사이클 대기", e);
                return;
            }
        }

        log.error("[RecoveryCoordinator] 재시도 횟수/타임아웃 초과 (attempts={}) — recovering 유지", attempts);
    }
}

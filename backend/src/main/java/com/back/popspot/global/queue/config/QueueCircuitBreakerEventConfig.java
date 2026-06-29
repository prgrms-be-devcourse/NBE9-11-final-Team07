package com.back.popspot.global.queue.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.context.annotation.Configuration;

import com.back.popspot.global.queue.service.QueueRecoveryService;
import com.back.popspot.global.queue.service.WaitingQueueRedisService;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class QueueCircuitBreakerEventConfig {

    static final String CB_NAME = "waitingQueueRedis";

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final WaitingQueueRedisService queueRedisService;
    private final QueueRecoveryService queueRecoveryService;

    // CB 이벤트 콜백 스레드를 블로킹하지 않기 위해 별도 스레드에 복구 작업을 제출.
    // 패키지 전용 setter(setRecoveryExecutor)로 테스트에서 mock으로 교체 가능.
    private ExecutorService recoveryExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "queue-recovery");
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
                log.warn("[CB] waitingQueueRedis → OPEN: recovering 플래그 활성화");
                queueRedisService.setRecovering(true);
            } else if (to == CircuitBreaker.State.CLOSED) {
                log.info("[CB] waitingQueueRedis → CLOSED: 대기열 복구 작업 제출");
                recoveryExecutor.submit(() -> {
                    try {
                        queueRecoveryService.recoverAll();
                        queueRedisService.setRecovering(false);
                        log.info("[CB] 대기열 복구 완료 — recovering 플래그 해제");
                    } catch (Exception e) {
                        // 복구 실패 시 recovering=true 유지 → 게이트가 계속 막아 데이터 정합성 보호
                        log.error("[CB] 대기열 복구 실패 — recovering 플래그 유지", e);
                    }
                });
            }
        });
    }
}

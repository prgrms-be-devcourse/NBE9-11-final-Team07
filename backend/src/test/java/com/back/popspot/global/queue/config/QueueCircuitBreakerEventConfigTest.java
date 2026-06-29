package com.back.popspot.global.queue.config;

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

import com.back.popspot.global.queue.service.QueueRecoveryCoordinator;
import com.back.popspot.global.queue.service.WaitingQueueRedisService;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

@DisplayName("QueueCircuitBreakerEventConfig — CB 상태 전이 리스너 단위 테스트")
class QueueCircuitBreakerEventConfigTest {

    private CircuitBreakerRegistry registry;
    private CircuitBreaker cb;
    private WaitingQueueRedisService queueRedisService;
    private QueueRecoveryCoordinator recoveryCoordinator;
    private ExecutorService mockExecutor;
    private QueueCircuitBreakerEventConfig config;

    @BeforeEach
    void setUp() {
        registry = CircuitBreakerRegistry.ofDefaults();
        cb = registry.circuitBreaker(QueueCircuitBreakerEventConfig.CB_NAME);

        queueRedisService = mock(WaitingQueueRedisService.class);
        recoveryCoordinator = mock(QueueRecoveryCoordinator.class);
        mockExecutor = mock(ExecutorService.class);

        config = new QueueCircuitBreakerEventConfig(registry, queueRedisService, recoveryCoordinator);
        config.setRecoveryExecutor(mockExecutor);
        config.registerCircuitBreakerEventListeners();
    }

    // ── OPEN 전이 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("CB OPEN 전이 → recovering 플래그가 true로 세팅된다")
    void onOpen_setsRecoveringTrue() {
        cb.transitionToOpenState();

        verify(queueRedisService).setRecovering(true);
    }

    // ── CLOSED 전이: 콜백 비블로킹 ───────────────────────────────────────────

    @Test
    @DisplayName("CB CLOSED 전이 → executor에 작업이 제출되고 콜백이 즉시 리턴한다")
    void onClosed_submitsToExecutor_andCallbackReturnsImmediately() {
        cb.transitionToOpenState();
        cb.transitionToHalfOpenState();

        long start = System.currentTimeMillis();
        cb.transitionToClosedState();
        long elapsed = System.currentTimeMillis() - start;

        // 제출만 하고 즉시 리턴 — mock executor이므로 블로킹 없음
        assertThat(elapsed).isLessThan(1_000L);
        verify(mockExecutor).submit(any(Runnable.class));
    }

    // ── CLOSED 전이: 복구 작업이 coordinator에 위임된다 ─────────────────────

    @Test
    @DisplayName("CB CLOSED 전이 → 제출된 Runnable이 coordinator.executeAndLowerGate()를 호출한다")
    void onClosed_submittedRunnableDelegatesToCoordinator() {
        cb.transitionToOpenState();
        cb.transitionToHalfOpenState();
        cb.transitionToClosedState();

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(mockExecutor).submit(captor.capture());
        captor.getValue().run();

        verify(recoveryCoordinator).executeAndLowerGate();
    }

    // ── HALF_OPEN 전이: 별도 처리 없음 ──────────────────────────────────────

    @Test
    @DisplayName("CB HALF_OPEN 전이 → recovering 플래그나 executor에 별도 처리 없음")
    void onHalfOpen_noSideEffects() {
        cb.transitionToOpenState();
        cb.transitionToHalfOpenState();

        // OPEN 전이에서 setRecovering(true)가 한 번 호출됐을 뿐
        verify(queueRedisService).setRecovering(true);
        verify(mockExecutor, never()).submit(any(Runnable.class));
    }
}

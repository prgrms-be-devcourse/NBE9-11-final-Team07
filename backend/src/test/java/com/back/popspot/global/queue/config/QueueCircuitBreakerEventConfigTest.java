package com.back.popspot.global.queue.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.concurrent.ExecutorService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.back.popspot.global.queue.service.QueueRecoveryService;
import com.back.popspot.global.queue.service.WaitingQueueRedisService;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

@DisplayName("QueueCircuitBreakerEventConfig — CB 상태 전이 리스너 단위 테스트")
class QueueCircuitBreakerEventConfigTest {

    private CircuitBreakerRegistry registry;
    private CircuitBreaker cb;
    private WaitingQueueRedisService queueRedisService;
    private QueueRecoveryService queueRecoveryService;
    private ExecutorService mockExecutor;
    private QueueCircuitBreakerEventConfig config;

    @BeforeEach
    void setUp() {
        registry = CircuitBreakerRegistry.ofDefaults();
        cb = registry.circuitBreaker(QueueCircuitBreakerEventConfig.CB_NAME);

        queueRedisService = mock(WaitingQueueRedisService.class);
        queueRecoveryService = mock(QueueRecoveryService.class);
        mockExecutor = mock(ExecutorService.class);

        config = new QueueCircuitBreakerEventConfig(registry, queueRedisService, queueRecoveryService);
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

    // ── CLOSED 전이: 복구 성공 시 recovering=false ───────────────────────────

    @Test
    @DisplayName("recoverAll() 성공 → recovering 플래그가 false로 해제된다")
    void onClosed_recoverAllSucceeds_setsRecoveringFalse() {
        cb.transitionToOpenState();
        cb.transitionToHalfOpenState();
        cb.transitionToClosedState();

        // 제출된 Runnable을 꺼내 동기 실행
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(mockExecutor).submit(captor.capture());
        captor.getValue().run();

        verify(queueRecoveryService).recoverAll();
        verify(queueRedisService).setRecovering(false);
    }

    // ── CLOSED 전이: 복구 실패 시 recovering 유지 ────────────────────────────

    @Test
    @DisplayName("recoverAll() 예외 → recovering 플래그가 false로 내려가지 않는다")
    void onClosed_recoverAllFails_recoveringFlagNotCleared() {
        doThrow(new RuntimeException("Redis still down")).when(queueRecoveryService).recoverAll();

        cb.transitionToOpenState();
        cb.transitionToHalfOpenState();
        cb.transitionToClosedState();

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(mockExecutor).submit(captor.capture());
        captor.getValue().run(); // 예외를 삼키고 리턴해야 함 (executor 스레드를 죽이면 안 됨)

        verify(queueRedisService, never()).setRecovering(false);
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

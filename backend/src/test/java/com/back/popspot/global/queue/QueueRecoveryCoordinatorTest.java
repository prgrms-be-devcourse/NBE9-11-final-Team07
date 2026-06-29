package com.back.popspot.global.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import com.back.popspot.global.queue.config.QueueRecoveryProperties;
import com.back.popspot.global.queue.service.QueueRecoveryCoordinator;
import com.back.popspot.global.queue.service.QueueRecoveryService;
import com.back.popspot.global.queue.service.WaitingQueueRedisService;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockingTaskExecutor.TaskResult;
import net.javacrumbs.shedlock.core.LockingTaskExecutor.TaskWithResult;

@ExtendWith(MockitoExtension.class)
@DisplayName("QueueRecoveryCoordinator 단위 테스트")
class QueueRecoveryCoordinatorTest {

    @Mock
    LockingTaskExecutor lockingTaskExecutor;
    @Mock
    QueueRecoveryService recoveryService;
    @Mock
    WaitingQueueRedisService queueRedisService;

    private Logger coordinatorLogger;
    private ListAppender<ILoggingEvent> logCaptor;

    @BeforeEach
    void setUpLogCaptor() {
        coordinatorLogger = (Logger) LoggerFactory.getLogger(QueueRecoveryCoordinator.class);
        logCaptor = new ListAppender<>();
        logCaptor.start();
        coordinatorLogger.addAppender(logCaptor);
    }

    @AfterEach
    void tearDownLogCaptor() {
        coordinatorLogger.detachAppender(logCaptor);
        Thread.interrupted(); // 인터럽트 플래그가 남아 있으면 다른 테스트에 영향 → 초기화
    }

    /** pollIntervalSeconds=0으로 sleep 없이 빠르게 동작하는 coordinator 생성 */
    private QueueRecoveryCoordinator coordinator(int maxAttempts) {
        return new QueueRecoveryCoordinator(
            lockingTaskExecutor, recoveryService, queueRedisService,
            new QueueRecoveryProperties(maxAttempts, 0L, 300L)
        );
    }

    /**
     * executeWithLock()을 모킹하는 헬퍼.
     * ShedLock의 TaskResult는 팩토리 메서드가 package-private이므로
     * 로컬 mock으로 wasExecuted() 반환값을 제어한다.
     *
     * executed=true: 태스크(recoverAll 포함)를 직접 호출 후 wasExecuted()=true 반환
     * executed=false: 태스크 미호출, wasExecuted()=false 반환 (다른 인스턴스 락 보유 시뮬레이션)
     */
    @SuppressWarnings("unchecked")
    private void stubLock(boolean executeTask) throws Throwable {
        doAnswer(invocation -> {
            if (executeTask) {
                TaskWithResult<?> task = invocation.getArgument(0);
                task.call();
            }
            TaskResult<Void> r = mock(TaskResult.class);
            when(r.wasExecuted()).thenReturn(executeTask);
            return r;
        }).when(lockingTaskExecutor).executeWithLock(
            any(TaskWithResult.class), any(LockConfiguration.class)
        );
    }

    // ── 케이스 1 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("첫 시도 wasExecuted=true → recoverAll 1회 호출, setRecovering(false), 즉시 종료")
    void firstAttempt_executed_lowersGateAndReturns() throws Throwable {
        stubLock(true);

        coordinator(60).executeAndLowerGate();

        verify(lockingTaskExecutor, times(1)).executeWithLock(any(TaskWithResult.class), any(LockConfiguration.class));
        verify(recoveryService, times(1)).recoverAll();
        verify(queueRedisService, times(1)).setRecovering(false);
    }

    // ── 케이스 2 ─────────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("wasExecuted=false 2회 후 true → 자신이 직접 rebuild 1회 수행 후 게이트 해제 (Option 1 핵심 동작)")
    void retryAfterNotExecuted_ownRebuildAndGateLowered() throws Throwable {
        /*
         * 다른 인스턴스가 먼저 락을 잡고 rebuild 중인 시나리오:
         * 1~2번째 시도: wasExecuted=false → 태스크(=recoverAll) 미실행
         * 3번째 시도: 락 획득 성공, 자신이 직접 rebuild 실행 → wasExecuted=true
         *
         * 핵심 검증: A 인스턴스의 rebuild가 성공했더라도 B는 "어차피 누군가 했으니 끝"이
         * 아닌, 자신이 직접 실행한 rebuild를 확인한 뒤에만 게이트를 내린다.
         * → recoverAll()은 자신이 실행한 3번째 한 번만 호출돼야 한다.
         */
        AtomicInteger callCount = new AtomicInteger();
        doAnswer(invocation -> {
            int call = callCount.incrementAndGet();
            boolean myTurn = call >= 3;
            if (myTurn) {
                TaskWithResult<?> task = invocation.getArgument(0);
                task.call();
            }
            TaskResult<Void> r = mock(TaskResult.class);
            when(r.wasExecuted()).thenReturn(myTurn);
            return r;
        }).when(lockingTaskExecutor).executeWithLock(any(TaskWithResult.class), any(LockConfiguration.class));

        coordinator(60).executeAndLowerGate();

        verify(lockingTaskExecutor, times(3)).executeWithLock(any(TaskWithResult.class), any(LockConfiguration.class));
        verify(recoveryService, times(1)).recoverAll();   // 자신이 실행한 3번째 딱 1회
        verify(queueRedisService, times(1)).setRecovering(false);
    }

    // ── 케이스 3 ─────────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("recoverAll() 예외 → setRecovering(false) 미호출, error 로그 기록, executeAndLowerGate() 정상 반환")
    void recoverAllThrows_recoveringKept_errorLogged() throws Throwable {
        /*
         * 비동기 실행 경로 (recoveryExecutor.submit(coordinator::executeAndLowerGate)):
         *   - executeAndLowerGate()는 catch(Throwable)에서 예외를 삼키고 정상 반환
         *   - executor 스레드에서 미처리 예외가 올라가지 않음 → silent failure 없음
         *   - 반드시 error 레벨 로그가 남아야 운영 중 인지 가능
         */
        doThrow(new RuntimeException("Redis still down")).when(recoveryService).recoverAll();
        doAnswer(invocation -> {
            TaskWithResult<?> task = invocation.getArgument(0);
            task.call(); // RuntimeException 전파 → coordinator catch(Throwable)으로 잡힘
            return null; // 실제로는 도달하지 않음
        }).when(lockingTaskExecutor).executeWithLock(any(TaskWithResult.class), any(LockConfiguration.class));

        coordinator(60).executeAndLowerGate(); // 예외를 삼키고 정상 반환해야 함

        verify(queueRedisService, never()).setRecovering(false);
        assertThat(logCaptor.list)
            .as("recoverAll() 실패 시 coordinator가 error 로그를 남겨야 한다 (silent failure 방지)")
            .anyMatch(e -> e.getLevel() == Level.ERROR
                && e.getFormattedMessage().contains("복구 실패"));
    }

    // ── 케이스 4 ─────────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("MAX_ATTEMPTS 초과 → 정확히 N회 시도 후 종료, recovering 유지, error 로그")
    void maxAttemptsExceeded_noInfiniteLoop_recoveringKept() throws Throwable {
        doAnswer(invocation -> {
            TaskResult<Void> r = mock(TaskResult.class);
            when(r.wasExecuted()).thenReturn(false); // 항상 락 획득 실패
            return r;
        }).when(lockingTaskExecutor).executeWithLock(any(TaskWithResult.class), any(LockConfiguration.class));

        coordinator(3).executeAndLowerGate(); // maxAttempts=3

        verify(lockingTaskExecutor, times(3)).executeWithLock(any(TaskWithResult.class), any(LockConfiguration.class));
        verify(recoveryService, never()).recoverAll();
        verify(queueRedisService, never()).setRecovering(false);
        assertThat(logCaptor.list)
            .as("재시도 한도 초과 시 error 로그가 남아야 한다")
            .anyMatch(e -> e.getLevel() == Level.ERROR
                && e.getFormattedMessage().contains("재시도 횟수"));
    }

    // ── 케이스 5 ─────────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("Thread.sleep 중 인터럽트 → 인터럽트 상태 복원, setRecovering(false) 미호출, warn 로그")
    void interruptDuringSleep_restoresInterruptFlag_recoveringKept() throws Throwable {
        /*
         * doAnswer 안에서 Thread.currentThread().interrupt()를 호출해 sleep 직전 시점을 재현.
         * pollIntervalSeconds=0 → Thread.sleep(0)은 인터럽트 상태에서 즉시 InterruptedException.
         * coordinator는 catch(InterruptedException)에서:
         *   1) Thread.currentThread().interrupt()로 플래그 복원
         *   2) warn 로그 기록
         *   3) 정상 반환 (스레드 종료 금지)
         */
        doAnswer(invocation -> {
            Thread.currentThread().interrupt(); // sleep 직전 인터럽트 시뮬레이션
            TaskResult<Void> r = mock(TaskResult.class);
            when(r.wasExecuted()).thenReturn(false); // 이번엔 락 못 얻음 → sleep으로 진입
            return r;
        }).when(lockingTaskExecutor).executeWithLock(any(TaskWithResult.class), any(LockConfiguration.class));

        coordinator(60).executeAndLowerGate();

        boolean interruptRestored = Thread.interrupted(); // 읽고 초기화 (tearDown에서 재초기화하지만 여기서 명시적으로 검증)
        assertThat(interruptRestored)
            .as("coordinator는 InterruptedException 처리 후 인터럽트 상태를 복원해야 한다")
            .isTrue();
        verify(queueRedisService, never()).setRecovering(false);
        assertThat(logCaptor.list)
            .as("InterruptedException 발생 시 warn 로그가 남아야 한다")
            .anyMatch(e -> e.getLevel() == Level.WARN
                && e.getFormattedMessage().contains("인터럽트"));
    }
}

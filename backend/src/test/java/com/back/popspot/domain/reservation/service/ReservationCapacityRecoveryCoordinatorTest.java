package com.back.popspot.domain.reservation.service;

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

import com.back.popspot.domain.reservation.config.ReservationCapacityRecoveryProperties;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockingTaskExecutor.TaskResult;
import net.javacrumbs.shedlock.core.LockingTaskExecutor.TaskWithResult;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReservationCapacityRecoveryCoordinator 단위 테스트")
class ReservationCapacityRecoveryCoordinatorTest {

	@Mock
	private LockingTaskExecutor lockingTaskExecutor;

	@Mock
	private ReservationCapacityRecoveryService recoveryService;

	@Mock
	private ReservationRedisService reservationRedisService;

	private Logger coordinatorLogger;
	private ListAppender<ILoggingEvent> logCaptor;

	@BeforeEach
	void setUpLogCaptor() {
		coordinatorLogger = (Logger) LoggerFactory.getLogger(ReservationCapacityRecoveryCoordinator.class);
		logCaptor = new ListAppender<>();
		logCaptor.start();
		coordinatorLogger.addAppender(logCaptor);
	}

	@AfterEach
	void tearDownLogCaptor() {
		coordinatorLogger.detachAppender(logCaptor);
		Thread.interrupted(); // 인터럽트 플래그가 남으면 다른 테스트에 영향 → 초기화
	}

	/** pollIntervalSeconds=0 으로 sleep 없이 빠르게 도는 coordinator */
	private ReservationCapacityRecoveryCoordinator coordinator(int maxAttempts) {
		return new ReservationCapacityRecoveryCoordinator(
			lockingTaskExecutor, recoveryService, reservationRedisService,
			new ReservationCapacityRecoveryProperties(maxAttempts, 0L, 300L, 360L)
		);
	}

	/**
	 * executeWithLock() 모킹 헬퍼. ShedLock TaskResult 팩토리는 package-private 이므로 mock 으로 wasExecuted() 제어.
	 * executeTask=true 면 넘겨받은 태스크(=recoverAll 포함)를 직접 호출한다.
	 */
	@SuppressWarnings("unchecked")
	private void stubLock(boolean executeTask) throws Throwable {
		doAnswer(invocation -> {
			if (executeTask) {
				TaskWithResult<?> task = invocation.getArgument(0);
				task.call();
			}
			TaskResult<Void> result = mock(TaskResult.class);
			when(result.wasExecuted()).thenReturn(executeTask);
			return result;
		}).when(lockingTaskExecutor).executeWithLock(any(TaskWithResult.class), any(LockConfiguration.class));
	}

	// ── 케이스 1 ─────────────────────────────────────────────────────────────

	@Test
	@DisplayName("첫 시도 락 획득 성공 → recoverAll 1회 실행, 게이트 해제(setRecovering(false)), 즉시 종료")
	void firstAttempt_executed_lowersGateAndReturns() throws Throwable {
		stubLock(true);

		coordinator(60).runRecovery();

		verify(lockingTaskExecutor, times(1)).executeWithLock(any(TaskWithResult.class), any(LockConfiguration.class));
		verify(recoveryService, times(1)).recoverAll();
		verify(reservationRedisService, times(1)).setRecovering(false);
	}

	// ── 케이스 2 ─────────────────────────────────────────────────────────────

	@Test
	@SuppressWarnings("unchecked")
	@DisplayName("경합(wasExecuted=false) 2회 후 락 획득 → 자신이 직접 rebuild 1회 수행 후 게이트 해제")
	void retryAfterContention_ownRebuildAndGateLowered() throws Throwable {
		/*
		 * 다른 인스턴스가 먼저 락을 잡고 복구 중인 시나리오:
		 * 1~2번째 시도: wasExecuted=false → recoverAll 미실행
		 * 3번째 시도: 락 획득 성공 → 자신이 직접 recoverAll 실행 → wasExecuted=true
		 *
		 * 핵심: 누군가 복구했더라도 이 인스턴스는 "자신이 실행한 복구"를 확인한 뒤에만 게이트를 내린다.
		 * → recoverAll()은 3번째 딱 1회만 호출돼야 한다. (락 보유 인스턴스가 죽어도 이어받는 구조)
		 */
		AtomicInteger callCount = new AtomicInteger();
		doAnswer(invocation -> {
			int call = callCount.incrementAndGet();
			boolean myTurn = call >= 3;
			if (myTurn) {
				TaskWithResult<?> task = invocation.getArgument(0);
				task.call();
			}
			TaskResult<Void> result = mock(TaskResult.class);
			when(result.wasExecuted()).thenReturn(myTurn);
			return result;
		}).when(lockingTaskExecutor).executeWithLock(any(TaskWithResult.class), any(LockConfiguration.class));

		coordinator(60).runRecovery();

		verify(lockingTaskExecutor, times(3)).executeWithLock(any(TaskWithResult.class), any(LockConfiguration.class));
		verify(recoveryService, times(1)).recoverAll();
		verify(reservationRedisService, times(1)).setRecovering(false);
	}

	// ── 케이스 3 ─────────────────────────────────────────────────────────────

	@Test
	@SuppressWarnings("unchecked")
	@DisplayName("recoverAll() 예외 → 게이트 유지(setRecovering(false) 미호출), error 로그, 정상 반환")
	void recoverAllThrows_gateKept_errorLogged() throws Throwable {
		doThrow(new RuntimeException("Redis still down")).when(recoveryService).recoverAll();
		doAnswer(invocation -> {
			TaskWithResult<?> task = invocation.getArgument(0);
			task.call(); // RuntimeException 전파 → coordinator catch(Throwable)
			return null;
		}).when(lockingTaskExecutor).executeWithLock(any(TaskWithResult.class), any(LockConfiguration.class));

		coordinator(60).runRecovery(); // 예외를 삼키고 정상 반환

		verify(reservationRedisService, never()).setRecovering(false);
		assertThat(logCaptor.list)
			.anyMatch(e -> e.getLevel() == Level.ERROR && e.getFormattedMessage().contains("복구 실패"));
	}

	// ── 케이스 4 ─────────────────────────────────────────────────────────────

	@Test
	@SuppressWarnings("unchecked")
	@DisplayName("MAX_ATTEMPTS 초과(계속 경합) → 정확히 N회 시도 후 종료, 게이트 유지, error 로그")
	void maxAttemptsExceeded_noInfiniteLoop_gateKept() throws Throwable {
		doAnswer(invocation -> {
			TaskResult<Void> result = mock(TaskResult.class);
			when(result.wasExecuted()).thenReturn(false); // 항상 락 획득 실패
			return result;
		}).when(lockingTaskExecutor).executeWithLock(any(TaskWithResult.class), any(LockConfiguration.class));

		coordinator(3).runRecovery();

		verify(lockingTaskExecutor, times(3)).executeWithLock(any(TaskWithResult.class), any(LockConfiguration.class));
		verify(recoveryService, never()).recoverAll();
		verify(reservationRedisService, never()).setRecovering(false);
		assertThat(logCaptor.list)
			.anyMatch(e -> e.getLevel() == Level.ERROR && e.getFormattedMessage().contains("재시도 횟수"));
	}

	// ── 케이스 5 ─────────────────────────────────────────────────────────────

	@Test
	@SuppressWarnings("unchecked")
	@DisplayName("Thread.sleep 중 인터럽트 → 인터럽트 상태 복원, 게이트 유지, warn 로그")
	void interruptDuringSleep_restoresInterruptFlag_gateKept() throws Throwable {
		doAnswer(invocation -> {
			Thread.currentThread().interrupt(); // sleep 직전 인터럽트 시뮬레이션
			TaskResult<Void> result = mock(TaskResult.class);
			when(result.wasExecuted()).thenReturn(false); // 락 못 얻음 → sleep 진입
			return result;
		}).when(lockingTaskExecutor).executeWithLock(any(TaskWithResult.class), any(LockConfiguration.class));

		coordinator(60).runRecovery();

		boolean interruptRestored = Thread.interrupted();
		assertThat(interruptRestored)
			.as("coordinator는 InterruptedException 처리 후 인터럽트 상태를 복원해야 한다")
			.isTrue();
		verify(reservationRedisService, never()).setRecovering(false);
		assertThat(logCaptor.list)
			.anyMatch(e -> e.getLevel() == Level.WARN && e.getFormattedMessage().contains("인터럽트"));
	}
}

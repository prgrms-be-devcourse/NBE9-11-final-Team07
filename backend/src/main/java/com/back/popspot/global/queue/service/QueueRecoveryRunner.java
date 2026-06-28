package com.back.popspot.global.queue.service;

import java.time.Duration;
import java.time.Instant;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockingTaskExecutor.TaskResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueueRecoveryRunner implements ApplicationRunner {

	private final LockingTaskExecutor lockingTaskExecutor;
	private final QueueRecoveryService recoveryService;

	@Override
	public void run(ApplicationArguments args) {
		LockConfiguration lockConfig = new LockConfiguration(
			Instant.now(),
			"popup-admission-scheduler",
			Duration.ofMinutes(5),
			Duration.ZERO
		);
		try {
			TaskResult<Void> result = lockingTaskExecutor.executeWithLock(
				() -> {
					log.info("[QueueRecovery] 앱 기동 시 대기열 복구 시작");
					recoveryService.recoverAll();
					log.info("[QueueRecovery] 앱 기동 시 대기열 복구 완료");
					return null;
				},
				lockConfig
			);
			if (!result.wasExecuted()) {
				log.info("[QueueRecovery] 다른 인스턴스가 처리 중 — 복구 스킵");
			}
		} catch (Throwable e) {
			log.error("[QueueRecovery] 복구 중 오류 발생", e);
		}
	}
}

package com.back.popspot.global.queue.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueueRecoveryRunner implements ApplicationRunner {

	private final QueueRecoveryCoordinator recoveryCoordinator;

	@Override
	public void run(ApplicationArguments args) {
		log.info("[QueueRecovery] 앱 기동 시 대기열 복구 시작");
		recoveryCoordinator.executeAndLowerGate();
	}
}

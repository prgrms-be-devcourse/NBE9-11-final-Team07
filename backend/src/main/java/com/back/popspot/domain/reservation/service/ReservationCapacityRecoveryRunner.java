package com.back.popspot.domain.reservation.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 앱 기동 시 예약 정원 복구를 1회 실행한다.
 *
 * <p>대기열의 {@code QueueRecoveryRunner}를 이식. 인스턴스가 재시작으로 Redis 최신 상태를 놓쳤을 수 있으므로
 * 부팅 직후 DB 원장 기준으로 한 번 재구축한다. 단일 실행 보장은 {@link ReservationCapacityRecoveryCoordinator}가 담당.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationCapacityRecoveryRunner implements ApplicationRunner {

	private final ReservationCapacityRecoveryCoordinator recoveryCoordinator;

	@Override
	public void run(ApplicationArguments args) {
		log.info("[ReservationCapacityRecovery] 앱 기동 시 예약 정원 복구 시작");
		recoveryCoordinator.runRecovery();
	}
}

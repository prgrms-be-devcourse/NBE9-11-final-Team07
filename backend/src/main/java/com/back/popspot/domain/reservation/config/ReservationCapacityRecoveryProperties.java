package com.back.popspot.domain.reservation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 예약 정원 복구 코디네이터 전용 설정.
 *
 * <p>대기열의 {@code queue-recovery} 와 동일한 형태 — 값도 대기열 수준 기본값을 그대로 쓰며,
 * 실측 데이터가 쌓이면 운영 후 재조정한다(application.yml 주석 참고).
 *
 * @param maxAttempts          락 프로바이더 일시 장애 시 재시도 최대 횟수
 * @param pollIntervalSeconds  재시도 사이 대기 시간(초)
 * @param lockAtMostForSeconds ShedLock 락 최대 유지 시간(초) — 복구 도중 인스턴스가 죽어도 이 시간 뒤 락이 풀린다
 * @param retryDeadlineSeconds 재시도 전체에 대한 데드라인(초)
 */
@ConfigurationProperties(prefix = "reservation-capacity-recovery")
public record ReservationCapacityRecoveryProperties(
	int maxAttempts,
	long pollIntervalSeconds,
	long lockAtMostForSeconds,
	long retryDeadlineSeconds
) {
}

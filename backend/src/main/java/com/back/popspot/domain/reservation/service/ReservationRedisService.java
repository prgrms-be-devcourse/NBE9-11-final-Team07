package com.back.popspot.domain.reservation.service;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.back.popspot.global.exception.BusinessException;
import com.back.popspot.global.exception.ErrorCode;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationRedisService {

	private final RedisTemplate<String, Long> redisTemplate;

	// Redis 잔여 정원 재구축이 도는 동안 신규 예약(DECR)을 잠시 막는 게이트.
	// 반드시 인메모리(JVM)여야 한다 — 이 게이트는 Redis 장애를 복구하는 동안 신규 쓰기를 막는 용도라,
	// 플래그를 Redis에 두면 Redis가 죽는 순간 게이트도 같이 죽어 무의미해진다. (대기열 recovering 필드와 동일)
	// CB가 CLOSED로 전이해도 복구가 성공해 setRecovering(false)가 불릴 때까지는 계속 막는다.
	private final AtomicBoolean recovering = new AtomicBoolean(false);

	public boolean isRecovering() {
		return recovering.get();
	}

	public void setRecovering(boolean recovering) {
		this.recovering.set(recovering);
	}

	// 예약 생성 시 정원 선차감
	@CircuitBreaker(name = "redisReservation", fallbackMethod = "decrementFallback")
	public Long decrement(String key) {
		return redisTemplate.opsForValue().decrement(key);
	}

	public Long decrementFallback(String key, Exception e) {
		log.error("Redis 장애 감지 - 예약 차단: key={}", key, e);
		throw new BusinessException(ErrorCode.RESERVATION_TEMPORARILY_UNAVAILABLE);
	}

	// 취소/만료 시 정원 복구
	@CircuitBreaker(name = "redisReservation", fallbackMethod = "incrementFallback")
	public Long increment(String key) {
		return redisTemplate.opsForValue().increment(key);
	}

	public Long incrementFallback(String key, Exception e) {
		// INCR 실패는 과소판매로 남기고 재구축에서 복구
		log.error("Redis INCR 실패 - 과소판매 상태로 남김: key={}", key, e);
		return null;
	}
}
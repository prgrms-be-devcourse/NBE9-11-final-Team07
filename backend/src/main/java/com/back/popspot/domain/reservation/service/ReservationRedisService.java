package com.back.popspot.domain.reservation.service;

import java.util.Collections;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
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
	private final DefaultRedisScript<Long> decrementIfAvailableScript;

	// 예약 생성 시 정원 선차감
	@CircuitBreaker(name = "redisReservation", fallbackMethod = "decrementFallback")
	public Long decrement(String key) {
		return redisTemplate.opsForValue().decrement(key);
	}

	// 예약 생성 시 정원 확인+차감을 원자적 Lua 스크립트 1회 호출로 처리 (Lua 스파이크 버전)
	@CircuitBreaker(name = "redisReservation", fallbackMethod = "decrementFallback")
	public Long decrementIfAvailable(String key) {
		return redisTemplate.execute(decrementIfAvailableScript, Collections.singletonList(key));
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
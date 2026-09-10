package com.back.popspot.global.queue.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.back.popspot.domain.queue.entity.PopupQueueEntry;
import com.back.popspot.domain.queue.entity.QueueEntryStatus;
import com.back.popspot.domain.queue.repository.PopupQueueEntryRepository;
import com.back.popspot.global.exception.BusinessException;
import com.back.popspot.global.exception.ErrorCode;
import com.back.popspot.global.queue.config.WaitingQueueProperties;
import com.back.popspot.global.queue.exception.QueueCircuitOpenException;
import com.back.popspot.global.redis.RedisKeys;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class WaitingQueueRedisService {

	private static final String CB_NAME = "waitingQueueRedis";

	/**
	 * ZADD(NX) + ZCARD + (조건부) EXPIREAT 를 단일 원자 Lua 스크립트로 실행한다.
	 *
	 * KEYS[1] = waiting ZSET  (waiting:popup:{id})
	 * KEYS[2] = seq counter   (seq:popup:{id})
	 * ARGV[1] = seq score, ARGV[2] = userId(ZSET member), ARGV[3] = expireAt(Unix epoch seconds)
	 *
	 * ZSET에 첫 번째 멤버가 추가된 경우(added==1 && ZCARD==1)에만 두 키 모두 EXPIREAT를 적용한다.
	 * ZADD 와 ZCARD 가 원자 단위로 묶여 있으므로, 동시 enqueue 시 TTL 이 누락되는
	 * race condition(ZADD ↔ ZCARD 사이 윈도우)이 제거된다.
	 */
	private static final RedisScript<Long> ENQUEUE_WITH_TTL_SCRIPT = RedisScript.of(
		"""
		local added = redis.call('ZADD', KEYS[1], 'NX', ARGV[1], ARGV[2])
		if added == 1 and redis.call('ZCARD', KEYS[1]) == 1 then
		    redis.call('EXPIREAT', KEYS[1], tonumber(ARGV[3]))
		    redis.call('EXPIREAT', KEYS[2], tonumber(ARGV[3]))
		    return 1
		end
		return 0
		""",
		Long.class
	);

	/**
	 * SMEMBERS → 각 id에 대해 EXISTS({prefix}{id}) 확인 → 없으면 SREM을
	 * 단일 원자 Lua 스크립트로 실행한다.
	 *
	 * N개 개별 Lua 호출 대신 1회 배치 호출을 선택한 이유:
	 * 개별 호출은 O(N) 네트워크 왕복이 필요하지만, 배치 호출은 1회 왕복으로
	 * 모든 id를 처리한다. 활성 팝업 수가 수십 건 내외인 이 서비스에서 Lua
	 * 블로킹 시간 증가보다 RTT 절약 효과가 크다.
	 *
	 * KEYS[1] = active:waiting:popups
	 * ARGV[1] = waiting:popup: (RedisKeys.popupWaitingQueuePrefix() — 하드코딩 방지)
	 */
	@SuppressWarnings("rawtypes")
	private static final RedisScript<List> FILTER_ACTIVE_POPUPS_SCRIPT = RedisScript.of(
		"""
		local members = redis.call('SMEMBERS', KEYS[1])
		local prefix = ARGV[1]
		local result = {}
		for _, id in ipairs(members) do
		    if redis.call('EXISTS', prefix .. id) == 1 then
		        result[#result + 1] = id
		    else
		        redis.call('SREM', KEYS[1], id)
		    end
		end
		return result
		""",
		List.class
	);

	private final StringRedisTemplate redisTemplate;
	private final WaitingQueueProperties properties;
	private final PopupQueueEntryRepository popupQueueEntryRepository;

	// Redis 장애 복구 중 플래그 — 인메모리여야 함 (Redis 장애 시 함께 날아가면 게이트 무의미)
	// CB가 CLOSED로 전이해도 이 플래그가 true인 동안은 enqueue 게이트가 계속 막음
	private final AtomicBoolean recovering = new AtomicBoolean(false);

	public boolean isRecovering() {
		return recovering.get();
	}

	public void setRecovering(boolean recovering) {
		this.recovering.set(recovering);
	}

	@CircuitBreaker(name = CB_NAME, fallbackMethod = "enqueueFallback")
	@Transactional
	public void enqueue(long popupId, String userId, LocalDateTime reservationEndAt) {
		long userIdLong = Long.parseLong(userId);
		if (popupQueueEntryRepository.existsByUserIdAndPopupIdAndStatus(
				userIdLong, popupId, QueueEntryStatus.WAITING)) {
			return;
		}
		Long seq = redisTemplate.opsForValue().increment(RedisKeys.popupQueueSeq(popupId));
		popupQueueEntryRepository.save(PopupQueueEntry.waiting(userIdLong, popupId, seq));

		// ZADD(NX) + ZCARD + (조건부) EXPIREAT 를 원자 Lua 스크립트로 실행.
		// 첫 번째 멤버 추가 시(added==1 && ZCARD==1) reservationEndAt + buffer 시각에 두 키 모두 만료.
		// recover(WAITING=0) 이후 첫 enqueue 에서도 이 경로로 TTL 이 설정됨.
		long expireAtEpochSec = properties.computeExpireAt(reservationEndAt).getEpochSecond();
		redisTemplate.execute(
			ENQUEUE_WITH_TTL_SCRIPT,
			List.of(RedisKeys.popupWaitingQueue(popupId), RedisKeys.popupQueueSeq(popupId)),
			String.valueOf(seq), userId, String.valueOf(expireAtEpochSec)
		);
		// 활성 팝업 인덱스에 등록 (스케줄러가 KEYS 전체 탐색 없이 찾도록)
		redisTemplate.opsForSet().add(RedisKeys.activeWaitingPopups(), String.valueOf(popupId));
	}

	private void enqueueFallback(long popupId, String userId, LocalDateTime reservationEndAt, Throwable t) {
		log.warn("waitingQueueRedis CB — enqueue fast-fail: popupId={}, userId={}", popupId, userId);
		throw new QueueCircuitOpenException(t);
	}

	@CircuitBreaker(name = CB_NAME, fallbackMethod = "hasProceedPermissionFallback")
	public boolean hasProceedPermission(long popupId, String userId) {
		return Boolean.TRUE.equals(redisTemplate.hasKey(RedisKeys.popupProceedFlag(popupId, userId)));
	}

	private boolean hasProceedPermissionFallback(long popupId, String userId, Throwable t) {
		log.warn("waitingQueueRedis CB — hasProceedPermission fail-closed: popupId={}", popupId);
		throw new BusinessException(ErrorCode.QUEUE_TEMPORARILY_UNAVAILABLE);
	}

	@CircuitBreaker(name = CB_NAME, fallbackMethod = "admitBatchFallback")
	@Transactional
	public void admitBatch(long popupId, int n) {
		Set<TypedTuple<String>> tuples =
			redisTemplate.opsForZSet().popMin(RedisKeys.popupWaitingQueue(popupId), n);
		if (tuples == null || tuples.isEmpty()) {
			return;
		}
		for (TypedTuple<String> tuple : tuples) {
			String userId = tuple.getValue();
			if (userId == null) {
				continue;
			}
			// batch-size=10 고정값 기준, 개별 UPDATE 10회 (popupId+userId+seq 키 정합성 우선)
			long seq = tuple.getScore().longValue();
			int updated = popupQueueEntryRepository.admitOne(
				popupId,
				Long.parseLong(userId),
				seq,
				QueueEntryStatus.WAITING,
				QueueEntryStatus.ADMITTED
			);
			if (updated == 0) {
				log.warn("admitOne matched 0 rows — popupId={}, userId={}, seq={}", popupId, userId, seq);
			}
			redisTemplate.opsForValue().set(
				RedisKeys.popupProceedFlag(popupId, userId),
				"1",
				Duration.ofSeconds(properties.proceedTtlSeconds())
			);
		}
	}

	private void admitBatchFallback(long popupId, int n, Throwable t) {
		log.warn("waitingQueueRedis CB — admitBatch tick skipped: popupId={}", popupId);
	}

	@CircuitBreaker(name = CB_NAME, fallbackMethod = "getQueueRankFallback")
	public Long getQueueRank(long popupId, String userId) {
		return redisTemplate.opsForZSet().rank(RedisKeys.popupWaitingQueue(popupId), userId);
	}

	private Long getQueueRankFallback(long popupId, String userId, Throwable t) {
		log.warn("waitingQueueRedis CB — getQueueRank fast-fail: popupId={}", popupId);
		throw new BusinessException(ErrorCode.QUEUE_TEMPORARILY_UNAVAILABLE);
	}

	public void revokeProceedPermission(long popupId, String userId) {
		redisTemplate.delete(RedisKeys.popupProceedFlag(popupId, userId));
	}

	public Set<Long> getActivePopupIds() {
		@SuppressWarnings("unchecked")
		List<String> result = redisTemplate.execute(
			FILTER_ACTIVE_POPUPS_SCRIPT,
			List.of(RedisKeys.activeWaitingPopups()),
			RedisKeys.popupWaitingQueuePrefix()
		);
		if (result == null || result.isEmpty()) {
			return Collections.emptySet();
		}
		return result.stream()
			.map(Long::parseLong)
			.collect(Collectors.toSet());
	}
}

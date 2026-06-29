package com.back.popspot.global.queue.service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
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

	private final StringRedisTemplate redisTemplate;
	private final WaitingQueueProperties properties;
	private final PopupQueueEntryRepository popupQueueEntryRepository;

	@CircuitBreaker(name = CB_NAME, fallbackMethod = "enqueueFallback")
	@Transactional
	public void enqueue(long popupId, String userId, LocalDateTime reservationEndAt) {
		Long seq = redisTemplate.opsForValue().increment(RedisKeys.popupQueueSeq(popupId));
		popupQueueEntryRepository.save(PopupQueueEntry.waiting(Long.parseLong(userId), popupId, seq));
		Boolean added = redisTemplate.opsForZSet().addIfAbsent(RedisKeys.popupWaitingQueue(popupId), userId, seq);

		// added==true(신규 멤버) && size==1(ZSET이 방금 생성됨) → TTL 적용
		// recover(WAITING=0) 이후 첫 enqueue에서도 이 경로로 TTL이 설정됨
		boolean isFirstInZset = Boolean.TRUE.equals(added)
				&& Long.valueOf(1L).equals(redisTemplate.opsForZSet().size(RedisKeys.popupWaitingQueue(popupId)));

		if (isFirstInZset) {
			Instant expireAt = properties.computeExpireAt(reservationEndAt);
			byte[] seqKey = RedisKeys.popupQueueSeq(popupId).getBytes(StandardCharsets.UTF_8);
			byte[] waitingKey = RedisKeys.popupWaitingQueue(popupId).getBytes(StandardCharsets.UTF_8);
			redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
				connection.keyCommands().expireAt(seqKey, expireAt);
				connection.keyCommands().expireAt(waitingKey, expireAt);
				return null;
			});
		}
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
		Set<String> keys = redisTemplate.keys(RedisKeys.popupWaitingQueuePattern());
		if (keys == null || keys.isEmpty()) {
			return Collections.emptySet();
		}
		return keys.stream()
			.map(k -> k.split(":")[2])
			.map(Long::parseLong)
			.collect(Collectors.toSet());
	}

}

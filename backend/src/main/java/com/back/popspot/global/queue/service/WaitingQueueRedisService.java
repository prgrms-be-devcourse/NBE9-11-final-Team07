package com.back.popspot.global.queue.service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.back.popspot.domain.queue.entity.PopupQueueEntry;
import com.back.popspot.domain.queue.entity.QueueEntryStatus;
import com.back.popspot.domain.queue.repository.PopupQueueEntryRepository;
import com.back.popspot.global.exception.BusinessException;
import com.back.popspot.global.exception.ErrorCode;
import com.back.popspot.global.queue.config.WaitingQueueProperties;
import com.back.popspot.global.queue.exception.QueueCircuitOpenException;
import com.back.popspot.global.redis.RedisKeys;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Metrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class WaitingQueueRedisService {

	private static final String CB_NAME = "waitingQueueRedis";

	// 좀비(DB WAITING + ZSET 누락) 발생 건수. 실패를 삼키므로 이 지표가 유일한 관측 수단이다.
	private static final String ZSET_REGISTER_FAILURE_METRIC = "popspot.queue.enqueue.zset_register_failures";

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

		// ZSET 에 이미 있으면 정상 대기 중 — DB 조회 없이 종료 (폴링 요청 대부분이 이 경로)
		if (redisTemplate.opsForZSet().score(RedisKeys.popupWaitingQueue(popupId), userId) != null) {
			return;
		}

		// ZSET 에 없는데 DB 에 WAITING 행이 남아 있으면 좀비다.
		// (커밋 성공 후 ZADD 실패, 또는 admitBatch 의 popMin 이후 DB 롤백)
		// 새 seq 를 발급하면 대기열 맨 뒤로 밀리므로, 행에 저장된 seq 를 그대로 재등록한다.
		Optional<PopupQueueEntry> orphan = popupQueueEntryRepository
			.findByUserIdAndPopupIdAndStatus(userIdLong, popupId, QueueEntryStatus.WAITING);
		if (orphan.isPresent()) {
			Long orphanSeq = orphan.get().getSeq();
			log.warn("좀비 대기 행 감지 — ZSET 재등록: popupId={}, userId={}, seq={}", popupId, userId, orphanSeq);
			registerInWaitingQueue(popupId, userId, orphanSeq, reservationEndAt);
			return;
		}

		// seq 는 DB 행에 함께 저장되므로 커밋 전에 발급해야 한다.
		// 롤백되면 seq 에 구멍이 남지만, 단조증가 순번일 뿐이라 ZSET 정렬 순서에는 영향이 없다.
		Long seq = redisTemplate.opsForValue().increment(RedisKeys.popupQueueSeq(popupId));
		popupQueueEntryRepository.save(PopupQueueEntry.waiting(userIdLong, popupId, seq));

		// ZSET/인덱스 등록은 DB 커밋 이후로 미룬다.
		// 커밋 전에 넣으면 롤백 시 "ZSET 에는 있는데 DB 원장에는 없는" 유령 항목이 남고,
		// 그 유저가 admit 될 때 admitOne 이 0 row 를 업데이트한다.
		runAfterCommit(() -> {
			try {
				registerInWaitingQueue(popupId, userId, seq, reservationEndAt);
			} catch (RuntimeException e) {
				// DB 커밋은 이미 성공했으므로 이 유저는 원장상 대기 중이다.
				// 예외를 전파하면 500 을 주게 되는데, 실제로는 등록돼 있어 정보가 어긋난다.
				// ZSET 누락은 다음 폴링의 좀비 복구 경로가 기존 seq 그대로 바로잡는다.
				log.warn("enqueue ZSET 등록 실패 — 다음 폴링에서 복구: popupId={}, userId={}, seq={}",
					popupId, userId, seq, e);
				Metrics.counter(ZSET_REGISTER_FAILURE_METRIC).increment();
			}
		});
	}

	// DB 커밋이 확정된 뒤에 수행하는 Redis 등록. 트랜잭션 밖에서 호출되면 즉시 실행한다.
	private void registerInWaitingQueue(long popupId, String userId, Long seq, LocalDateTime reservationEndAt) {
		Boolean added = redisTemplate.opsForZSet().addIfAbsent(RedisKeys.popupWaitingQueue(popupId), userId, seq);
		// 활성 팝업 인덱스에 등록 (스케줄러가 KEYS 전체 탐색 없이 찾도록)
		redisTemplate.opsForSet().add(RedisKeys.activeWaitingPopups(), String.valueOf(popupId));

		// added==true(신규 멤버) && size==1(ZSET이 방금 생성됨) → TTL 적용
		// recover(WAITING=0) 이후 첫 enqueue에서도 이 경로로 TTL이 설정됨
		boolean isFirstInZset = Boolean.TRUE.equals(added)
			&& Long.valueOf(1L).equals(redisTemplate.opsForZSet().size(RedisKeys.popupWaitingQueue(popupId)));

		if (isFirstInZset) {
			Instant expireAt = properties.computeExpireAt(reservationEndAt);
			byte[] seqKey = RedisKeys.popupQueueSeq(popupId).getBytes(StandardCharsets.UTF_8);
			byte[] waitingKey = RedisKeys.popupWaitingQueue(popupId).getBytes(StandardCharsets.UTF_8);
			redisTemplate.executePipelined((RedisCallback<Object>)connection -> {
				connection.keyCommands().expireAt(seqKey, expireAt);
				connection.keyCommands().expireAt(waitingKey, expireAt);
				return null;
			});
		}
	}

	// 트랜잭션 동기화가 살아 있으면 커밋 이후로, 아니면 즉시 실행한다.
	private void runAfterCommit(Runnable action) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			action.run();
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				action.run();
			}
		});
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
		// 비파괴 읽기 — ZSET 제거는 DB 커밋 이후로 미룬다.
		// popMin 으로 즉시 꺼내면 루프 중간 예외 시 DB 는 롤백되지만 ZSET 제거는 되돌릴 수 없어
		// "DB 는 WAITING 인데 ZSET 에는 없는" 항목이 남는다.
		// 동시 실행 배제는 상위 @SchedulerLock(popup-admission-scheduler)이 보장한다.
		Set<TypedTuple<String>> tuples =
			redisTemplate.opsForZSet().rangeWithScores(RedisKeys.popupWaitingQueue(popupId), 0, n - 1);
		if (tuples == null || tuples.isEmpty()) {
			return;
		}

		List<String> admittedUserIds = new ArrayList<>();
		for (TypedTuple<String> tuple : tuples) {
			String userId = tuple.getValue();
			if (userId == null || tuple.getScore() == null) {
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
			admittedUserIds.add(userId);
		}
		if (admittedUserIds.isEmpty()) {
			return;
		}

		// 커밋 확정 후: proceed 플래그를 먼저 세우고 그 다음 ZSET 에서 제거한다.
		// 순서를 뒤집으면 ZREM 성공 + 플래그 실패 시 그 유저가 대기열 맨 뒤로 재등록된다
		// (admit 된 행은 WAITING 이 아니라 enqueue 의 좀비 복구 경로에 잡히지 않는다).
		runAfterCommit(() -> {
			for (String userId : admittedUserIds) {
				redisTemplate.opsForValue().set(
					RedisKeys.popupProceedFlag(popupId, userId),
					"1",
					Duration.ofSeconds(properties.proceedTtlSeconds())
				);
			}
			redisTemplate.opsForZSet()
				.remove(RedisKeys.popupWaitingQueue(popupId), admittedUserIds.toArray());
		});
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
		Set<String> ids = redisTemplate.opsForSet().members(RedisKeys.activeWaitingPopups());
		if (ids == null || ids.isEmpty()) {
			return Collections.emptySet();
		}
		Set<Long> active = new HashSet<>();
		for (String id : ids) {
			long popupId = Long.parseLong(id);
			if (Boolean.TRUE.equals(redisTemplate.hasKey(RedisKeys.popupWaitingQueue(popupId)))) {
				active.add(popupId);                          // ZSET 살아있음 → 활성 대기열
			} else {
				// ZSET 없음(대기열 소진/TTL 만료) → Set에서 lazy 청소
				// 주의: hasKey(없음) 확인 후 SREM 사이에 enqueue가 끼면
				// 방금 들어온 팝업을 뺄 수 있음. 다음 조회/enqueue에서 자연 복원되므로
				// 유저 누락으로 이어지진 않음. 완전 보장은 Lua 원자화(추후 과제).
				redisTemplate.opsForSet().remove(RedisKeys.activeWaitingPopups(), id);
			}
		}
		return active;

	}
}

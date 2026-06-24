package com.back.popspot.global.queue.service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Service;

import com.back.popspot.global.queue.config.WaitingQueueProperties;
import com.back.popspot.global.redis.RedisKeys;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WaitingQueueRedisService {

	private final StringRedisTemplate redisTemplate;
	private final WaitingQueueProperties properties;

	public void enqueue(long popupId, String userId) {
		Long seq = redisTemplate.opsForValue().increment(RedisKeys.popupQueueSeq(popupId));
		redisTemplate.opsForZSet().addIfAbsent(RedisKeys.popupWaitingQueue(popupId), userId, seq);
	}

	public boolean hasProceedPermission(long popupId, String userId) {
		return Boolean.TRUE.equals(redisTemplate.hasKey(RedisKeys.popupProceedFlag(popupId, userId)));
	}

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
			redisTemplate.opsForValue().set(
				RedisKeys.popupProceedFlag(popupId, userId),
				"1",
				Duration.ofSeconds(properties.proceedTtlSeconds())
			);
		}
	}

	public Long getQueueRank(long popupId, String userId) {
		return redisTemplate.opsForZSet().rank(RedisKeys.popupWaitingQueue(popupId), userId);
	}

	public void setLastSeen(long popupId, String userId) {
		redisTemplate.opsForValue().set(
			RedisKeys.popupLastSeen(popupId, userId),
			String.valueOf(System.currentTimeMillis()),
			Duration.ofSeconds(properties.lastSeenTtlSeconds())
		);
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

	/**
	 * Pipeline으로 lastSeen 키를 일괄 조회해 만료(이탈)된 멤버를 ZREM한다.
	 * RTT를 1회로 줄여 대기열이 클 때의 Redis 부하를 최소화한다.
	 * TODO: 멀티 인스턴스 전환 시 이 메서드와 admitBatch는 분산락 대상이 됨.
	 */
	public void sweepAbsentMembers(long popupId) {
		String waitingKey = RedisKeys.popupWaitingQueue(popupId);
		Set<String> members = redisTemplate.opsForZSet().range(waitingKey, 0, -1);
		if (members == null || members.isEmpty()) {
			return;
		}

		List<String> memberList = new ArrayList<>(members);

		// Pipeline: 전체 멤버의 lastSeen 키 존재 여부를 RTT 1회에 일괄 확인
		List<Object> results = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
			for (String userId : memberList) {
				byte[] key = RedisKeys.popupLastSeen(popupId, userId)
					.getBytes(StandardCharsets.UTF_8);
				connection.keyCommands().exists(key);
			}
			return null;
		});

		Set<Object> absent = new HashSet<>();
		for (int i = 0; i < memberList.size(); i++) {
			// StringRedisTemplate 파이프라인에서 EXISTS 결과는 구현에 따라 Long 또는 Boolean으로 역직렬화됨
			Object result = results.get(i);
			boolean exists = Boolean.TRUE.equals(result) || Long.valueOf(1L).equals(result);
			if (!exists) {
				absent.add(memberList.get(i));
			}
		}

		if (!absent.isEmpty()) {
			redisTemplate.opsForZSet().remove(waitingKey, absent.toArray());
		}
	}
}

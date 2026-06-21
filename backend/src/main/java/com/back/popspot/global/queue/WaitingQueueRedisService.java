package com.back.popspot.global.queue;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WaitingQueueRedisService {

	private static final String SEQ_KEY = "seq:popup:%d";
	private static final String WAITING_KEY = "waiting:popup:%d";
	private static final String PROCEED_KEY = "proceed:popup:%d:%s";

	private final StringRedisTemplate redisTemplate;
	private final WaitingQueueProperties properties;

	public void enqueue(long popupId, String userId) {
		Long seq = redisTemplate.opsForValue().increment(String.format(SEQ_KEY, popupId));
		redisTemplate.opsForZSet().addIfAbsent(String.format(WAITING_KEY, popupId), userId, seq);
	}

	public boolean hasProceedPermission(long popupId, String userId) {
		return Boolean.TRUE.equals(redisTemplate.hasKey(String.format(PROCEED_KEY, popupId, userId)));
	}

	public void admitBatch(long popupId, int n) {
		Set<TypedTuple<String>> tuples =
			redisTemplate.opsForZSet().popMin(String.format(WAITING_KEY, popupId), n);
		if (tuples == null || tuples.isEmpty()) {
			return;
		}
		for (TypedTuple<String> tuple : tuples) {
			String userId = tuple.getValue();
			if (userId == null) {
				continue;
			}
			redisTemplate.opsForValue().set(
				String.format(PROCEED_KEY, popupId, userId),
				"1",
				Duration.ofSeconds(properties.proceedTtlSeconds())
			);
		}
	}

	public Set<Long> getActivePopupIds() {
		Set<String> keys = redisTemplate.keys("waiting:popup:*");
		if (keys == null || keys.isEmpty()) {
			return Collections.emptySet();
		}
		return keys.stream()
			.map(k -> k.split(":")[2])
			.map(Long::parseLong)
			.collect(Collectors.toSet());
	}
}

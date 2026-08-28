package com.back.popspot.global.admin.service;

import java.nio.charset.StandardCharsets;

import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.back.popspot.global.redis.RedisKeys;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 부하 테스트 반복 실행을 위해 대기열 입장 허가(proceed flag)를 유저 id 범위로 한 번에 재발급한다.
 *
 * <p>예약이 성공하면 {@code revokeProceedPermission} 이 flag 를 즉시 소각하므로, 재고만 리셋하면
 * 다음 회차는 전부 {@code RESERVATION_ADMISSION_REQUIRED} 로 막힌다. 대기열을 실제로 태우는 대신
 * {@link com.back.popspot.global.queue.service.WaitingQueueRedisService#admitBatch} 와 동일한 키/값/TTL 로
 * flag 만 직접 심어 준다. 대기열 DB 원장({@code popup_queue_entry})은 건드리지 않는다.
 *
 * <p>부하 테스트 전용이므로 운영(prod) 프로파일에서는 빈 자체가 등록되지 않는다.
 */
@Slf4j
@Service
@Profile("!prod")
@RequiredArgsConstructor
public class ProceedFlagGrantService {

	// admitBatch 가 심는 값과 동일해야 한다. hasProceedPermission 은 키 존재만 보지만 값도 맞춰 둔다.
	private static final byte[] FLAG_VALUE = "1".getBytes(StandardCharsets.UTF_8);

	// 파이프라인 응답이 한 번에 메모리에 쌓이지 않도록 끊어서 보낸다.
	private static final long PIPELINE_CHUNK_SIZE = 10_000L;

	private final StringRedisTemplate stringRedisTemplate;

	public long grantRange(long popupId, long userIdFrom, long userIdTo, long ttlSeconds) {
		long granted = 0L;

		for (long chunkStart = userIdFrom; chunkStart <= userIdTo; chunkStart += PIPELINE_CHUNK_SIZE) {
			long chunkEnd = Math.min(chunkStart + PIPELINE_CHUNK_SIZE - 1, userIdTo);
			grantChunk(popupId, chunkStart, chunkEnd, ttlSeconds);
			granted += chunkEnd - chunkStart + 1;
		}

		log.info(
			"[PROCEED_FLAG_GRANTED] proceed flag 재발급: popupId={}, userIdFrom={}, userIdTo={}, granted={}, ttlSeconds={}",
			popupId,
			userIdFrom,
			userIdTo,
			granted,
			ttlSeconds
		);

		return granted;
	}

	private void grantChunk(long popupId, long chunkStart, long chunkEnd, long ttlSeconds) {
		stringRedisTemplate.executePipelined((RedisCallback<Object>)connection -> {
			for (long userId = chunkStart; userId <= chunkEnd; userId++) {
				byte[] key = RedisKeys.popupProceedFlag(popupId, Long.toString(userId))
					.getBytes(StandardCharsets.UTF_8);
				connection.stringCommands().setEx(key, ttlSeconds, FLAG_VALUE);
			}
			return null;
		});
	}
}

package com.back.popspot.global.queue;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.back.popspot.global.queue.scheduler.WaitingQueueScheduler;
import com.back.popspot.global.queue.service.WaitingQueueRedisService;
import com.back.popspot.global.redis.RedisKeys;
import com.back.popspot.support.IntegrationTestSupport;

@DisplayName("대기열 sweeper 통합 테스트")
class WaitingQueueSweeperIntegrationTest extends IntegrationTestSupport {

	private static final long TEST_POPUP_ID = 88888L;

	@MockitoBean
	private WaitingQueueScheduler scheduler;

	@Autowired
	private WaitingQueueRedisService queueService;

	@Autowired
	private StringRedisTemplate redisTemplate;

	@BeforeEach
	void setUp() {
		cleanupTestKeys();
	}

	@AfterEach
	void tearDown() {
		cleanupTestKeys();
	}

	private void cleanupTestKeys() {
		Set<String> keys = redisTemplate.keys("*popup:" + TEST_POPUP_ID + "*");
		if (keys != null && !keys.isEmpty()) {
			redisTemplate.delete(keys);
		}
	}

	// ── sweeper 검증 1: 이탈자 제거 ─────────────────────────────────────────

	@Test
	@DisplayName("sweeper검증1: lastSeen 만료(폴링 끊김) 멤버가 sweeper 실행 후 ZREM 됨")
	void sweeper_lastSeen_만료된_멤버_제거() throws InterruptedException {
		String userId = "201";
		queueService.enqueue(TEST_POPUP_ID, userId);

		// 1초 TTL로 lastSeen 직접 세팅 — 즉시 만료를 시뮬레이션
		redisTemplate.opsForValue().set(
			RedisKeys.popupLastSeen(TEST_POPUP_ID, userId),
			String.valueOf(System.currentTimeMillis()),
			Duration.ofSeconds(1)
		);

		// TTL 만료 대기
		Thread.sleep(1500);

		queueService.sweepAbsentMembers(TEST_POPUP_ID);

		assertThat(redisTemplate.opsForZSet().score(RedisKeys.popupWaitingQueue(TEST_POPUP_ID), userId))
			.as("lastSeen 만료된 멤버는 ZSET에서 제거되어야 한다")
			.isNull();
	}

	// ── sweeper 검증 2: 정상 대기자 오탐 없음 ───────────────────────────────

	@Test
	@DisplayName("sweeper검증2: lastSeen 살아있는(폴링 중인) 멤버는 sweeper가 건드리지 않음")
	void sweeper_lastSeen_살아있는_멤버_유지() {
		String userId = "202";
		queueService.enqueue(TEST_POPUP_ID, userId);

		// 충분히 긴 TTL로 lastSeen 설정 — 이탈하지 않은 상태
		redisTemplate.opsForValue().set(
			RedisKeys.popupLastSeen(TEST_POPUP_ID, userId),
			String.valueOf(System.currentTimeMillis()),
			Duration.ofSeconds(30)
		);

		queueService.sweepAbsentMembers(TEST_POPUP_ID);

		assertThat(redisTemplate.opsForZSet().score(RedisKeys.popupWaitingQueue(TEST_POPUP_ID), userId))
			.as("lastSeen이 살아있는 멤버는 ZSET에 그대로 남아야 한다")
			.isNotNull();
	}

	// ── sweeper 검증 3: 이탈자 제거 후 순번 보정 ────────────────────────────

	@Test
	@DisplayName("sweeper검증3: 이탈자(A)가 sweeper로 제거된 뒤 뒷사람(B, C)의 ZRANK가 앞으로 당겨짐")
	void sweeper_이탈자_제거_후_순번_보정() throws InterruptedException {
		String userA = "301"; // 이탈자 — 1초 TTL
		String userB = "302"; // 정상 대기자
		String userC = "303"; // 정상 대기자

		queueService.enqueue(TEST_POPUP_ID, userA);
		queueService.enqueue(TEST_POPUP_ID, userB);
		queueService.enqueue(TEST_POPUP_ID, userC);

		redisTemplate.opsForValue().set(
			RedisKeys.popupLastSeen(TEST_POPUP_ID, userA),
			String.valueOf(System.currentTimeMillis()),
			Duration.ofSeconds(1)
		);
		redisTemplate.opsForValue().set(
			RedisKeys.popupLastSeen(TEST_POPUP_ID, userB),
			String.valueOf(System.currentTimeMillis()),
			Duration.ofSeconds(30)
		);
		redisTemplate.opsForValue().set(
			RedisKeys.popupLastSeen(TEST_POPUP_ID, userC),
			String.valueOf(System.currentTimeMillis()),
			Duration.ofSeconds(30)
		);

		// sweeper 전: A=rank0, B=rank1, C=rank2 (0-indexed)
		assertThat(redisTemplate.opsForZSet().rank(RedisKeys.popupWaitingQueue(TEST_POPUP_ID), userA)).isEqualTo(0L);
		assertThat(redisTemplate.opsForZSet().rank(RedisKeys.popupWaitingQueue(TEST_POPUP_ID), userB)).isEqualTo(1L);
		assertThat(redisTemplate.opsForZSet().rank(RedisKeys.popupWaitingQueue(TEST_POPUP_ID), userC)).isEqualTo(2L);

		// A의 lastSeen 만료 대기
		Thread.sleep(1500);
		queueService.sweepAbsentMembers(TEST_POPUP_ID);

		// A 제거 확인
		assertThat(redisTemplate.opsForZSet().score(RedisKeys.popupWaitingQueue(TEST_POPUP_ID), userA))
			.as("이탈자 A는 ZSET에서 제거되어야 한다")
			.isNull();

		// B, C 순번이 앞으로 당겨짐: B=rank0, C=rank1
		assertThat(redisTemplate.opsForZSet().rank(RedisKeys.popupWaitingQueue(TEST_POPUP_ID), userB))
			.as("B의 ZRANK는 A 제거 후 0이어야 한다")
			.isEqualTo(0L);
		assertThat(redisTemplate.opsForZSet().rank(RedisKeys.popupWaitingQueue(TEST_POPUP_ID), userC))
			.as("C의 ZRANK는 A 제거 후 1이어야 한다")
			.isEqualTo(1L);
	}
}

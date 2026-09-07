package com.back.popspot.domain.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import com.back.popspot.global.redis.RedisKeys;
import com.back.popspot.support.IntegrationTestSupport;

@DisplayName("ReservationRedisService - Lua 스크립트 decrementIfAvailable 동시성 테스트")
class ReservationRedisServiceLuaConcurrencyTest extends IntegrationTestSupport {

	private static final Long TEST_SLOT_ID = 999999L;
	private static final String REMAINING_KEY = RedisKeys.reservationSlotRemaining(TEST_SLOT_ID);

	@Autowired
	private ReservationRedisService reservationRedisService;

	@Autowired
	private RedisTemplate<String, Long> redisTemplate;

	@BeforeEach
	void setUp() {
		redisTemplate.delete(REMAINING_KEY);
	}

	@AfterEach
	void tearDown() {
		redisTemplate.delete(REMAINING_KEY);
	}

	@Test
	@DisplayName("remaining=1일 때 100개 스레드가 동시에 호출해도 정확히 1개만 성공하고 정원을 초과 판매하지 않는다")
	void decrementIfAvailable_동시요청_정원초과판매없음() throws InterruptedException {
		redisTemplate.opsForValue().set(REMAINING_KEY, 1L);

		int threadCount = 100;
		ExecutorService executor = Executors.newFixedThreadPool(threadCount);
		CountDownLatch latch = new CountDownLatch(threadCount);
		List<Long> results = new CopyOnWriteArrayList<>();

		for (int i = 0; i < threadCount; i++) {
			executor.submit(() -> {
				try {
					results.add(reservationRedisService.decrementIfAvailable(REMAINING_KEY));
				} finally {
					latch.countDown();
				}
			});
		}
		latch.await();
		executor.shutdown();

		assertThat(results).hasSize(threadCount);
		assertThat(results.stream().filter(r -> r == 0L).count()).isEqualTo(1);
		assertThat(results.stream().filter(r -> r == -1L).count()).isEqualTo(threadCount - 1);
		assertThat(redisTemplate.opsForValue().get(REMAINING_KEY)).isEqualTo(0L);
	}
}

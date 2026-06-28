package com.back.popspot.global.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import com.back.popspot.domain.popupStore.dto.PopupStoreCreateRequest;
import com.back.popspot.domain.popupStore.entity.PopupFeeType;
import com.back.popspot.domain.popupStore.entity.PopupStore;
import com.back.popspot.domain.popupStore.repository.PopupStoreRepository;
import com.back.popspot.domain.user.entity.User;
import com.back.popspot.domain.user.repository.UserRepository;
import com.back.popspot.global.queue.config.WaitingQueueProperties;
import com.back.popspot.global.queue.scheduler.WaitingQueueScheduler;
import com.back.popspot.global.queue.service.WaitingQueueRedisService;
import com.back.popspot.global.redis.RedisKeys;
import com.back.popspot.support.IntegrationTestSupport;

@DisplayName("대기열 엔진 통합 테스트")
class WaitingQueueIntegrationTest extends IntegrationTestSupport {

	private static final long TEST_POPUP_ID = 99999L;
	private static final LocalDateTime TEST_RESERVATION_END_AT = LocalDateTime.of(2099, 12, 31, 23, 59);

	@Autowired
	private WaitingQueueRedisService queueService;

	@Autowired
	private WaitingQueueScheduler scheduler;

	@Autowired
	private WaitingQueueProperties properties;

	@Autowired
	private StringRedisTemplate redisTemplate;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PopupStoreRepository popupStoreRepository;

	@BeforeEach
	void setUp() {
		cleanupTestKeys();
	}

	@AfterEach
	void tearDown() {
		cleanupTestKeys();
	}

	private void cleanupTestKeys() {
		for (String pattern : List.of("*popup:" + TEST_POPUP_ID + "*", "waiting:popup:*", "seq:popup:*")) {
			Set<String> keys = redisTemplate.keys(pattern);
			if (keys != null && !keys.isEmpty()) {
				redisTemplate.delete(keys);
			}
		}
	}

	// ── 검증 1: FIFO 순서 보장 ─────────────────────────────────────────────

	@Test
	@DisplayName("검증1: 동시 진입 시 INCR 순번 충돌 없이 FIFO 순서 유지")
	void 동시진입_순번_충돌없이_FIFO_유지() throws InterruptedException {
		int threadCount = 10;
		ExecutorService executor = Executors.newFixedThreadPool(threadCount);
		CountDownLatch latch = new CountDownLatch(threadCount);

		for (long i = 1; i <= threadCount; i++) {
			final String userId = String.valueOf(i);
			executor.submit(() -> {
				try {
					queueService.enqueue(TEST_POPUP_ID, userId, TEST_RESERVATION_END_AT);
				} finally {
					latch.countDown();
				}
			});
		}
		latch.await();
		executor.shutdown();

		String waitingKey = RedisKeys.popupWaitingQueue(TEST_POPUP_ID);
		assertThat(redisTemplate.opsForZSet().size(waitingKey)).isEqualTo(threadCount);

		Set<ZSetOperations.TypedTuple<String>> tuples =
			redisTemplate.opsForZSet().rangeWithScores(waitingKey, 0, -1);
		long distinctScoreCount = tuples.stream()
			.map(ZSetOperations.TypedTuple::getScore)
			.map(Double::longValue)
			.distinct()
			.count();
		assertThat(distinctScoreCount).isEqualTo(threadCount);
	}

	// ── 검증 2: 고정 비율 ─────────────────────────────────────────────────

	@Test
	@DisplayName("검증2: 스케줄러 1회 실행 시 정확히 batchSize명만 popMin")
	void 스케줄러_1회에_정확히_N명_popMin() {
		int batchSize = properties.batchSize();
		int total = batchSize + 5;

		for (long i = 1; i <= total; i++) {
			queueService.enqueue(TEST_POPUP_ID, String.valueOf(i), TEST_RESERVATION_END_AT);
		}

		assertThat(redisTemplate.opsForZSet().size(RedisKeys.popupWaitingQueue(TEST_POPUP_ID))).isEqualTo(total);

		scheduler.admitWaiting();

		assertThat(redisTemplate.opsForZSet().size(RedisKeys.popupWaitingQueue(TEST_POPUP_ID))).isEqualTo(5);
		assertThat(redisTemplate.keys(RedisKeys.popupProceedFlagPattern(TEST_POPUP_ID))).hasSize(batchSize);
	}

	// ── 검증 3: 중복 진입 차단 (NX) ───────────────────────────────────────

	@Test
	@DisplayName("검증3: 같은 userId 두 번 enqueue해도 ZSET에 1건, 순번 불변")
	void 동일_userId_중복_등록_차단() {
		String userId = "42";
		queueService.enqueue(TEST_POPUP_ID, userId, TEST_RESERVATION_END_AT);
		Double scoreAfterFirst = redisTemplate.opsForZSet()
			.score(RedisKeys.popupWaitingQueue(TEST_POPUP_ID), userId);

		queueService.enqueue(TEST_POPUP_ID, userId, TEST_RESERVATION_END_AT);

		assertThat(redisTemplate.opsForZSet().size(RedisKeys.popupWaitingQueue(TEST_POPUP_ID))).isEqualTo(1);
		assertThat(redisTemplate.opsForZSet().score(RedisKeys.popupWaitingQueue(TEST_POPUP_ID), userId))
			.isEqualTo(scoreAfterFirst);
	}

	// ── 검증 4: 게이트 3분기 ──────────────────────────────────────────────

	@Test
	@DisplayName("검증4-a: 비회원 요청 → 큐 등록 없이 200 + 상세 데이터")
	void 게이트_비회원_큐스킵_상세반환() throws Exception {
		PopupStore popup = persistPopup(persistUser());

		// 인증 없이 요청 → 인터셉터가 큐 스킵 후 통과
		mockMvc.perform(get("/popups/" + popup.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("SUCCESS"));

		assertThat(redisTemplate.opsForZSet().size(RedisKeys.popupWaitingQueue(popup.getId()))).isZero();
	}

	@Test
	@DisplayName("검증4-b: 회원 + 미허가 → 202 + ZSET에 userId 등록")
	void 게이트_회원_미허가_202() throws Exception {
		long userId = 77L;
		PopupStore popup = persistPopup(persistUser());

		mockMvc.perform(get("/popups/" + popup.getId())
				.with(authentication(auth(userId))))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.code").value("WAITING"));

		assertThat(redisTemplate.opsForZSet()
			.score(RedisKeys.popupWaitingQueue(popup.getId()), String.valueOf(userId))).isNotNull();
	}

	@Test
	@DisplayName("검증4-c: 회원 + 허가(proceed 키 존재) → 200 + 상세 데이터")
	void 게이트_회원_허가_통과() throws Exception {
		User user = persistUser();
		PopupStore popup = persistPopup(user);
		redisTemplate.opsForValue().set(
			RedisKeys.popupProceedFlag(popup.getId(), String.valueOf(user.getId())), "1");

		mockMvc.perform(get("/popups/" + popup.getId())
				.with(authentication(auth(user.getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("SUCCESS"));
	}

	// ── 검증 5: TTL 회수 ─────────────────────────────────────────────────

	@Test
	@DisplayName("검증5: 허가 키가 TTL 만료 후 자동 삭제")
	void 허가키_TTL_만료_후_삭제() throws InterruptedException {
		String userId = "99";
		queueService.enqueue(TEST_POPUP_ID, userId, TEST_RESERVATION_END_AT);
		queueService.admitBatch(TEST_POPUP_ID, 1);

		String proceedKey = RedisKeys.popupProceedFlag(TEST_POPUP_ID, userId);
		assertThat(redisTemplate.hasKey(proceedKey)).isTrue();

		// test 프로파일: proceed-ttl-seconds=1
		Thread.sleep(1500L);

		assertThat(redisTemplate.hasKey(proceedKey)).isFalse();
	}

	// ── 폴링 검증 1: 순번 정확도 ──────────────────────────────────────────

	@Test
	@DisplayName("폴링검증1: 진입 순서대로 ZRANK 기반 순번 1·2·3 부여")
	void 순번_진입순서와_일치() throws Exception {
		long userId1 = 10L, userId2 = 20L, userId3 = 30L;
		queueService.enqueue(TEST_POPUP_ID, String.valueOf(userId1), TEST_RESERVATION_END_AT);
		queueService.enqueue(TEST_POPUP_ID, String.valueOf(userId2), TEST_RESERVATION_END_AT);
		queueService.enqueue(TEST_POPUP_ID, String.valueOf(userId3), TEST_RESERVATION_END_AT);

		mockMvc.perform(get("/popups/" + TEST_POPUP_ID + "/waiting-status")
				.with(authentication(auth(userId1))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("WAITING"))
			.andExpect(jsonPath("$.data.rank").value(1));

		mockMvc.perform(get("/popups/" + TEST_POPUP_ID + "/waiting-status")
				.with(authentication(auth(userId2))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.rank").value(2));

		mockMvc.perform(get("/popups/" + TEST_POPUP_ID + "/waiting-status")
				.with(authentication(auth(userId3))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.rank").value(3));
	}

	// ── 폴링 검증 2: 3-상태 분기 ─────────────────────────────────────────

	@Test
	@DisplayName("폴링검증2: (a) proceed 있음 → ADMITTED, (b) ZSET에만 있음 → WAITING+순번, (c) 둘 다 없음 → NOT_IN_QUEUE")
	void 상태_3분기() throws Exception {
		// (a) ADMITTED
		long admittedId = 11L;
		redisTemplate.opsForValue().set(RedisKeys.popupProceedFlag(TEST_POPUP_ID, String.valueOf(admittedId)), "1");

		mockMvc.perform(get("/popups/" + TEST_POPUP_ID + "/waiting-status")
				.with(authentication(auth(admittedId))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("ADMITTED"));

		// (b) WAITING + 순번
		long waitingId = 22L;
		queueService.enqueue(TEST_POPUP_ID, String.valueOf(waitingId), TEST_RESERVATION_END_AT);

		mockMvc.perform(get("/popups/" + TEST_POPUP_ID + "/waiting-status")
				.with(authentication(auth(waitingId))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("WAITING"))
			.andExpect(jsonPath("$.data.rank").isNumber())
			.andExpect(jsonPath("$.data.estimatedSeconds").isNumber())
			.andExpect(jsonPath("$.data.pollIntervalSeconds").value(properties.pollIntervalSeconds()));

		// (c) NOT_IN_QUEUE
		long unknownId = 33L;

		mockMvc.perform(get("/popups/" + TEST_POPUP_ID + "/waiting-status")
				.with(authentication(auth(unknownId))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("NOT_IN_QUEUE"));
	}

	// ── 헬퍼 ─────────────────────────────────────────────────────────────

	private UsernamePasswordAuthenticationToken auth(long userId) {
		return new UsernamePasswordAuthenticationToken(userId, null, List.of());
	}

	private User persistUser() {
		return userRepository.save(User.create("queue-test@example.com", "테스터"));
	}

	private PopupStore persistPopup(User user) {
		LocalDateTime now = LocalDateTime.now();
		PopupStoreCreateRequest request = new PopupStoreCreateRequest(
			"테스트 팝업",
			"서울",
			PopupFeeType.FREE,
			null,
			now.minusDays(1),
			now.plusDays(10),
			now.plusDays(1),
			now.plusDays(20),
			null,
			null
		);
		return popupStoreRepository.save(PopupStore.of(user, request));
	}
}

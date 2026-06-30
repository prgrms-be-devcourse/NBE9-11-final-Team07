package com.back.popspot.global.queue;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.back.popspot.domain.popupStore.dto.PopupStoreCreateRequest;
import com.back.popspot.domain.popupStore.entity.PopupFeeType;
import com.back.popspot.domain.popupStore.entity.PopupStore;
import com.back.popspot.domain.popupStore.repository.PopupStoreRepository;
import com.back.popspot.domain.queue.entity.PopupQueueEntry;
import com.back.popspot.domain.queue.repository.PopupQueueEntryRepository;
import com.back.popspot.domain.user.entity.User;
import com.back.popspot.domain.user.repository.UserRepository;
import com.back.popspot.global.queue.config.QueueRecoveryProperties;
import com.back.popspot.global.queue.config.SchedulerLockConfig;
import com.back.popspot.global.queue.config.WaitingQueueProperties;
import com.back.popspot.global.queue.interceptor.WaitingQueueInterceptor;
import com.back.popspot.global.queue.service.QueueRecoveryCoordinator;
import com.back.popspot.global.queue.service.QueueRecoveryService;
import com.back.popspot.global.queue.service.WaitingQueueRedisService;
import com.back.popspot.global.redis.RedisKeys;

import jakarta.servlet.http.HttpServletResponse;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import tools.jackson.databind.ObjectMapper;

/**
 * 대기열 멀티 인스턴스 Recovery 분산 락 테스트.
 *
 * <p>동일 JVM 내에서 {@link WaitingQueueRedisService}·{@link QueueRecoveryCoordinator} 인스턴스를
 * 각각 두 벌 수동 생성해 멀티 인스턴스 시나리오를 재현한다.
 * 분산 락({@link LockingTaskExecutor})과 {@link QueueRecoveryService}는 공유,
 * {@code recovering} AtomicBoolean은 인스턴스별 독립.
 *
 * <p>Toxiproxy 없이 MySQL + Redis 컨테이너만 사용.
 *
 * <p>{@code @Transactional}을 클래스 레벨에 붙이지 않는 이유: 복구 스레드(queue-recovery)가
 * 별도 트랜잭션으로 DB를 읽으므로, 테스트 트랜잭션이 커밋되지 않으면 복구 스레드가 데이터를 볼 수 없다.
 */
@SpringBootTest(
    classes = QueueMultiInstanceRecoveryTest.TestConfig.class,
    properties = {
        "waiting-queue.batch-size=3",
        "waiting-queue.scheduler-fixed-rate-ms=3600000",
        "waiting-queue.proceed-ttl-seconds=60",
        "waiting-queue.poll-interval-seconds=1",
        "waiting-queue.queue-ttl-buffer-seconds=300",
        "queue-recovery.max-attempts=20",
        "queue-recovery.poll-interval-seconds=0",
        "queue-recovery.lock-at-most-for-seconds=3",
        "queue-recovery.retry-deadline-seconds=10",
    }
)
@Testcontainers
@ActiveProfiles("test")
@DisplayName("대기열 멀티 인스턴스 Recovery 분산 락 테스트")
class QueueMultiInstanceRecoveryTest {

    // ── 최소 Spring 컨텍스트 ──────────────────────────────────────────────────
    // WaitingQueueRedisService·QueueRecoveryCoordinator는 수동 2벌 생성 → 컨텍스트 미등록
    @Configuration
    @Import({
        QueueRecoveryService.class,
        SchedulerLockConfig.class
    })
    @ImportAutoConfiguration({
        AopAutoConfiguration.class,
        DataRedisAutoConfiguration.class,
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        DataJpaRepositoriesAutoConfiguration.class,
        TransactionAutoConfiguration.class
    })
    @EnableConfigurationProperties({WaitingQueueProperties.class, QueueRecoveryProperties.class})
    @EntityScan(basePackages = "com.back.popspot")
    @EnableJpaRepositories(basePackages = "com.back.popspot")
    static class TestConfig {}

    // ── 컨테이너 ──────────────────────────────────────────────────────────────

    @Container
    static final MySQLContainer<?> mysql =
        new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("popspot_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static final GenericContainer<?> redis =
        new GenericContainer<>(DockerImageName.parse("redis:7.2"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    // ── Autowired (공유 자원) ─────────────────────────────────────────────────

    @Autowired StringRedisTemplate redisTemplate;
    @Autowired QueueRecoveryService recoveryService;    // 공유 + spy 베이스
    @Autowired LockingTaskExecutor lockingTaskExecutor; // 공유 — 같은 Redis ShedLock
    @Autowired WaitingQueueProperties queueProperties;
    @Autowired QueueRecoveryProperties recoveryProperties;
    @Autowired PopupQueueEntryRepository queueEntryRepository;
    @Autowired PopupStoreRepository popupStoreRepository;
    @Autowired UserRepository userRepository;

    // ── 인스턴스별 독립 Bean (recovering AtomicBoolean 분리) ──────────────────

    WaitingQueueRedisService serviceA;
    WaitingQueueRedisService serviceB;

    @BeforeEach
    void setUp() {
        serviceA = new WaitingQueueRedisService(redisTemplate, queueProperties, queueEntryRepository);
        serviceB = new WaitingQueueRedisService(redisTemplate, queueProperties, queueEntryRepository);
        serviceA.setRecovering(false);
        serviceB.setRecovering(false);
        cleanupRedis();
        cleanupDb();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        cleanupRedis();
        cleanupDb();
    }

    // ── TC-MULTI-1 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-MULTI-1: 두 인스턴스 동시 executeAndLowerGate — 분산 락 경쟁, 최종 정합성 보장")
    void tcMulti1_concurrentRecovery_bothLowerGate_zsetConsistent() throws Exception {
        PopupStore popup = createOpenPopup("multi1");
        long popupId = popup.getId();
        queueEntryRepository.save(PopupQueueEntry.waiting(10L, popupId, 1L));
        queueEntryRepository.save(PopupQueueEntry.waiting(20L, popupId, 2L));
        queueEntryRepository.save(PopupQueueEntry.waiting(30L, popupId, 3L));

        serviceA.setRecovering(true);
        serviceB.setRecovering(true);

        QueueRecoveryCoordinator coordA = newCoordinator(recoveryService, serviceA, recoveryProperties);
        QueueRecoveryCoordinator coordB = newCoordinator(recoveryService, serviceB, recoveryProperties);

        CompletableFuture<Void> futA = CompletableFuture.runAsync(coordA::executeAndLowerGate);
        CompletableFuture<Void> futB = CompletableFuture.runAsync(coordB::executeAndLowerGate);
        futA.get(15, SECONDS);
        futB.get(15, SECONDS);

        assertThat(serviceA.isRecovering()).as("인스턴스 A recovering 해제").isFalse();
        assertThat(serviceB.isRecovering()).as("인스턴스 B recovering 해제").isFalse();

        String zsetKey = RedisKeys.popupWaitingQueue(popupId);
        Set<ZSetOperations.TypedTuple<String>> tuples =
            redisTemplate.opsForZSet().rangeWithScores(zsetKey, 0, -1);

        assertThat(tuples).as("ZSET에 WAITING 3건 존재").hasSize(3);
        assertThat(tuples)
            .extracting(ZSetOperations.TypedTuple::getValue)
            .as("ZSET 멤버 = WAITING userId(10, 20, 30), ADMITTED 제외")
            .containsExactlyInAnyOrder("10", "20", "30");
        assertThat(tuples)
            .extracting(t -> t.getScore().longValue())
            .as("ZSET score = DB seq(1, 2, 3) 1:1 매핑")
            .containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    // ── TC-MULTI-2 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-MULTI-2: rebuild 겹침 + DEL 윈도우 — A DEL 후 ZPOPMIN 0건 반환 명시적 증명")
    void tcMulti2_delWindow_zpopminReturnsEmpty() throws Exception {
        PopupStore popup = createOpenPopup("multi2");
        long popupId = popup.getId();
        queueEntryRepository.save(PopupQueueEntry.waiting(10L, popupId, 1L));
        queueEntryRepository.save(PopupQueueEntry.waiting(20L, popupId, 2L));
        queueEntryRepository.save(PopupQueueEntry.waiting(30L, popupId, 3L));

        serviceA.setRecovering(true);
        serviceB.setRecovering(true);

        String zsetKey = RedisKeys.popupWaitingQueue(popupId);
        CountDownLatch aDelDone = new CountDownLatch(1);
        CountDownLatch proceedToZadd = new CountDownLatch(1);

        QueueRecoveryService spyService = spy(recoveryService);
        doAnswer(inv -> {
            redisTemplate.delete(zsetKey);  // Step 1: DEL만 수동 실행 (ZADD 전)
            aDelDone.countDown();           // Step 2: DEL 완료 신호
            proceedToZadd.await();          // Step 3: main thread 허가 대기
            return inv.callRealMethod();    // Step 4: 실제 recoverAll (DEL+ZADD 완전 실행)
        }).when(spyService).recoverAll();

        // coordB: A가 락을 얼마나 오래 잡을지 예측 불가 → maxAttempts/deadline을 넉넉히 설정
        QueueRecoveryProperties tc2Props = new QueueRecoveryProperties(500, 0, 3, 60);
        QueueRecoveryCoordinator coordA = newCoordinator(spyService, serviceA, tc2Props);
        QueueRecoveryCoordinator coordB = newCoordinator(recoveryService, serviceB, tc2Props);

        // Thread-A 시작 — spy doAnswer 내부 aDelDone까지 진행
        CompletableFuture<Void> futA = CompletableFuture.runAsync(coordA::executeAndLowerGate);
        assertThat(aDelDone.await(10, SECONDS)).as("A DEL 완료 신호 대기").isTrue();

        // DEL 윈도우 검증: ZSET 키가 삭제된 상태 → ZPOPMIN 결과 0건
        Set<ZSetOperations.TypedTuple<String>> emptyTuples =
            redisTemplate.opsForZSet().popMin(zsetKey, 10);
        assertThat(emptyTuples)
            .as("A가 DEL만 실행한 윈도우에서 ZPOPMIN 결과가 0건이어야 한다")
            .isEmpty();

        // Thread-B 시작 — A가 락 보유 중이므로 획득 실패 후 폴링
        CompletableFuture<Void> futB = CompletableFuture.runAsync(coordB::executeAndLowerGate);

        proceedToZadd.countDown();  // A 진행 허가 (callRealMethod → DEL+ZADD 완전 실행)

        futA.get(15, SECONDS);
        futB.get(15, SECONDS);

        // 멱등 rebuild 2회 후에도 3건 유지
        Set<ZSetOperations.TypedTuple<String>> finalTuples =
            redisTemplate.opsForZSet().rangeWithScores(zsetKey, 0, -1);
        assertThat(finalTuples).as("최종 ZSET에 WAITING 3건 존재").hasSize(3);
        assertThat(finalTuples)
            .extracting(ZSetOperations.TypedTuple::getValue)
            .containsExactlyInAnyOrder("10", "20", "30");
        assertThat(finalTuples)
            .extracting(t -> t.getScore().longValue())
            .containsExactlyInAnyOrder(1L, 2L, 3L);
        assertThat(serviceA.isRecovering()).as("A recovering 해제").isFalse();
        assertThat(serviceB.isRecovering()).as("B recovering 해제").isFalse();
    }

    // ── TC-MULTI-3 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-MULTI-3: 락 holder 지연(sleep 3s > lockAtMostFor 2s) → 락 만료 후 B 선취, A 후속 완료")
    void tcMulti3_lockHolderDelayed_lockExpires_bRecovesFirst() throws Exception {
        PopupStore popup = createOpenPopup("multi3");
        long popupId = popup.getId();
        queueEntryRepository.save(PopupQueueEntry.waiting(10L, popupId, 1L));
        queueEntryRepository.save(PopupQueueEntry.waiting(20L, popupId, 2L));

        serviceA.setRecovering(true);
        serviceB.setRecovering(true);

        // lockAtMostForSeconds=2: A가 락 점유 중 2s 초과 → ShedLock이 만료, B가 재취득 가능
        // pollIntervalSeconds=1: B가 1s 간격 폴링 → 20회×1s=20s 예산, 락 만료(2s) 전 소진 방지
        QueueRecoveryProperties tc3Props = new QueueRecoveryProperties(20, 1, 2, 10);

        CountDownLatch aGotLock = new CountDownLatch(1);

        QueueRecoveryService spyForA = spy(recoveryService);
        doAnswer(inv -> {
            aGotLock.countDown();        // A가 락 획득 + recoverAll() 진입 신호
            Thread.sleep(3_000);         // 락 TTL(2s) 초과 지연 → ShedLock 강제 만료
            return inv.callRealMethod();
        }).when(spyForA).recoverAll();

        QueueRecoveryCoordinator coordA = new QueueRecoveryCoordinator(
            lockingTaskExecutor, spyForA, serviceA, tc3Props);
        QueueRecoveryCoordinator coordB = new QueueRecoveryCoordinator(
            lockingTaskExecutor, recoveryService, serviceB, tc3Props);

        long startMs = System.currentTimeMillis();

        // A 먼저 시작 — 락 획득 확인 후 B 시작해서 "A가 락 보유 중" 시나리오 보장
        CompletableFuture<Void> futA = CompletableFuture.runAsync(coordA::executeAndLowerGate);
        assertThat(aGotLock.await(5, SECONDS)).as("A 락 획득 확인").isTrue();
        CompletableFuture<Void> futB = CompletableFuture.runAsync(coordB::executeAndLowerGate);

        // B: 폴링 중 → t≈2s 락 만료 → B 락 획득 → rebuild → B.recovering=false
        await()
            .atMost(8, SECONDS)
            .pollInterval(50, MILLISECONDS)
            .untilAsserted(() -> assertThat(serviceB.isRecovering()).isFalse());

        long bDoneMs = System.currentTimeMillis() - startMs;
        // B 완료 시점에 A는 여전히 sleep 중 → recovering=true
        assertThat(serviceA.isRecovering())
            .as("B 완료 시점(경과 %dms)에 A는 아직 recovering=true", bDoneMs)
            .isTrue();

        // t≈3s: A sleep 해제 → A rebuild 완료 → A.recovering=false
        futA.get(8, SECONDS);
        futB.get(8, SECONDS);

        assertThat(serviceA.isRecovering()).as("A도 최종적으로 recovering=false").isFalse();
        assertThat(System.currentTimeMillis() - startMs)
            .as("두 스레드 모두 7s 이내 종료 (sleep 3s + 오버헤드)")
            .isLessThan(7_000L);

        // 두 번 rebuild 후 멱등성: ZSET ↔ DB 정합
        Set<ZSetOperations.TypedTuple<String>> tuples =
            redisTemplate.opsForZSet().rangeWithScores(RedisKeys.popupWaitingQueue(popupId), 0, -1);
        assertThat(tuples).as("ZSET에 WAITING 2건 존재 (멱등)").hasSize(2);
        assertThat(tuples)
            .extracting(ZSetOperations.TypedTuple::getValue)
            .containsExactlyInAnyOrder("10", "20");
        assertThat(tuples)
            .extracting(t -> t.getScore().longValue())
            .containsExactlyInAnyOrder(1L, 2L);
    }

    // ── TC-MULTI-4 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-MULTI-4: 게이트 해제 시점 차이 — 중간 구간 인터셉터 동작 명시 검증")
    void tcMulti4_gateTimingDifference_interceptorBehaviorAtMidpoint() throws Exception {
        PopupStore popup = createOpenPopup("multi4");
        long popupId = popup.getId();
        queueEntryRepository.save(PopupQueueEntry.waiting(10L, popupId, 1L));
        queueEntryRepository.save(PopupQueueEntry.waiting(20L, popupId, 2L));

        serviceA.setRecovering(true);
        serviceB.setRecovering(true);

        CountDownLatch bCanStart = new CountDownLatch(1);

        QueueRecoveryCoordinator coordA = newCoordinator(recoveryService, serviceA, recoveryProperties);
        // spyCoordB: executeAndLowerGate() 전체를 bCanStart까지 홀드
        QueueRecoveryCoordinator realCoordB = newCoordinator(recoveryService, serviceB, recoveryProperties);
        QueueRecoveryCoordinator spyCoordB = spy(realCoordB);
        doAnswer(inv -> {
            bCanStart.await();           // main thread가 중간 검증 마칠 때까지 대기
            return inv.callRealMethod();
        }).when(spyCoordB).executeAndLowerGate();

        CompletableFuture<Void> futA = CompletableFuture.runAsync(coordA::executeAndLowerGate);
        CompletableFuture<Void> futB = CompletableFuture.runAsync(spyCoordB::executeAndLowerGate);

        futA.get(10, SECONDS);  // A 복구 완료 대기

        // ── 중간 검증: A 완료, B 아직 시작 안 한 시점 ─────────────────────────
        assertThat(serviceA.isRecovering()).as("A는 recovering=false").isFalse();
        assertThat(serviceB.isRecovering()).as("B는 아직 recovering=true").isTrue();

        // SecurityContext: principal = Long → resolveAuthenticatedUserId() 반환값
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(42L, null, List.of())
        );

        // 인터셉터는 Spring Bean이 아닌 원칙으로 수동 생성
        // hasProceedPermission·enqueue는 CB 프록시 없이 Redis 직접 호출 (이 TC에서는 무방)
        WaitingQueueInterceptor interceptorA =
            new WaitingQueueInterceptor(serviceA, new ObjectMapper(), popupStoreRepository);
        WaitingQueueInterceptor interceptorB =
            new WaitingQueueInterceptor(serviceB, new ObjectMapper(), popupStoreRepository);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/popups/" + popupId);

        // interceptorA: recovering=false → 정상 경로 (503 아님, 대기열 enqueue 후 202)
        MockHttpServletResponse resA = new MockHttpServletResponse();
        interceptorA.preHandle(req, resA, new Object());
        assertThat(resA.getStatus())
            .as("인터셉터 A: recovering=false → 503 아님")
            .isNotEqualTo(HttpServletResponse.SC_SERVICE_UNAVAILABLE);

        // interceptorB: recovering=true → 503 + Retry-After: 10
        MockHttpServletResponse resB = new MockHttpServletResponse();
        interceptorB.preHandle(req, resB, new Object());
        assertThat(resB.getStatus())
            .as("인터셉터 B: recovering=true → 503 반환")
            .isEqualTo(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        assertThat(resB.getHeader("Retry-After"))
            .as("503 응답에 Retry-After: 10 헤더 포함")
            .isEqualTo("10");

        // B 시작 허가 → 복구 완료 대기
        bCanStart.countDown();
        futB.get(10, SECONDS);

        // ── 최종 검증: B도 recovering=false, 인터셉터 B 정상 응답 ───────────────
        assertThat(serviceB.isRecovering()).as("B도 최종적으로 recovering=false").isFalse();

        MockHttpServletResponse resBFinal = new MockHttpServletResponse();
        interceptorB.preHandle(req, resBFinal, new Object());
        assertThat(resBFinal.getStatus())
            .as("B recovering=false 후 인터셉터 B → 503 아님")
            .isNotEqualTo(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────

    private PopupStore createOpenPopup(String name) {
        LocalDateTime now = LocalDateTime.now();
        User organizer = userRepository.save(User.create("org-" + name + "@test.com", name));
        return popupStoreRepository.save(PopupStore.of(organizer, new PopupStoreCreateRequest(
            name, "서울", PopupFeeType.FREE, null,
            now.minusDays(1), now.plusDays(10),
            now.plusDays(1), now.plusDays(20),
            "test-image-key", null
        )));
    }

    private QueueRecoveryCoordinator newCoordinator(
        QueueRecoveryService svc, WaitingQueueRedisService redis, QueueRecoveryProperties props
    ) {
        return new QueueRecoveryCoordinator(lockingTaskExecutor, svc, redis, props);
    }

    private void cleanupRedis() {
        for (String pattern : List.of("waiting:popup:*", "seq:popup:*", "proceed:popup:*", "job-lock:*")) {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        }
    }

    private void cleanupDb() {
        queueEntryRepository.deleteAll();
        popupStoreRepository.deleteAll();
        userRepository.deleteAll();
    }
}

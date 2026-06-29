package com.back.popspot.global.queue;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.ToxiproxyContainer;
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
import com.back.popspot.global.exception.BusinessException;
import com.back.popspot.global.queue.config.QueueCircuitBreakerEventConfig;
import com.back.popspot.global.queue.config.QueueRecoveryProperties;
import com.back.popspot.global.queue.config.SchedulerLockConfig;
import com.back.popspot.global.queue.config.WaitingQueueProperties;
import com.back.popspot.global.queue.service.QueueRecoveryCoordinator;
import com.back.popspot.global.queue.service.QueueRecoveryService;
import com.back.popspot.global.queue.service.WaitingQueueRedisService;
import com.back.popspot.global.redis.RedisKeys;

import eu.rekawek.toxiproxy.model.ToxicDirection;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;

/**
 * 대기열 서킷브레이커 + 장애 복구 E2E 통합 테스트.
 *
 * <p>Redis 장애 주입(Toxiproxy) → CB OPEN → Redis 복원 → CB CLOSED → rebuild → 정합성 검증까지
 * 전체 파이프라인을 커버한다.
 * Security/Web/S3/OAuth2를 배제한 최소 컨텍스트로 기동하며,
 * {@link WaitingQueueCircuitBreakerTest}와 동일한 {@code classes = TestConfig.class} 방식을 사용한다.
 *
 * <p>컨테이너 구성: Redis ← Toxiproxy(장애 주입) ← App / MySQL은 직접 연결(장애 주입 대상 아님).
 *
 * <p>{@code @Transactional}을 클래스 레벨에 붙이지 않는 이유: 복구 스레드(queue-recovery)가
 * 별도 트랜잭션으로 DB를 읽으므로, 테스트 트랜잭션이 커밋되지 않으면 복구 스레드가 데이터를 볼 수 없다.
 * 대신 setUp/tearDown에서 직접 deleteAll()로 정리한다.
 */
@SpringBootTest(
    classes = QueueFaultRecoveryIntegrationTest.TestConfig.class,
    properties = {
        // CB: COUNT_BASED 소 윈도우 — 3회 연속 실패(100% > 60% 임계) 시 OPEN
        "resilience4j.circuitbreaker.instances.waitingQueueRedis.sliding-window-type=COUNT_BASED",
        "resilience4j.circuitbreaker.instances.waitingQueueRedis.sliding-window-size=3",
        "resilience4j.circuitbreaker.instances.waitingQueueRedis.minimum-number-of-calls=3",
        "resilience4j.circuitbreaker.instances.waitingQueueRedis.failure-rate-threshold=60",
        // OPEN → HALF_OPEN 전환 대기 시간 단축 (TC-2: 2s 후 probe 허용)
        "resilience4j.circuitbreaker.instances.waitingQueueRedis.wait-duration-in-open-state=2s",
        "resilience4j.circuitbreaker.instances.waitingQueueRedis.permitted-number-of-calls-in-half-open-state=1",
        "resilience4j.circuitbreaker.instances.waitingQueueRedis.record-exceptions[0]"
            + "=org.springframework.data.redis.RedisConnectionFailureException",
        "resilience4j.circuitbreaker.instances.waitingQueueRedis.record-exceptions[1]"
            + "=org.springframework.dao.QueryTimeoutException",
        "resilience4j.circuitbreaker.instances.waitingQueueRedis.ignore-exceptions[0]"
            + "=com.back.popspot.global.queue.exception.QueueFullException",
        // Redis 커맨드 타임아웃 1s: TC-4A에서 2s latency > 1s timeout → QueryTimeoutException
        "spring.data.redis.lettuce.shutdown-timeout=0ms",
        "spring.data.redis.timeout=1s",
        "spring.data.redis.connect-timeout=1s",
        // WaitingQueueProperties: 스케줄러 주기를 1시간으로 늘려 테스트 중 자동 실행 방지
        "waiting-queue.batch-size=3",
        "waiting-queue.scheduler-fixed-rate-ms=3600000",
        "waiting-queue.proceed-ttl-seconds=1",
        "waiting-queue.poll-interval-seconds=1",
        "waiting-queue.queue-ttl-buffer-seconds=300",
        // QueueRecoveryProperties: 빠른 재시도, 락 TTL 단축
        "queue-recovery.max-attempts=10",
        "queue-recovery.poll-interval-seconds=0",
        "queue-recovery.lock-at-most-for-seconds=5",
        "queue-recovery.retry-deadline-seconds=30",
    }
)
@Testcontainers
@ActiveProfiles("test")
@DisplayName("대기열 장애 복구 E2E 통합 테스트")
class QueueFaultRecoveryIntegrationTest {

    // ── 최소 Spring 컨텍스트 ──────────────────────────────────────────────────
    // Security/Web/S3/OAuth2 없이 AOP + CB + Redis + JPA + ShedLock만 기동
    @Configuration
    @Import({
        WaitingQueueRedisService.class,
        QueueCircuitBreakerEventConfig.class,
        QueueRecoveryCoordinator.class,
        QueueRecoveryService.class,
        SchedulerLockConfig.class
    })
    @ImportAutoConfiguration({
        AopAutoConfiguration.class,
        CircuitBreakerAutoConfiguration.class,
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

    static final Network NETWORK = Network.newNetwork();

    @Container
    static final MySQLContainer<?> mysql =
        new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("popspot_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static final ToxiproxyContainer toxiproxy =
        new ToxiproxyContainer(DockerImageName.parse("ghcr.io/shopify/toxiproxy:2.9.0"))
            .withNetwork(NETWORK);

    @Container
    static final GenericContainer<?> redis =
        new GenericContainer<>(DockerImageName.parse("redis:7.2"))
            .withNetwork(NETWORK)
            .withNetworkAliases("redis")
            .withExposedPorts(6379);

    static ToxiproxyContainer.ContainerProxy redisProxy;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) throws IOException {
        redisProxy = toxiproxy.getProxy("redis", 6379);
        // MySQL: application-test.yml의 datasource 설정을 Testcontainers 값으로 덮어씀
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        // Redis: App → Toxiproxy → Redis (장애 주입 경로)
        registry.add("spring.data.redis.host", toxiproxy::getHost);
        registry.add("spring.data.redis.port", () -> redisProxy.getProxyPort());
    }

    // ── Autowired ─────────────────────────────────────────────────────────────

    @Autowired
    WaitingQueueRedisService queueService;

    @Autowired
    CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    StringRedisTemplate redisTemplate;

    @Autowired
    PopupQueueEntryRepository queueEntryRepository;

    @Autowired
    PopupStoreRepository popupStoreRepository;

    @Autowired
    UserRepository userRepository;

    // ── 상수 ─────────────────────────────────────────────────────────────────

    static final int CALLS_TO_TRIP = 3;
    static final long PROBE_POPUP_ID = 77777L;
    static final LocalDateTime FAR_FUTURE = LocalDateTime.of(2099, 12, 31, 23, 59);

    CircuitBreaker cb;

    // ── 공통 픽스처 ───────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() throws IOException {
        cb = circuitBreakerRegistry.circuitBreaker("waitingQueueRedis");
        cb.reset();
        queueService.setRecovering(false);
        redisProxy.setConnectionCut(false);
        cleanupRedis();
        cleanupDb();
    }

    @AfterEach
    void tearDown() throws IOException {
        redisProxy.setConnectionCut(false);
        cleanupRedis();
        cleanupDb();
    }

    private void cleanupRedis() {
        for (String pattern : List.of("waiting:popup:*", "seq:popup:*", "proceed:popup:*")) {
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

    /**
     * 커넥션이 차단된 상태에서 CALLS_TO_TRIP 회 enqueue를 시도해 CB를 OPEN 상태로 전환한다.
     * 각 호출은 fallback을 거쳐 예외를 던지므로 try-catch로 삼킨다.
     */
    private void tripCb(long popupId) {
        for (int i = 0; i < CALLS_TO_TRIP; i++) {
            try {
                queueService.enqueue(popupId, String.valueOf(1000 + i), FAR_FUTURE);
            } catch (Exception ignored) {}
        }
    }

    // ── TC-1 ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-1: Toxiproxy 차단 → 3회 실패 → CB OPEN 전이, recovering 플래그 즉시 활성화")
    void tc1_connectionCut_cbOpens_recoveringActivated() throws IOException {
        redisProxy.setConnectionCut(true);

        tripCb(PROBE_POPUP_ID);

        assertThat(cb.getState())
            .as("3회 연속 Redis 실패 후 CB가 OPEN으로 전환되어야 한다")
            .isEqualTo(CircuitBreaker.State.OPEN);
        // QueueCircuitBreakerEventConfig.onStateTransition → OPEN 분기에서 setRecovering(true) 동기 호출
        assertThat(queueService.isRecovering())
            .as("CB OPEN 전이 시 recovering 플래그가 동기적으로 true로 활성화되어야 한다")
            .isTrue();
    }

    // ── TC-2 ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-2: 연결 복원 → wait-duration(2s) 경과 → probe 성공 → CB CLOSED, recovering=false")
    void tc2_connectionRestored_cbClosesAfterWaitDuration_recoveringLowered() throws IOException {
        redisProxy.setConnectionCut(true);
        tripCb(PROBE_POPUP_ID);
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(queueService.isRecovering()).isTrue();

        redisProxy.setConnectionCut(false);

        // wait-duration(2s) 경과 후 다음 호출에서 HALF_OPEN probe 실행 → 성공 → CLOSED.
        // OPEN 중 short-circuit으로 BusinessException이 던져질 수 있으므로 catch 후 재시도.
        await()
            .atMost(15, SECONDS)
            .pollInterval(300, MILLISECONDS)
            .untilAsserted(() -> {
                try {
                    queueService.getQueueRank(PROBE_POPUP_ID, "probe-user");
                } catch (Exception ignored) {}
                assertThat(cb.getState())
                    .as("wait-duration 경과 후 probe 성공 시 CB가 CLOSED로 전환되어야 한다")
                    .isEqualTo(CircuitBreaker.State.CLOSED);
            });

        // CLOSED 전이 → QueueCircuitBreakerEventConfig가 비동기로 복구 작업 제출.
        // 열린 팝업 스토어 없음 → recoverAll() no-op, recovering=false로 해제.
        await()
            .atMost(10, SECONDS)
            .pollInterval(200, MILLISECONDS)
            .untilAsserted(() ->
                assertThat(queueService.isRecovering())
                    .as("복구 완료 후 recovering 플래그가 false로 해제되어야 한다")
                    .isFalse()
            );
    }

    // ── TC-3 ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-3: rebuild 후 ZSET 멤버·score·TTL이 DB WAITING 행과 1:1 정합, ADMITTED 행은 제외")
    void tc3_afterRebuild_zsetIntegrityMatchesDbWaitingRows() throws IOException {
        // ── 픽스처: 예약 진행 중인 팝업 + WAITING 3건 + ADMITTED 1건 ──────────
        User organizer = userRepository.save(User.create("org@test.com", "주최자"));
        LocalDateTime now = LocalDateTime.now();
        PopupStore popup = popupStoreRepository.save(PopupStore.of(organizer,
            new PopupStoreCreateRequest(
                "복구 테스트 팝업", "서울", PopupFeeType.FREE, null,
                now.minusDays(1), now.plusDays(10),     // reservationStart ~ End
                now.plusDays(1), now.plusDays(20),       // openDate ~ closeDate
                "test-image-key", null
            )
        ));
        long popupId = popup.getId();
        LocalDateTime reservationEndAt = popup.getReservationEndAt();

        // WAITING 3건 — userId(10,20,30) / seq(1,2,3) 쌍으로 ZSET 1:1 매핑 검증용
        queueEntryRepository.save(PopupQueueEntry.waiting(10L, popupId, 1L));
        queueEntryRepository.save(PopupQueueEntry.waiting(20L, popupId, 2L));
        queueEntryRepository.save(PopupQueueEntry.waiting(30L, popupId, 3L));
        // ADMITTED 1건 — rebuild 시 반드시 ZSET에서 제외되어야 함
        PopupQueueEntry admittedEntry = PopupQueueEntry.waiting(40L, popupId, 4L);
        admittedEntry.admit();
        queueEntryRepository.save(admittedEntry);

        // ── CB 트립 → 복구 파이프라인 실행 ─────────────────────────────────
        redisProxy.setConnectionCut(true);
        for (int i = 0; i < CALLS_TO_TRIP; i++) {
            try {
                queueService.enqueue(popupId, "999", reservationEndAt);
            } catch (Exception ignored) {}
        }
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        redisProxy.setConnectionCut(false);

        // wait-duration(2s) 경과 후 probe → CLOSED
        await()
            .atMost(15, SECONDS)
            .pollInterval(300, MILLISECONDS)
            .untilAsserted(() -> {
                try {
                    queueService.getQueueRank(popupId, "probe");
                } catch (Exception ignored) {}
                assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
            });

        // 비동기 복구 완료 대기
        await()
            .atMost(10, SECONDS)
            .pollInterval(200, MILLISECONDS)
            .untilAsserted(() -> assertThat(queueService.isRecovering()).isFalse());

        // ── ZSET 정합성 검증 ─────────────────────────────────────────────────
        String zsetKey = RedisKeys.popupWaitingQueue(popupId);
        String seqKey = RedisKeys.popupQueueSeq(popupId);

        Set<ZSetOperations.TypedTuple<String>> tuples =
            redisTemplate.opsForZSet().rangeWithScores(zsetKey, 0, -1);

        assertThat(tuples)
            .as("WAITING 3건만 ZSET에 존재해야 한다 (ADMITTED 1건 제외)")
            .hasSize(3);
        assertThat(tuples)
            .extracting(ZSetOperations.TypedTuple::getValue)
            .as("ZSET 멤버가 DB WAITING userId(10, 20, 30)와 1:1 매핑되어야 한다")
            .containsExactlyInAnyOrder("10", "20", "30");
        assertThat(tuples)
            .extracting(t -> t.getScore().longValue())
            .as("ZSET score가 DB seq(1, 2, 3)와 1:1 매핑되어야 한다")
            .containsExactlyInAnyOrder(1L, 2L, 3L);

        // seq 키 = MAX(seq) across ALL statuses (WAITING + ADMITTED 포함 → 4)
        assertThat(redisTemplate.opsForValue().get(seqKey))
            .as("seq 키 값이 DB MAX(seq)=4(ADMITTED 포함 전체)와 일치해야 한다")
            .isEqualTo("4");

        // 두 키 모두 절대 TTL(EXPIREAT) 적용 확인
        Long zsetTtl = redisTemplate.getExpire(zsetKey, TimeUnit.SECONDS);
        Long seqTtl = redisTemplate.getExpire(seqKey, TimeUnit.SECONDS);
        assertThat(zsetTtl)
            .as("ZSET 키에 절대 TTL이 설정되어야 한다")
            .isGreaterThan(0L);
        assertThat(seqTtl)
            .as("seq 키에 절대 TTL이 설정되어야 한다")
            .isGreaterThan(0L);
    }

    // ── TC-4A ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-4A: latency toxic(2s) > timeout(1s) → QueryTimeoutException 기록 → 3회 후 CB OPEN")
    void tc4a_latencyExceedsRedisTimeout_queriesFailAndCbTrips() throws IOException {
        // DOWNSTREAM: Redis → App 방향 2000ms 지연 → Lettuce 1s timeout 초과 → QueryTimeoutException
        redisProxy.toxics().latency("latency-2s", ToxicDirection.DOWNSTREAM, 2000);
        try {
            for (int i = 0; i < CALLS_TO_TRIP; i++) {
                final String userId = "user-" + i;
                // fallback: hasProceedPermissionFallback → BusinessException(QUEUE_TEMPORARILY_UNAVAILABLE)
                assertThatThrownBy(() -> queueService.hasProceedPermission(PROBE_POPUP_ID, userId))
                    .as("latency > timeout → QueryTimeoutException이 CB에 기록되고 fallback이 BusinessException을 던진다")
                    .isInstanceOf(BusinessException.class);
            }
            assertThat(cb.getState())
                .as("3회 QueryTimeoutException 기록 후 CB가 OPEN으로 전환되어야 한다")
                .isEqualTo(CircuitBreaker.State.OPEN);
        } finally {
            redisProxy.toxics().get("latency-2s").remove();
        }
    }

    // ── TC-4B ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-4B: latency toxic(300ms) < timeout(1s) → 느리지만 성공, CB CLOSED 유지")
    void tc4b_latencyBelowRedisTimeout_callsSucceedAndCbStaysClosed() throws IOException {
        // 300ms 지연은 1s timeout 이내이므로 Lettuce가 정상 응답을 받음 → CB는 성공으로 기록
        redisProxy.toxics().latency("latency-300ms", ToxicDirection.DOWNSTREAM, 300);
        try {
            for (int i = 0; i < CALLS_TO_TRIP + 5; i++) {
                // proceed 키 없음 → false 반환, 예외 없음
                boolean result = queueService.hasProceedPermission(PROBE_POPUP_ID, "user-x");
                assertThat(result)
                    .as("proceed 키가 없으면 false 반환, 예외 없음")
                    .isFalse();
            }
            assertThat(cb.getState())
                .as("300ms latency < 1s timeout → 모든 호출이 성공하므로 CB는 CLOSED를 유지해야 한다")
                .isEqualTo(CircuitBreaker.State.CLOSED);
        } finally {
            redisProxy.toxics().get("latency-300ms").remove();
        }
    }
}

package com.back.popspot.global.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.back.popspot.domain.queue.repository.PopupQueueEntryRepository;
import com.back.popspot.global.exception.BusinessException;
import com.back.popspot.global.exception.ErrorCode;
import com.back.popspot.global.queue.config.WaitingQueueProperties;
import com.back.popspot.global.queue.exception.QueueCircuitOpenException;
import com.back.popspot.global.queue.service.WaitingQueueRedisService;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;

/**
 * {@link WaitingQueueRedisService} 4개 CB 지점 Toxiproxy 통합 테스트.
 *
 * <p>Security/JPA를 배제한 최소 컨텍스트(AOP + CB + 실 Redis)로 기동하고,
 * Toxiproxy로 Redis 커넥션을 끊어 CB OPEN 후 각 fallback 동작을 검증한다.
 * minimum-number-of-calls=3 / failure-rate-threshold=60% → 3 연속 실패 시 OPEN.
 */
@SpringBootTest(
    classes = WaitingQueueCircuitBreakerTest.TestConfig.class,
    properties = {
        // CB 설정: 빠른 트립용 재정의 (COUNT_BASED, 소 윈도우)
        "resilience4j.circuitbreaker.instances.waitingQueueRedis.sliding-window-type=COUNT_BASED",
        "resilience4j.circuitbreaker.instances.waitingQueueRedis.sliding-window-size=3",
        "resilience4j.circuitbreaker.instances.waitingQueueRedis.minimum-number-of-calls=3",
        "resilience4j.circuitbreaker.instances.waitingQueueRedis.failure-rate-threshold=60",
        "resilience4j.circuitbreaker.instances.waitingQueueRedis.wait-duration-in-open-state=60s",
        "resilience4j.circuitbreaker.instances.waitingQueueRedis.record-exceptions[0]=org.springframework.data.redis.RedisConnectionFailureException",
        // 1s 타임아웃 설정 시 QueryTimeoutException이 발생하므로 함께 기록 대상에 포함
        "resilience4j.circuitbreaker.instances.waitingQueueRedis.record-exceptions[1]=org.springframework.dao.QueryTimeoutException",
        "resilience4j.circuitbreaker.instances.waitingQueueRedis.ignore-exceptions[0]=com.back.popspot.global.queue.exception.QueueFullException",
        // WaitingQueueProperties 바인딩 최솟값
        "waiting-queue.batch-size=3",
        "waiting-queue.scheduler-fixed-rate-ms=3600000",
        "waiting-queue.proceed-ttl-seconds=1",
        "waiting-queue.poll-interval-seconds=1",
        "waiting-queue.queue-ttl-buffer-seconds=5",
        // Toxiproxy 연결 차단 중 컨텍스트 종료 시 Lettuce blocking 방지
        "spring.data.redis.lettuce.shutdown-timeout=0ms",
        // 커맨드 타임아웃 단축: Toxiproxy 차단 후 즉시(1s) 실패해야 테스트가 빠름
        "spring.data.redis.timeout=1s",
        "spring.data.redis.connect-timeout=1s"
    }
)
@Testcontainers
@DisplayName("대기열 서킷브레이커 Toxiproxy 통합 테스트")
class WaitingQueueCircuitBreakerTest {

    // ── 최소 Spring 컨텍스트 ──────────────────────────────────────────────────
    // Security/JPA 없이 AOP + Resilience4j CB + 실 Redis만 기동
    @Configuration
    @Import(WaitingQueueRedisService.class)
    @ImportAutoConfiguration({AopAutoConfiguration.class, CircuitBreakerAutoConfiguration.class, DataRedisAutoConfiguration.class})
    @EnableConfigurationProperties(WaitingQueueProperties.class)
    static class TestConfig {}

    // ── Toxiproxy + Redis 컨테이너 ────────────────────────────────────────────
    static final Network NETWORK = Network.newNetwork();

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
        registry.add("spring.data.redis.host", toxiproxy::getHost);
        registry.add("spring.data.redis.port", () -> redisProxy.getProxyPort());
    }

    // PopupQueueEntryRepository: Redis 장애 시 도달 전에 예외 발생하므로 모킹
    @MockitoBean
    PopupQueueEntryRepository popupQueueEntryRepository;

    @Autowired
    WaitingQueueRedisService queueService;

    @Autowired
    CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    StringRedisTemplate redisTemplate;

    private static final long POPUP_ID = 88888L;
    private static final String USER_ID = "1";
    private static final LocalDateTime EXPIRY = LocalDateTime.of(2099, 12, 31, 23, 59);
    // minimum-number-of-calls=3, failure-rate=60% → 3 연속 실패 후 OPEN
    private static final int CALLS_TO_TRIP = 3;

    @BeforeEach
    void resetState() throws IOException {
        circuitBreakerRegistry.circuitBreaker("waitingQueueRedis").reset();
        redisProxy.setConnectionCut(false);
    }

    @AfterEach
    void cleanUp() throws IOException {
        redisProxy.setConnectionCut(false);
        Set<String> keys = redisTemplate.keys("*" + POPUP_ID + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    // ── 지점 1: enqueue (INCR + ZADD) ─────────────────────────────────────

    @Test
    @DisplayName("enqueue: CB OPEN 시 QueueCircuitOpenException (Redis 호출 없이 즉시 차단)")
    void enqueue_cbOpen_throwsQueueCircuitOpenException() throws IOException {
        redisProxy.setConnectionCut(true);

        // 3회 실패 → CB OPEN (매 실패마다 fallback이 QueueCircuitOpenException 던짐)
        for (int i = 0; i < CALLS_TO_TRIP; i++) {
            assertThatThrownBy(() -> queueService.enqueue(POPUP_ID, USER_ID, EXPIRY))
                .isInstanceOf(QueueCircuitOpenException.class);
        }
        assertThat(circuitBreakerRegistry.circuitBreaker("waitingQueueRedis").getState())
            .isEqualTo(CircuitBreaker.State.OPEN);

        // Redis 복구 후에도 CB OPEN → fallback이 Redis 접근 없이 즉시 차단
        redisProxy.setConnectionCut(false);
        assertThatThrownBy(() -> queueService.enqueue(POPUP_ID, USER_ID, EXPIRY))
            .isInstanceOf(QueueCircuitOpenException.class);
        assertThat(circuitBreakerRegistry.circuitBreaker("waitingQueueRedis").getState())
            .isEqualTo(CircuitBreaker.State.OPEN);
    }

    // ── 지점 2: hasProceedPermission (EXISTS) ──────────────────────────────

    @Test
    @DisplayName("hasProceedPermission: CB OPEN 시 fail-closed → BusinessException(QUEUE_TEMPORARILY_UNAVAILABLE)")
    void hasProceedPermission_cbOpen_failClosed_throwsBusinessException() throws IOException {
        redisProxy.setConnectionCut(true);

        for (int i = 0; i < CALLS_TO_TRIP; i++) {
            assertThatThrownBy(() -> queueService.hasProceedPermission(POPUP_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.QUEUE_TEMPORARILY_UNAVAILABLE);
        }
        assertThat(circuitBreakerRegistry.circuitBreaker("waitingQueueRedis").getState())
            .isEqualTo(CircuitBreaker.State.OPEN);

        // Redis 복구 후에도 CB OPEN → 차단 유지
        redisProxy.setConnectionCut(false);
        assertThatThrownBy(() -> queueService.hasProceedPermission(POPUP_ID, USER_ID))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ErrorCode.QUEUE_TEMPORARILY_UNAVAILABLE);
    }

    // ── 지점 3: getQueueRank (ZRANK) ──────────────────────────────────────

    @Test
    @DisplayName("getQueueRank: CB OPEN 시 fast-fail → BusinessException(QUEUE_TEMPORARILY_UNAVAILABLE)")
    void getQueueRank_cbOpen_fastFail_throwsBusinessException() throws IOException {
        redisProxy.setConnectionCut(true);

        for (int i = 0; i < CALLS_TO_TRIP; i++) {
            assertThatThrownBy(() -> queueService.getQueueRank(POPUP_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.QUEUE_TEMPORARILY_UNAVAILABLE);
        }
        assertThat(circuitBreakerRegistry.circuitBreaker("waitingQueueRedis").getState())
            .isEqualTo(CircuitBreaker.State.OPEN);

        redisProxy.setConnectionCut(false);
        assertThatThrownBy(() -> queueService.getQueueRank(POPUP_ID, USER_ID))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ErrorCode.QUEUE_TEMPORARILY_UNAVAILABLE);
    }

    // ── 지점 4: admitBatch (ZPOPMIN) ──────────────────────────────────────

    @Test
    @DisplayName("admitBatch: CB OPEN 시 해당 틱 스킵 (예외 없음, CB 상태 OPEN 유지)")
    void admitBatch_cbOpen_skipsTick_noException() throws IOException {
        redisProxy.setConnectionCut(true);

        // 3회 실패 → CB OPEN (fallback은 void 반환이므로 예외 없음)
        for (int i = 0; i < CALLS_TO_TRIP; i++) {
            assertThatCode(() -> queueService.admitBatch(POPUP_ID, 10))
                .doesNotThrowAnyException();
        }
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("waitingQueueRedis");
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // CB OPEN 상태에서 admitBatch → fallback(skip+log) → 예외 없음
        assertThatCode(() -> queueService.admitBatch(POPUP_ID, 10))
            .doesNotThrowAnyException();

        // CB 상태 여전히 OPEN (Redis 호출 없었음 → HALF-OPEN 전환 안 됨)
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }
}

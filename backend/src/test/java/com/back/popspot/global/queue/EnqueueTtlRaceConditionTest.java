package com.back.popspot.global.queue;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
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
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.back.popspot.domain.queue.repository.PopupQueueEntryRepository;
import com.back.popspot.global.queue.config.WaitingQueueProperties;
import com.back.popspot.global.queue.service.WaitingQueueRedisService;
import com.back.popspot.global.redis.RedisKeys;

/**
 * enqueue()의 ZADD(NX) ↔ ZCARD 경쟁 조건 재현 및 Lua 원자화 검증 테스트.
 *
 * <p>버그: 두 유저가 동시에 enqueue() 를 호출하면 둘 다 ZADD NX 성공(added=true)을 받지만,
 * ZCARD 확인 시점에는 이미 둘 다 ZSET 에 존재해 size==2 가 됨. 결과로 isFirstInZset==false 가
 * 되어 ZSET 과 seq 키에 TTL 이 영구적으로 설정되지 않음.
 * 한 번 이 타이밍을 놓치면 ZSET 이 완전히 소진돼 재생성되기 전까지 self-healing 경로가 없음.
 *
 * <p>Lua 원자화: ZADD(NX) + ZCARD + (조건부) EXPIREAT 를 단일 원자 스크립트로 묶어
 * 위 윈도우를 제거함.
 *
 * <p>알려진 한계 (이번 변경 범위 밖):
 * - DB @Transactional ↔ Redis 명령 간 정합성: DB 롤백 시 Redis 에 반영된 상태가 남는 기존 이슈
 * - 중복 enqueue 시 seq gap: dedup_key unique constraint 로 실제 데이터 중복은 방어되나,
 *   INCR 은 이미 호출된 뒤 ZADD NX 가 실패할 경우 seq 카운터에 gap 이 생길 수 있음
 */
@SpringBootTest(
    classes = EnqueueTtlRaceConditionTest.TestConfig.class,
    properties = {
        "waiting-queue.batch-size=3",
        "waiting-queue.scheduler-fixed-rate-ms=3600000",
        "waiting-queue.proceed-ttl-seconds=60",
        "waiting-queue.poll-interval-seconds=1",
        "waiting-queue.queue-ttl-buffer-seconds=300",
    }
)
@Testcontainers
@ActiveProfiles("test")
@DisplayName("enqueue TTL 경쟁 조건 재현 및 Lua 원자화 검증 테스트")
class EnqueueTtlRaceConditionTest {

    @Configuration
    @ImportAutoConfiguration({
        AopAutoConfiguration.class,
        DataRedisAutoConfiguration.class,
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        DataJpaRepositoriesAutoConfiguration.class,
        TransactionAutoConfiguration.class
    })
    @EnableConfigurationProperties(WaitingQueueProperties.class)
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

    // ── Autowired ─────────────────────────────────────────────────────────────

    @Autowired StringRedisTemplate redisTemplate;
    @Autowired WaitingQueueProperties queueProperties;
    @Autowired PopupQueueEntryRepository queueEntryRepository;

    WaitingQueueRedisService service;

    @BeforeEach
    void setUp() {
        service = new WaitingQueueRedisService(redisTemplate, queueProperties, queueEntryRepository);
        cleanup();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    // ── TC-TTL-1 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-TTL-1: 구버전 비원자 구현 — ZADD↔ZCARD 윈도우에서 동시 진입 시 TTL 미설정 (버그 재현)")
    void nonAtomicImpl_raceBetweenZaddAndZcard_ttlNotSet() throws Exception {
        long popupId = 501L;
        LocalDateTime endAt = LocalDateTime.now().plusDays(1);

        // 두 스레드가 각자 ZADD 를 마친 뒤 서로를 기다렸다가 ZCARD 를 확인하도록 동기화.
        // → 두 스레드 모두 ZCARD==2 를 보게 되어 isFirst==false → TTL 미설정.
        CountDownLatch aZaddDone = new CountDownLatch(1);
        CountDownLatch bZaddDone = new CountDownLatch(1);

        ExecutorService exec = Executors.newFixedThreadPool(2);

        exec.submit(() -> {
            try {
                brokenEnqueue(popupId, "user_a", endAt, aZaddDone, bZaddDone);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        exec.submit(() -> {
            try {
                brokenEnqueue(popupId, "user_b", endAt, bZaddDone, aZaddDone);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        exec.shutdown();
        exec.awaitTermination(10, SECONDS);

        assertThat(redisTemplate.opsForZSet().size(RedisKeys.popupWaitingQueue(popupId)))
            .as("두 유저 모두 ZSET 에 등록돼야 함")
            .isEqualTo(2L);
        assertThat(redisTemplate.getExpire(RedisKeys.popupWaitingQueue(popupId)))
            .as("구버전: race 로 인해 waiting ZSET 에 TTL 미설정 (버그)")
            .isEqualTo(-1L);
        assertThat(redisTemplate.getExpire(RedisKeys.popupQueueSeq(popupId)))
            .as("구버전: race 로 인해 seq 키에도 TTL 미설정 (버그)")
            .isEqualTo(-1L);
    }

    // ── TC-TTL-2 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-TTL-2: Lua 원자화 — 동시 enqueue 시 ZSET·seq 키 모두 반드시 TTL 설정")
    void luaImpl_concurrentEnqueue_ttlSetOnBothKeys() throws Exception {
        long popupId = 502L;
        LocalDateTime endAt = LocalDateTime.of(2099, 12, 31, 23, 59);
        int threadCount = 10;

        ExecutorService exec = Executors.newFixedThreadPool(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        for (int i = 1; i <= threadCount; i++) {
            final String userId = String.valueOf(i);
            exec.submit(() -> {
                try {
                    start.await();
                    service.enqueue(popupId, userId, endAt);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();  // 모든 스레드 동시 출발
        done.await(10, SECONDS);
        exec.shutdown();

        assertThat(redisTemplate.opsForZSet().size(RedisKeys.popupWaitingQueue(popupId)))
            .as("모든 유저가 ZSET 에 등록돼야 함")
            .isEqualTo((long) threadCount);
        assertThat(redisTemplate.getExpire(RedisKeys.popupWaitingQueue(popupId)))
            .as("Lua 원자화: waiting ZSET 에 TTL 설정")
            .isPositive();
        assertThat(redisTemplate.getExpire(RedisKeys.popupQueueSeq(popupId)))
            .as("Lua 원자화: seq 키에 TTL 설정")
            .isPositive();
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────

    /**
     * 구버전(비원자) enqueue Redis 로직.
     * ZADD 완료 후 countDownOwn 을 감소시키고, awaitOther 가 0 이 될 때까지 기다린 뒤
     * ZCARD 를 확인한다. 이로써 두 스레드가 반드시 둘 다 ZADD 를 마친 뒤에 ZCARD 를 보도록
     * 경쟁 윈도우를 확정적으로 재현한다.
     */
    private void brokenEnqueue(long popupId, String userId, LocalDateTime endAt,
        CountDownLatch countDownOwn, CountDownLatch awaitOther) throws InterruptedException {
        Long seq = redisTemplate.opsForValue().increment(RedisKeys.popupQueueSeq(popupId));
        Boolean added = redisTemplate.opsForZSet().addIfAbsent(RedisKeys.popupWaitingQueue(popupId), userId, seq);
        countDownOwn.countDown();   // 내 ZADD 완료 신호
        awaitOther.await(5, SECONDS); // 상대방 ZADD 완료 대기

        boolean isFirst = Boolean.TRUE.equals(added)
            && Long.valueOf(1L).equals(redisTemplate.opsForZSet().size(RedisKeys.popupWaitingQueue(popupId)));
        if (isFirst) {
            Instant expireAt = queueProperties.computeExpireAt(endAt);
            redisTemplate.expireAt(RedisKeys.popupWaitingQueue(popupId), expireAt);
            redisTemplate.expireAt(RedisKeys.popupQueueSeq(popupId), expireAt);
        }
    }

    private void cleanup() {
        for (String pattern : List.of("waiting:popup:*", "seq:popup:*", "proceed:popup:*",
            RedisKeys.activeWaitingPopups())) {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        }
        queueEntryRepository.deleteAll();
    }
}

package com.back.popspot.global.queue;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
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
 * getActivePopupIds()의 hasKey→SREM 경쟁 조건 재현 및 Lua 원자화 검증 테스트.
 *
 * <p>구버전 버그: Thread A가 hasKey()=false 결과를 캡처한 뒤 SREM을 실행하기 전,
 * Thread B의 enqueue()가 ZADD+SADD를 완료하면 A의 SREM이 B가 정상 등록한 popupId를
 * active set에서 삭제한다. 이 경우 스케줄러가 해당 팝업을 처리 대상에서 누락시켜
 * 대기자들이 ZSET TTL 만료까지 admit을 받지 못한다.
 *
 * <p>Lua 원자화: SMEMBERS → (EXISTS → SREM or keep) 전체를 단일 원자 스크립트로 실행해
 * 위 윈도우를 제거한다. EXISTS 확인 시점에 enqueue가 이미 ZADD를 완료했다면 EXISTS=1이
 * 되어 SREM이 발생하지 않는다.
 *
 * <p>컨테이너 구성 및 TestConfig는 {@link ActiveWaitingPopupsIndexTest}와 동일하다.
 */
@SpringBootTest(
    classes = ActiveWaitingPopupsRaceConditionTest.TestConfig.class,
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
@DisplayName("getActivePopupIds Lua 원자화 경쟁 조건 테스트")
class ActiveWaitingPopupsRaceConditionTest {

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

    // ── TC-RACE-1 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-RACE-1: 구버전 비원자 구현 — hasKey↔SREM 윈도우에서 enqueue 끼어들면 active set 손상")
    void nonAtomicImpl_raceWindow_erasesValidEnqueue() throws Exception {
        long popupId = 100L;

        // Given: active set에만 있고 ZSET은 없는 상태 (TTL 만료 직후)
        redisTemplate.opsForSet().add(RedisKeys.activeWaitingPopups(), String.valueOf(popupId));

        CountDownLatch enqueueStart = new CountDownLatch(1);
        CountDownLatch enqueueFinished = new CountDownLatch(1);

        // Thread B: hasKey 확인 직후 신호를 받아 enqueue 실행 (경쟁 윈도우 진입)
        CompletableFuture<Void> futureB = CompletableFuture.runAsync(() -> {
            try {
                enqueueStart.await(5, SECONDS);
                service.enqueue(popupId, "1", LocalDateTime.now().plusDays(1));
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                enqueueFinished.countDown();
            }
        });

        // Thread A: 구버전 비원자 로직 — hasKey 직후 hook으로 B의 enqueue를 대기
        brokenGetActivePopupIds(() -> {
            enqueueStart.countDown();                       // B에게 enqueue 시작 신호
            try {
                enqueueFinished.await(5, SECONDS);          // B의 ZADD+SADD 완료 대기
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        futureB.get(10, SECONDS);

        // 버그 확인: B의 enqueue가 SADD로 등록했지만 A의 SREM이 즉시 삭제함
        assertThat(redisTemplate.opsForSet()
            .isMember(RedisKeys.activeWaitingPopups(), String.valueOf(popupId)))
            .as("구버전: SREM이 enqueue의 SADD를 덮어써 active set에서 popupId가 사라짐 (버그 재현)")
            .isFalse();

        // ZSET은 enqueue로 생성돼 있음 → 스케줄러는 이 팝업을 영원히 누락
        assertThat(redisTemplate.hasKey(RedisKeys.popupWaitingQueue(popupId)))
            .as("ZSET은 살아있으나 active set에 없어 스케줄러 admitBatch 대상에서 누락")
            .isTrue();
    }

    // ── TC-RACE-2 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-RACE-2: Lua 원자화 — SMEMBERS 이후 enqueue 완료 상태에서도 SREM 발생하지 않음")
    void luaImpl_enqueueBeforeScript_retainsPopupInActiveSet() {
        long popupId = 200L;

        // Given: active set에만 있고 ZSET은 없는 상태 (TTL 만료 직후)
        redisTemplate.opsForSet().add(RedisKeys.activeWaitingPopups(), String.valueOf(popupId));

        // SMEMBERS 이후 Lua 실행 전에 enqueue가 완료된 상태를 시뮬레이션
        // (Lua의 EXISTS 호출 시점에 ZSET이 이미 존재 → EXISTS=1 → SREM 안 함)
        service.enqueue(popupId, "1", LocalDateTime.now().plusDays(1));

        // When: Lua 원자화 getActivePopupIds
        Set<Long> active = service.getActivePopupIds();

        // Then: EXISTS=1이므로 SREM 없이 결과에 포함
        assertThat(active)
            .as("Lua 원자화: enqueue로 살아있는 ZSET은 결과에 포함")
            .contains(popupId);
        assertThat(redisTemplate.opsForSet()
            .isMember(RedisKeys.activeWaitingPopups(), String.valueOf(popupId)))
            .as("Lua 원자화: active set에서 popupId가 삭제되지 않음")
            .isTrue();
    }

    // ── TC-RACE-3 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-RACE-3: Lua 원자화 — 동시 다발적 getActivePopupIds + enqueue에서 popupId 누락 없음")
    void luaImpl_concurrentEnqueueAndCleanup_noPopupIdLost() throws Exception {
        long popupId = 300L;
        int iterations = 50;

        for (int i = 0; i < iterations; i++) {
            cleanup();
            // active set에만 있고 ZSET 없는 상태 (정리 대상처럼 보이는 엔트리)
            redisTemplate.opsForSet().add(RedisKeys.activeWaitingPopups(), String.valueOf(popupId));

            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(2);
            String userId = String.valueOf(i + 1);

            // Thread A: getActivePopupIds (Lua 원자 cleanup)
            CompletableFuture.runAsync(() -> {
                try {
                    start.await();
                    service.getActivePopupIds();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });

            // Thread B: enqueue (ZADD + SADD)
            CompletableFuture.runAsync(() -> {
                try {
                    start.await();
                    service.enqueue(popupId, userId, LocalDateTime.now().plusDays(1));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });

            start.countDown(); // 동시 시작
            done.await(10, SECONDS);

            // 두 경우 모두 popupId는 active set에 있어야 한다:
            //   - Lua가 먼저: EXISTS=0 → SREM, 이후 enqueue의 SADD로 복원
            //   - enqueue가 먼저: EXISTS=1 → SREM 없음, active set 유지
            assertThat(redisTemplate.opsForSet()
                .isMember(RedisKeys.activeWaitingPopups(), String.valueOf(popupId)))
                .as("반복 %d: enqueue 완료 후 popupId가 active set에 존재해야 함", i)
                .isTrue();
        }
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────

    /**
     * 구버전(비원자) getActivePopupIds 로직.
     * hasKey 확인 후 SREM 실행 전에 {@code raceHook}을 호출해 경쟁 윈도우를 외부에서 제어할 수 있다.
     * TC-RACE-1에서 버그 재현 용도로만 사용한다.
     */
    private Set<Long> brokenGetActivePopupIds(Runnable raceHook) {
        Set<String> ids = redisTemplate.opsForSet().members(RedisKeys.activeWaitingPopups());
        if (ids == null || ids.isEmpty()) {
            return Collections.emptySet();
        }
        Set<Long> active = new HashSet<>();
        for (String id : ids) {
            long pid = Long.parseLong(id);
            if (Boolean.TRUE.equals(redisTemplate.hasKey(RedisKeys.popupWaitingQueue(pid)))) {
                active.add(pid);
            } else {
                raceHook.run(); // ← 경쟁 윈도우: hasKey=false 확인 직후, SREM 직전
                redisTemplate.opsForSet().remove(RedisKeys.activeWaitingPopups(), id);
            }
        }
        return active;
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

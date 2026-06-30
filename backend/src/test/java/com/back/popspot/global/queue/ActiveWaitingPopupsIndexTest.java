package com.back.popspot.global.queue;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

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
 * 활성 대기열 팝업 인덱스(Set) 동작 테스트.
 *
 * <p>{@link WaitingQueueRedisService#enqueue}가 {@link RedisKeys#activeWaitingPopups()} Set에
 * popupId를 등록하고, {@link WaitingQueueRedisService#getActivePopupIds()}가 ZSET이 살아있는
 * 팝업만 반환하면서 죽은 popupId(Set에만 있고 ZSET 없음)를 lazy하게 제거하는지 검증한다.
 *
 * <p>{@link QueueMultiInstanceRecoveryTest}의 Testcontainers(MySQL + Redis) 셋업을 따른다.
 * {@code @CircuitBreaker}/{@code @Transactional}은 프록시 없이 직접 Redis를 호출해도 무방하므로
 * 서비스는 {@code new}로 직접 생성한다.
 */
@SpringBootTest(
    classes = ActiveWaitingPopupsIndexTest.TestConfig.class,
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
@DisplayName("활성 대기열 팝업 인덱스(Set) 테스트")
class ActiveWaitingPopupsIndexTest {

    // ── 최소 Spring 컨텍스트 (StringRedisTemplate + JPA Repository만) ──────────
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

    // ── TC-1 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("enqueue 시 activeWaitingPopups Set에 popupId가 추가된다")
    void enqueue_addsPopupIdToActiveSet() {
        long popupId = 100L;

        service.enqueue(popupId, "1", LocalDateTime.now().plusDays(1));

        Set<String> members = redisTemplate.opsForSet().members(RedisKeys.activeWaitingPopups());
        assertThat(members).contains(String.valueOf(popupId));
    }

    // ── TC-2 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getActivePopupIds는 ZSET이 살아있는 팝업만 반환하고 죽은 popupId는 제외한다")
    void getActivePopupIds_returnsOnlyPopupsWithLiveZset() {
        // 살아있는 팝업: enqueue로 ZSET + Set 모두 생성
        long livePopupId = 100L;
        service.enqueue(livePopupId, "1", LocalDateTime.now().plusDays(1));

        // 죽은 팝업: Set에는 있지만 ZSET은 없는 상태를 수동으로 만든다
        long deadPopupId = 200L;
        redisTemplate.opsForSet().add(RedisKeys.activeWaitingPopups(), String.valueOf(deadPopupId));

        Set<Long> active = service.getActivePopupIds();

        assertThat(active).contains(livePopupId);
        assertThat(active).doesNotContain(deadPopupId);
    }

    // ── TC-3 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ZSET 없는 죽은 popupId는 getActivePopupIds 호출 시 Set에서 lazy 제거된다")
    void getActivePopupIds_lazilyRemovesDeadPopupIdFromSet() {
        long deadPopupId = 300L;
        // ZSET 없이 Set에만 죽은 popupId를 넣어 둔다
        redisTemplate.opsForSet().add(RedisKeys.activeWaitingPopups(), String.valueOf(deadPopupId));
        assertThat(redisTemplate.opsForSet().isMember(
            RedisKeys.activeWaitingPopups(), String.valueOf(deadPopupId))).isTrue();

        service.getActivePopupIds();

        // lazy 청소로 Set에서 빠져야 한다
        assertThat(redisTemplate.opsForSet().isMember(
            RedisKeys.activeWaitingPopups(), String.valueOf(deadPopupId))).isFalse();
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────

    private void cleanup() {
        for (String pattern : List.of("waiting:popup:*", "seq:popup:*", "proceed:popup:*", RedisKeys.activeWaitingPopups())) {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        }
        queueEntryRepository.deleteAll();
    }
}

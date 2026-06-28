package com.back.popspot.global.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.back.popspot.domain.queue.entity.PopupQueueEntry;
import com.back.popspot.domain.queue.entity.QueueEntryStatus;
import com.back.popspot.domain.queue.repository.PopupQueueEntryRepository;
import com.back.popspot.global.queue.service.WaitingQueueRedisService;
import com.back.popspot.global.redis.RedisKeys;
import com.back.popspot.support.IntegrationTestSupport;

@DisplayName("Phase 0 — DB source of truth 통합 테스트")
class WaitingQueueDbSyncTest extends IntegrationTestSupport {

    private static final long POPUP_ID = 77777L;

    @Autowired
    private WaitingQueueRedisService queueService;

    @Autowired
    private PopupQueueEntryRepository entryRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        cleanupRedisKeys();
    }

    @AfterEach
    void tearDown() {
        cleanupRedisKeys();
    }

    private void cleanupRedisKeys() {
        Set<String> keys = redisTemplate.keys("*popup:" + POPUP_ID + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    // ── DB_SYNC_1 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("DB_SYNC_1: enqueue 후 DB WAITING 행 존재, ZSET score == DB seq")
    void enqueue_후_DB_WAITING_행과_ZSET_score_일치() {
        queueService.enqueue(POPUP_ID, "100");

        // DB 확인
        List<PopupQueueEntry> entries = entryRepository.findAll();
        assertThat(entries).hasSize(1);
        PopupQueueEntry entry = entries.get(0);
        assertThat(entry.getUserId()).isEqualTo(100L);
        assertThat(entry.getPopupId()).isEqualTo(POPUP_ID);
        assertThat(entry.getStatus()).isEqualTo(QueueEntryStatus.WAITING);
        assertThat(entry.getCreatedAt()).isNotNull();

        // Redis 확인 — ZSET score == DB seq (순번 고정 식별)
        Double score = redisTemplate.opsForZSet()
            .score(RedisKeys.popupWaitingQueue(POPUP_ID), "100");
        assertThat(score).isNotNull();
        assertThat(score.longValue()).isEqualTo(entry.getSeq());
    }

    // ── DB_SYNC_2 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("DB_SYNC_2: admitBatch 후 DB ADMITTED 전환 + ZSET 제거 + proceed 키 존재")
    void admitBatch_후_DB_ADMITTED_ZSET제거_proceed키존재() {
        queueService.enqueue(POPUP_ID, "200");
        Long entryId = entryRepository.findAll().get(0).getId();

        queueService.admitBatch(POPUP_ID, 1);

        // DB: ADMITTED 전환 (clearAutomatically=true로 PC 캐시 무효화 후 조회)
        PopupQueueEntry entry = entryRepository.findById(entryId).orElseThrow();
        assertThat(entry.getStatus()).isEqualTo(QueueEntryStatus.ADMITTED);

        // Redis: ZSET에서 제거됨
        assertThat(redisTemplate.opsForZSet()
            .score(RedisKeys.popupWaitingQueue(POPUP_ID), "200")).isNull();

        // Redis: proceed 키 존재
        assertThat(redisTemplate.hasKey(
            RedisKeys.popupProceedFlag(POPUP_ID, "200"))).isTrue();
    }

    // ── DB_SYNC_3 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("DB_SYNC_3: 정상 흐름 한 바퀴 — DB 행 집합 ↔ Redis 상태 완전 일치")
    void 정상흐름_한바퀴_DB와_Redis_상태_일치() {
        queueService.enqueue(POPUP_ID, "1");
        queueService.enqueue(POPUP_ID, "2");
        queueService.enqueue(POPUP_ID, "3");

        queueService.admitBatch(POPUP_ID, 2);

        // DB 상태 확인
        List<PopupQueueEntry> allEntries = entryRepository.findAll();
        List<PopupQueueEntry> admitted = allEntries.stream()
            .filter(e -> e.getStatus() == QueueEntryStatus.ADMITTED).toList();
        List<PopupQueueEntry> waiting = allEntries.stream()
            .filter(e -> e.getStatus() == QueueEntryStatus.WAITING).toList();

        assertThat(admitted).hasSize(2);
        assertThat(waiting).hasSize(1);

        // Redis 상태 확인
        assertThat(redisTemplate.opsForZSet()
            .size(RedisKeys.popupWaitingQueue(POPUP_ID))).isEqualTo(1);
        assertThat(redisTemplate.keys(
            RedisKeys.popupProceedFlagPattern(POPUP_ID))).hasSize(2);

        // DB ADMITTED ↔ Redis proceed 키 1:1 매핑
        for (PopupQueueEntry e : admitted) {
            assertThat(redisTemplate.hasKey(
                RedisKeys.popupProceedFlag(POPUP_ID, e.getUserId().toString())))
                .as("ADMITTED userId=%d 의 proceed 키가 없음", e.getUserId())
                .isTrue();
        }

        // DB WAITING ↔ ZSET 존재 + score == seq
        for (PopupQueueEntry e : waiting) {
            Double score = redisTemplate.opsForZSet()
                .score(RedisKeys.popupWaitingQueue(POPUP_ID), e.getUserId().toString());
            assertThat(score)
                .as("WAITING userId=%d 가 ZSET에 없음", e.getUserId())
                .isNotNull();
            assertThat(score.longValue())
                .as("WAITING userId=%d 의 ZSET score와 DB seq 불일치", e.getUserId())
                .isEqualTo(e.getSeq());
        }
    }

    // ── DB_SYNC_4 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("DB_SYNC_4: admitOne 0행(DB 미매칭) — 예외 없음, proceed 키 세팅 (자가 치유 흐름)")
    void admitOne_0행_예외없음_proceed키_세팅() {
        // DB에 WAITING 행 없이 ZSET에만 직접 멤버 추가 — 크래시 윈도우 시뮬레이션
        // (INCR 후 INSERT 전 크래시로 Redis에만 남은 유령 멤버)
        redisTemplate.opsForZSet().add(RedisKeys.popupWaitingQueue(POPUP_ID), "999", 1.0);

        // 예외 없이 완료 (결정 2(가) — 미매칭은 warn 로그만, 자가 치유 흐름 유지)
        assertThatNoException().isThrownBy(() -> queueService.admitBatch(POPUP_ID, 1));

        // proceed 키는 세팅됨 — 사용자가 접근 가능하도록 자가 치유
        assertThat(redisTemplate.hasKey(
            RedisKeys.popupProceedFlag(POPUP_ID, "999"))).isTrue();

        // DB에 ADMITTED 행은 없음 (0행 업데이트이므로)
        assertThat(entryRepository.findAll()).isEmpty();
    }
}

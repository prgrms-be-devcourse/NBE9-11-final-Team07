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
import org.springframework.data.redis.core.StringRedisTemplate;

import com.back.popspot.domain.queue.entity.PopupQueueEntry;
import com.back.popspot.domain.queue.entity.QueueEntryStatus;
import com.back.popspot.domain.queue.repository.PopupQueueEntryRepository;
import com.back.popspot.global.queue.service.WaitingQueueRedisService;
import com.back.popspot.global.redis.RedisKeys;
import com.back.popspot.support.IntegrationTestSupport;

@DisplayName("WAITING 중복 진입 방지(멱등 enqueue) 통합 테스트")
class WaitingQueueIdempotentEnqueueTest extends IntegrationTestSupport {

    private static final long POPUP_A = 88881L;
    private static final long POPUP_B = 88882L;
    private static final LocalDateTime END_AT = LocalDateTime.of(2099, 12, 31, 23, 59);

    @Autowired
    private WaitingQueueRedisService queueService;

    @Autowired
    private PopupQueueEntryRepository repo;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        cleanRedis();
    }

    @AfterEach
    void tearDown() {
        cleanRedis();
    }

    private void cleanRedis() {
        for (long popupId : List.of(POPUP_A, POPUP_B)) {
            Set<String> keys = redisTemplate.keys("*popup:" + popupId + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        }
    }

    // ── 1. 동일 유저·팝업 중복 enqueue → DB 행 1개, seq 1회만 소비 ──────────────

    @Test
    @DisplayName("동일 유저·팝업 WAITING 중 재진입 → DB 행 1개, Redis seq 1만 소비")
    void 중복_enqueue_DB행_1개_seq_낭비없음() {
        String userId = "50001";

        queueService.enqueue(POPUP_A, userId, END_AT);
        queueService.enqueue(POPUP_A, userId, END_AT);

        List<PopupQueueEntry> waiting = repo.findByPopupIdAndStatusOrderBySeqAsc(POPUP_A, QueueEntryStatus.WAITING);
        assertThat(waiting).hasSize(1);

        String seqStr = redisTemplate.opsForValue().get(RedisKeys.popupQueueSeq(POPUP_A));
        assertThat(Long.parseLong(seqStr)).isEqualTo(1L);
    }

    // ── 1-b. 사전체크 경로 증명: INCR 미호출 ─────────────────────────────────
    //
    // dedup 컬럼 제약이 막힌다면 → INCR은 이미 호출된 뒤 INSERT만 롤백 → seq=2
    // 사전체크(existsBy)가 막힌다면 → INCR 자체가 호출되지 않음 → seq=1
    // seq 값으로 두 경로를 구분할 수 있다.

    @Test
    @DisplayName("사전체크 경로 증명: 2차 enqueue 후 Redis seq 카운터가 1에서 증가하지 않음")
    void 사전체크_경로_INCR_미호출_증명() {
        String userId = "50011";

        queueService.enqueue(POPUP_A, userId, END_AT);
        long seqAfterFirst = Long.parseLong(redisTemplate.opsForValue().get(RedisKeys.popupQueueSeq(POPUP_A)));

        queueService.enqueue(POPUP_A, userId, END_AT);  // 사전체크 → 조기 return
        long seqAfterSecond = Long.parseLong(redisTemplate.opsForValue().get(RedisKeys.popupQueueSeq(POPUP_A)));

        // INCR이 불렸다면 seqAfterSecond == seqAfterFirst + 1.
        // 사전체크가 INCR 전에 막아야만 두 값이 같다.
        assertThat(seqAfterSecond).isEqualTo(seqAfterFirst);
    }

    // ── 2. WAITING 행의 dedupKey 값 검증 ─────────────────────────────────────

    @Test
    @DisplayName("waiting() 팩토리 → dedup_key = '{popupId}:{userId}' 로 채워짐")
    void waiting_팩토리_dedupKey_채워짐() {
        String userId = "50002";

        queueService.enqueue(POPUP_A, userId, END_AT);

        PopupQueueEntry entry = repo.findByPopupIdAndStatusOrderBySeqAsc(POPUP_A, QueueEntryStatus.WAITING).get(0);
        assertThat(entry.getDedupKey()).isEqualTo(POPUP_A + ":" + userId);
    }

    // ── 3. admitBatch → dedupKey NULL, 이후 재진입 가능 ──────────────────────

    @Test
    @DisplayName("admitBatch 후 dedupKey = NULL → 동일 유저 재진입 WAITING 가능")
    void admit_후_dedupKey_null_재진입_가능() {
        String userId = "50003";

        queueService.enqueue(POPUP_A, userId, END_AT);
        queueService.admitBatch(POPUP_A, 1);

        // ADMITTED 행의 dedupKey가 NULL이어야 재진입 WAITING INSERT가 충돌하지 않음
        List<PopupQueueEntry> admitted = repo.findByPopupIdAndStatusOrderBySeqAsc(POPUP_A, QueueEntryStatus.ADMITTED);
        assertThat(admitted).hasSize(1);
        assertThat(admitted.get(0).getDedupKey()).isNull();

        // 재진입
        queueService.enqueue(POPUP_A, userId, END_AT);

        assertThat(repo.findByPopupIdAndStatusOrderBySeqAsc(POPUP_A, QueueEntryStatus.WAITING)).hasSize(1);
        assertThat(repo.findByPopupIdAndStatusOrderBySeqAsc(POPUP_A, QueueEntryStatus.ADMITTED)).hasSize(1);
    }

    // ── 4. 다른 팝업 동일 유저 → 각각 독립적으로 WAITING 가능 ────────────────

    @Test
    @DisplayName("동일 유저·다른 팝업 → 각 팝업 WAITING 행 독립 생성")
    void 다른_팝업_동일_유저_각각_WAITING_가능() {
        String userId = "50004";

        queueService.enqueue(POPUP_A, userId, END_AT);
        queueService.enqueue(POPUP_B, userId, END_AT);

        assertThat(repo.findByPopupIdAndStatusOrderBySeqAsc(POPUP_A, QueueEntryStatus.WAITING)).hasSize(1);
        assertThat(repo.findByPopupIdAndStatusOrderBySeqAsc(POPUP_B, QueueEntryStatus.WAITING)).hasSize(1);
    }

    // ── 5. 다른 유저 동일 팝업 → 둘 다 WAITING 가능 ─────────────────────────

    @Test
    @DisplayName("다른 유저·동일 팝업 → 각각 WAITING 행 독립 생성")
    void 다른_유저_동일_팝업_각각_WAITING_가능() {
        queueService.enqueue(POPUP_A, "50005", END_AT);
        queueService.enqueue(POPUP_A, "50006", END_AT);

        assertThat(repo.findByPopupIdAndStatusOrderBySeqAsc(POPUP_A, QueueEntryStatus.WAITING)).hasSize(2);
    }
}

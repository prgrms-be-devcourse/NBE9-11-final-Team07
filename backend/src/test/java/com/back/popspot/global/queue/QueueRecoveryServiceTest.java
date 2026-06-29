package com.back.popspot.global.queue;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.back.popspot.domain.queue.entity.PopupQueueEntry;
import com.back.popspot.domain.queue.repository.PopupQueueEntryRepository;
import com.back.popspot.global.queue.scheduler.WaitingQueueScheduler;
import com.back.popspot.global.queue.service.QueueRecoveryService;
import com.back.popspot.global.queue.service.WaitingQueueRedisService;
import com.back.popspot.global.redis.RedisKeys;
import com.back.popspot.support.ContainerIntegrationTestSupport;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;

@DisplayName("QueueRecoveryService 통합 테스트")
class QueueRecoveryServiceTest extends ContainerIntegrationTestSupport {

    private static final long POPUP_A = 88801L;
    private static final long POPUP_B = 88802L;
    private static final long POPUP_C = 88803L;
    private static final long POPUP_D = 88804L;

    // TTL 검증에 사용할 far-future reservationEndAt: TTL이 양수임을 확인하기에 충분
    private static final LocalDateTime FAR_FUTURE = LocalDateTime.of(2099, 12, 31, 23, 59);

    @Autowired
    private QueueRecoveryService recoveryService;

    @Autowired
    private WaitingQueueRedisService queueService;

    @Autowired
    private WaitingQueueScheduler waitingQueueScheduler;

    @Autowired
    private LockingTaskExecutor lockingTaskExecutor;

    @Autowired
    private PopupQueueEntryRepository entryRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @AfterEach
    void tearDownRedis() {
        for (long id : new long[]{POPUP_A, POPUP_B, POPUP_C, POPUP_D}) {
            redisTemplate.delete(RedisKeys.popupWaitingQueue(id));
            redisTemplate.delete(RedisKeys.popupQueueSeq(id));
            Set<String> proceedKeys = redisTemplate.keys(RedisKeys.popupProceedFlagPattern(id));
            if (proceedKeys != null && !proceedKeys.isEmpty()) {
                redisTemplate.delete(proceedKeys);
            }
        }
    }

    // ── 시나리오 1 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("시나리오1: DB WAITING + ADMITTED 혼재 → Redis 비움 → recover() → ZSET=WAITING행만, seq=MAX, TTL 양수")
    void recover_WAITING_ADMITTED_혼재시_ZSET_복원() {
        // given — DB 세팅 (WAITING 3건, ADMITTED 1건)
        PopupQueueEntry w1 = PopupQueueEntry.waiting(1L, POPUP_A, 1L);
        PopupQueueEntry w2 = PopupQueueEntry.waiting(2L, POPUP_A, 2L);
        PopupQueueEntry w3 = PopupQueueEntry.waiting(3L, POPUP_A, 3L);
        PopupQueueEntry a4 = PopupQueueEntry.waiting(4L, POPUP_A, 4L);
        a4.admit();
        entryRepository.save(w1);
        entryRepository.save(w2);
        entryRepository.save(w3);
        entryRepository.save(a4);

        // when
        recoveryService.recover(POPUP_A, FAR_FUTURE);

        // then — ZSET: WAITING 유저 3명만 (score == seq)
        assertThat(redisTemplate.opsForZSet().size(RedisKeys.popupWaitingQueue(POPUP_A))).isEqualTo(3L);
        assertThat(redisTemplate.opsForZSet().score(RedisKeys.popupWaitingQueue(POPUP_A), "1")).isEqualTo(1.0);
        assertThat(redisTemplate.opsForZSet().score(RedisKeys.popupWaitingQueue(POPUP_A), "2")).isEqualTo(2.0);
        assertThat(redisTemplate.opsForZSet().score(RedisKeys.popupWaitingQueue(POPUP_A), "3")).isEqualTo(3.0);
        assertThat(redisTemplate.opsForZSet().score(RedisKeys.popupWaitingQueue(POPUP_A), "4")).isNull();

        // then — seq 카운터: ADMITTED 포함 전체 MAX
        assertThat(redisTemplate.opsForValue().get(RedisKeys.popupQueueSeq(POPUP_A))).isEqualTo("4");

        // then — TTL: 두 키 모두 양수 (무제한 아님)
        assertThat(redisTemplate.getExpire(RedisKeys.popupWaitingQueue(POPUP_A))).isGreaterThan(0L);
        assertThat(redisTemplate.getExpire(RedisKeys.popupQueueSeq(POPUP_A))).isGreaterThan(0L);
    }

    // ── 시나리오 2 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("시나리오2: DB ADMITTED 행만 존재 → recover() → ZSET 없음, seq=MAX, seq TTL 양수")
    void recover_ADMITTED만_있으면_ZSET_비어있고_seq_복원() {
        // given — ADMITTED만 (seq 5)
        PopupQueueEntry a5 = PopupQueueEntry.waiting(10L, POPUP_B, 5L);
        a5.admit();
        entryRepository.save(a5);

        // when
        recoveryService.recover(POPUP_B, FAR_FUTURE);

        // then — ZSET 키 자체가 없어야 함 (WAITING 0건 → ZADD 없음)
        assertThat(redisTemplate.hasKey(RedisKeys.popupWaitingQueue(POPUP_B))).isFalse();

        // then — seq 카운터: ADMITTED MAX인 5
        assertThat(redisTemplate.opsForValue().get(RedisKeys.popupQueueSeq(POPUP_B))).isEqualTo("5");

        // then — seq 키는 반드시 TTL 양수 (ZSET은 존재하지 않으므로 expireAt no-op이 정상)
        assertThat(redisTemplate.getExpire(RedisKeys.popupQueueSeq(POPUP_B))).isGreaterThan(0L);
    }

    // ── 시나리오 3 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("시나리오3: recover() 직후 enqueue() 시 seq = (복구 MAX + 1), 충돌 없음, TTL 유지")
    void recover_후_enqueue_seq_충돌_없음() {
        // given — ADMITTED seq=3, WAITING seq=4
        PopupQueueEntry a3 = PopupQueueEntry.waiting(20L, POPUP_C, 3L);
        a3.admit();
        PopupQueueEntry w4 = PopupQueueEntry.waiting(21L, POPUP_C, 4L);
        entryRepository.save(a3);
        entryRepository.save(w4);

        recoveryService.recover(POPUP_C, FAR_FUTURE);

        // seq 카운터가 4인지 중간 검증
        assertThat(redisTemplate.opsForValue().get(RedisKeys.popupQueueSeq(POPUP_C))).isEqualTo("4");

        // when — 신규 유저 enqueue (seq != 1이므로 enqueue()의 TTL 재설정은 실행 안 됨)
        String newUserId = "9999";
        queueService.enqueue(POPUP_C, newUserId, FAR_FUTURE);

        // then — INCR 결과 = 5 (MAX 4 + 1), ZSET score도 5
        Double newScore = redisTemplate.opsForZSet()
            .score(RedisKeys.popupWaitingQueue(POPUP_C), newUserId);
        assertThat(newScore).isEqualTo(5.0);

        // then — 기존 WAITING userId=21 의 score(4)와 중복 없음
        assertThat(redisTemplate.opsForZSet().size(RedisKeys.popupWaitingQueue(POPUP_C))).isEqualTo(2L);
        assertThat(redisTemplate.opsForZSet().score(RedisKeys.popupWaitingQueue(POPUP_C), "21")).isEqualTo(4.0);

        // then — TTL: INCR/ZADD는 TTL을 초기화하지 않으므로 recover()가 설정한 TTL이 유지됨
        assertThat(redisTemplate.getExpire(RedisKeys.popupWaitingQueue(POPUP_C))).isGreaterThan(0L);
        assertThat(redisTemplate.getExpire(RedisKeys.popupQueueSeq(POPUP_C))).isGreaterThan(0L);
    }

    // ── TTL 공백 버그 증명 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("TTL공백회귀: recover(WAITING=0) 후 enqueue() → 신규 ZSET에 TTL 정상 적용")
    void recover_WAITING_0_후_enqueue_ZSET_TTL_정상적용() {
        // given — ADMITTED만 있는 popup (seq=5), WAITING=0 상태로 recover
        PopupQueueEntry a5 = PopupQueueEntry.waiting(50L, POPUP_B, 5L);
        a5.admit();
        entryRepository.save(a5);
        recoveryService.recover(POPUP_B, FAR_FUTURE);

        // 전제: ZSET 없음, seq TTL 양수
        assertThat(redisTemplate.hasKey(RedisKeys.popupWaitingQueue(POPUP_B))).isFalse();
        assertThat(redisTemplate.getExpire(RedisKeys.popupQueueSeq(POPUP_B))).isGreaterThan(0L);

        // when — 신규 유저 enqueue (seq=6, added=true && size==1 → TTL 적용)
        queueService.enqueue(POPUP_B, "777", FAR_FUTURE);

        // then — ZSET 생성 + TTL 양수 (무제한 아님)
        assertThat(redisTemplate.hasKey(RedisKeys.popupWaitingQueue(POPUP_B))).isTrue();
        assertThat(redisTemplate.getExpire(RedisKeys.popupWaitingQueue(POPUP_B))).isGreaterThan(0L);
    }

    // ── 시나리오 4 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("시나리오4: recover()가 popup-admission-scheduler 락 보유 중 admitWaiting()이 스킵됨")
    void recover_락_보유중_admitWaiting_스킵() throws Exception {
        // given — Redis ZSET에 직접 대기 항목 추가 (recover() 호출 없이 락 경쟁만 검증)
        redisTemplate.opsForZSet().add(RedisKeys.popupWaitingQueue(POPUP_D), "1", 1.0);
        redisTemplate.opsForZSet().add(RedisKeys.popupWaitingQueue(POPUP_D), "2", 2.0);

        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch schedulerDone = new CountDownLatch(1);

        LockConfiguration lockConfig = new LockConfiguration(
            Instant.now(),
            "popup-admission-scheduler",
            Duration.ofSeconds(30),
            Duration.ZERO
        );

        // 백그라운드 스레드가 락을 점유한 채 대기
        // executeWithLock은 락 획득 실패 시 재시도 없이 즉시 복귀하므로 직접 재시도한다.
        // (스케줄러가 500ms 주기·lockAtLeastFor=450ms로 락을 거의 상시 보유하기 때문)
        CompletableFuture<Void> lockHolder = CompletableFuture.runAsync(() -> {
            try {
                Instant deadline = Instant.now().plusSeconds(5);
                while (lockAcquired.getCount() > 0 && Instant.now().isBefore(deadline)) {
                    lockingTaskExecutor.executeWithLock(
                        () -> {
                            lockAcquired.countDown();
                            schedulerDone.await(10, TimeUnit.SECONDS);
                            return null;
                        },
                        lockConfig
                    );
                    if (lockAcquired.getCount() > 0) {
                        Thread.sleep(60);
                    }
                }
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        });

        // 백그라운드 스레드가 락을 획득할 때까지 대기
        assertThat(lockAcquired.await(5, TimeUnit.SECONDS))
            .as("락 획득이 5초 안에 완료되어야 합니다").isTrue();

        // when — 동일 락 이름으로 admitWaiting() 시도 (ShedLock이 스킵해야 함)
        waitingQueueScheduler.admitWaiting();

        // then — 락이 아직 보유된 상태에서 검증 (Spring 스케줄러가 이 시점에 실행돼도 락 획득 실패 → 스킵)
        // schedulerDone.countDown() 이후에 검증하면 500ms 주기 스케줄러가 락을 선점해 ZSET을 비울 수 있음
        assertThat(redisTemplate.opsForZSet().size(RedisKeys.popupWaitingQueue(POPUP_D))).isEqualTo(2L);
        assertThat(redisTemplate.keys(RedisKeys.popupProceedFlagPattern(POPUP_D))).isEmpty();

        // 백그라운드 스레드 해제 (assertion 완료 후)
        schedulerDone.countDown();
        lockHolder.get(5, TimeUnit.SECONDS);
    }
}

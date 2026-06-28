package com.back.popspot.global.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.back.popspot.domain.popupStore.dto.PopupStoreCreateRequest;
import com.back.popspot.domain.popupStore.entity.PopupFeeType;
import com.back.popspot.domain.popupStore.entity.PopupStore;
import com.back.popspot.domain.popupStore.repository.PopupStoreRepository;
import com.back.popspot.domain.queue.entity.PopupQueueEntry;
import com.back.popspot.domain.queue.repository.PopupQueueEntryRepository;
import com.back.popspot.domain.user.entity.User;
import com.back.popspot.domain.user.repository.UserRepository;
import com.back.popspot.global.queue.scheduler.QueueCleanupScheduler;
import com.back.popspot.support.IntegrationTestSupport;

/**
 * QueueCleanupScheduler 통합 테스트.
 *
 * <p>이 배치는 DB만 정리한다. Redis 키(ZSET·seq)는 절대 TTL로 자동 소멸하므로
 * Redis assertion은 포함하지 않는다.
 *
 * <p>부모 {@link IntegrationTestSupport}의 {@code @Transactional}을
 * {@code NOT_SUPPORTED}로 덮어쓴다. 실제 커밋 후 잔존 데이터를 확인해야
 * 청크별 트랜잭션 분리를 검증할 수 있기 때문이다.
 */
@DisplayName("Phase 3 — 자정 일괄 삭제 배치 통합 테스트")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = "queue-cleanup.chunk-size=5")
class QueueCleanupSchedulerTest extends IntegrationTestSupport {

    @Autowired private QueueCleanupScheduler scheduler;
    @Autowired private PopupStoreRepository popupStoreRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private NamedParameterJdbcTemplate namedJdbc;

    @MockitoSpyBean
    private PopupQueueEntryRepository entryRepository;

    @BeforeEach
    void setUp() {
        reset(entryRepository);
        clearShedLockKey();
    }

    @AfterEach
    void cleanUp() {
        entryRepository.deleteAllInBatch();
        popupStoreRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        clearShedLockKey();
    }

    // ── CLEANUP_1: chunkSize 초과 — 다중 청크 순회 ──────────────────────────

    @Test
    @DisplayName("CLEANUP_1: chunkSize(5) 초과 12개 행 → 다중 청크로 전부 삭제")
    void chunkSize_초과_행_전부_삭제() {
        User user = persistUser("cleanup1@test.com");
        PopupStore expired = persistExpiredPopup(user);
        insertEntries(expired.getId(), 12);  // 청크 3번 순회

        scheduler.cleanupExpiredQueues();

        assertThat(entryRepository.count()).isZero();
    }

    // ── CLEANUP_2: chunkSize 정배수 — 루프 종료 경계 케이스 ─────────────────

    @Test
    @DisplayName("CLEANUP_2: chunkSize(5) 정배수 10개 → 빈 3번째 조회에서 루프 정상 종료")
    void chunkSize_정배수_행_전부_삭제() {
        User user = persistUser("cleanup2@test.com");
        PopupStore expired = persistExpiredPopup(user);
        insertEntries(expired.getId(), 10);  // 5 × 2, 루프가 3번째 page(0,5) 조회 후 0건 → 종료

        scheduler.cleanupExpiredQueues();

        assertThat(entryRepository.count()).isZero();
    }

    // ── TX_ISOLATION: 청크별 독립 트랜잭션 검증 ─────────────────────────────

    @Test
    @DisplayName("TX_ISOLATION: 2번째 청크 DELETE에서 예외 → 1번째 청크(5행)는 이미 커밋, 나머지 2행 잔존")
    void 두번째_청크_예외시_첫번째_청크_커밋_유지() {
        User user = persistUser("tx@test.com");
        PopupStore expired = persistExpiredPopup(user);
        insertEntries(expired.getId(), 7);  // 청크1: 5행, 청크2: 2행

        // @MockitoSpyBean on interface → doCallRealMethod() 사용 불가 (delegation mock 제약).
        // 첫 번째 호출은 NamedParameterJdbcTemplate으로 직접 DELETE(auto-commit).
        // 두 번째 호출에서 RuntimeException을 던져 트랜잭션 분리를 증명한다.
        AtomicInteger callCount = new AtomicInteger();
        doAnswer(invocation -> {
            if (callCount.getAndIncrement() > 0) {
                throw new RuntimeException("청크2 강제 실패 — 트랜잭션 분리 검증");
            }
            @SuppressWarnings("unchecked")
            List<Long> ids = (List<Long>) invocation.getArgument(0);
            namedJdbc.update(
                "DELETE FROM popup_queue_entry WHERE id IN (:ids)",
                Map.of("ids", ids)
            );
            return null;
        }).when(entryRepository).deleteAllByIdInBatch(any());

        assertThatThrownBy(() -> scheduler.cleanupExpiredQueues())
            .isInstanceOf(RuntimeException.class);

        // 첫 번째 청크는 독립적으로 커밋됨 → 예외에도 롤백되지 않고 2행만 남아 있어야 함
        assertThat(entryRepository.count()).isEqualTo(2);
    }

    // ── TARGET_1: 대상 선정 — 종료 팝업만 삭제 ──────────────────────────────

    @Test
    @DisplayName("TARGET_1: 종료 팝업 행만 삭제 — 운영 중(reservationEndAt 미래) 팝업 행은 영향 없음")
    void 종료_팝업만_삭제_운영중_팝업_무영향() {
        User user = persistUser("target1@test.com");
        PopupStore expired = persistExpiredPopup(user);
        PopupStore active = persistActivePopup(user);
        insertEntries(expired.getId(), 3);
        insertEntries(active.getId(), 3);

        scheduler.cleanupExpiredQueues();

        // 전체 3행만 남아 있고, 그 3행 모두 운영 중 팝업 소속
        assertThat(entryRepository.count()).isEqualTo(3);
        List<Long> remainingIds = entryRepository.findIdsByPopupId(active.getId(), PageRequest.of(0, 10));
        assertThat(remainingIds).hasSize(3);
    }

    // ── TARGET_2: reservationEndAt 경계 케이스 ───────────────────────────────

    @Test
    @DisplayName("TARGET_2: reservationEndAt이 배치 실행 시각보다 1초 전 → 삭제 대상 포함")
    void reservationEndAt_직전_경계값_삭제대상_포함() {
        User user = persistUser("target2@test.com");
        // reservationEndAt = now - 1s → 쿼리 조건 reservationEndAt <= :now 충족
        PopupStore boundary = persistPopupWithEndAt(user, LocalDateTime.now().minusSeconds(1));
        insertEntries(boundary.getId(), 2);

        scheduler.cleanupExpiredQueues();

        assertThat(entryRepository.count()).isZero();
    }

    // ── LOOP_1: 행이 0개인 팝업 ──────────────────────────────────────────────

    @Test
    @DisplayName("LOOP_1: 종료 팝업에 행이 0개 → 예외 없이 즉시 종료")
    void 행없는_종료_팝업_예외없이_종료() {
        persistExpiredPopup(persistUser("loop1@test.com"));  // 행 미삽입

        assertThatNoException().isThrownBy(() -> scheduler.cleanupExpiredQueues());
        assertThat(entryRepository.count()).isZero();
    }

    // ── LOOP_2: partial chunk ─────────────────────────────────────────────

    @Test
    @DisplayName("LOOP_2: chunkSize(5) 미만 3개 → 단일 청크로 전부 삭제 후 정상 종료")
    void 부분_청크_단일_삭제_정상종료() {
        User user = persistUser("loop2@test.com");
        PopupStore expired = persistExpiredPopup(user);
        insertEntries(expired.getId(), 3);  // chunkSize 미만

        scheduler.cleanupExpiredQueues();

        assertThat(entryRepository.count()).isZero();
    }

    // ── SHEDLOCK_1: 동시 실행 방지 ────────────────────────────────────────

    @Test
    @DisplayName("SHEDLOCK_1: lockAtLeastFor(1m) 내 2번째 호출 → ShedLock 스킵, 신규 데이터 미삭제")
    void 두번째_호출_ShedLock_스킵() {
        User user = persistUser("shed@test.com");

        // 1차 배치 실행 — 락 획득, lockAtLeastFor="1m" 동안 Redis 키 유지
        PopupStore expired1 = persistExpiredPopup(user);
        insertEntries(expired1.getId(), 3);
        scheduler.cleanupExpiredQueues();
        assertThat(entryRepository.count()).isZero();

        // 1차 완료 직후(락 보유 중) 새 종료 팝업 데이터 추가
        PopupStore expired2 = persistExpiredPopup(user);
        insertEntries(expired2.getId(), 3);
        assertThat(entryRepository.count()).isEqualTo(3);

        // 2차 호출 — ShedLock이 락 획득 실패로 메서드 바디 스킵, 예외 없이 반환
        assertThatNoException().isThrownBy(() -> scheduler.cleanupExpiredQueues());

        // 바디가 실행되지 않았으므로 신규 데이터 그대로 잔존
        assertThat(entryRepository.count()).isEqualTo(3);
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private User persistUser(String email) {
        return userRepository.save(User.create(email, "테스터"));
    }

    private PopupStore persistExpiredPopup(User user) {
        return persistPopupWithEndAt(user, LocalDateTime.now().minusSeconds(1));
    }

    private PopupStore persistActivePopup(User user) {
        return persistPopupWithEndAt(user, LocalDateTime.now().plusDays(10));
    }

    private PopupStore persistPopupWithEndAt(User user, LocalDateTime reservationEndAt) {
        LocalDateTime now = LocalDateTime.now();
        PopupStoreCreateRequest request = new PopupStoreCreateRequest(
            "테스트 팝업",
            "서울",
            PopupFeeType.FREE,
            null,
            now.minusDays(10),
            reservationEndAt,
            now.minusDays(5),
            now.plusDays(1),
            null,
            null
        );
        return popupStoreRepository.save(PopupStore.of(user, request));
    }

    private void insertEntries(Long popupId, int count) {
        List<PopupQueueEntry> entries = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            entries.add(PopupQueueEntry.waiting((long) i, popupId, (long) i));
        }
        entryRepository.saveAll(entries);
    }

    private void clearShedLockKey() {
        Set<String> keys = redisTemplate.keys("*queue-cleanup-scheduler*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}

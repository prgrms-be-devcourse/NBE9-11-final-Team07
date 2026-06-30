package com.back.popspot.domain.queue.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.back.popspot.domain.queue.entity.PopupQueueEntry;
import com.back.popspot.domain.queue.entity.QueueEntryStatus;

/**
 * 운영 중 경로(enqueue, admission)에서 이 리포지토리의 delete* 메서드를 호출하지 말 것.
 * 행 삭제는 오직 자정 배치({@link #findIdsByPopupId} + {@link #deleteAllByIdInBatch})만 허용한다.
 */
public interface PopupQueueEntryRepository extends JpaRepository<PopupQueueEntry, Long> {

    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE PopupQueueEntry e
        SET e.status = :admitted
        WHERE e.popupId = :popupId
          AND e.userId = :userId
          AND e.seq = :seq
          AND e.status = :waiting
        """)
    int admitOne(
        @Param("popupId") Long popupId,
        @Param("userId") Long userId,
        @Param("seq") Long seq,
        @Param("waiting") QueueEntryStatus waiting,
        @Param("admitted") QueueEntryStatus admitted
    );

    // 복구 전용 — WAITING 행을 seq 오름차순으로 조회
    List<PopupQueueEntry> findByPopupIdAndStatusOrderBySeqAsc(Long popupId, QueueEntryStatus status);

    // 테스트/검증용 — 특정 팝업의 대기열 행 전체 조회
    List<PopupQueueEntry> findByPopupIdOrderBySeqAsc(Long popupId);

    // 테스트 정리용 — 특정 팝업의 대기열 행 삭제
    void deleteByPopupId(Long popupId);

    // 복구 전용 — status 무관 MAX(seq) 조회. 항목이 없으면 Optional.empty()
    @Query("SELECT MAX(e.seq) FROM PopupQueueEntry e WHERE e.popupId = :popupId")
    Optional<Long> findMaxSeqByPopupId(@Param("popupId") Long popupId);

    // 자정 배치 전용 — 청크 단위 ID 조회. 운영 경로에서 호출하지 말 것
    @Query("SELECT e.id FROM PopupQueueEntry e WHERE e.popupId = :popupId ORDER BY e.id")
    List<Long> findIdsByPopupId(@Param("popupId") Long popupId, Pageable pageable);
}

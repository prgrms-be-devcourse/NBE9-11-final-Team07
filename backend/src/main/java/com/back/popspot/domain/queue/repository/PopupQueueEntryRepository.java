package com.back.popspot.domain.queue.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.back.popspot.domain.queue.entity.PopupQueueEntry;
import com.back.popspot.domain.queue.entity.QueueEntryStatus;

/**
 * 운영 중 경로(enqueue, admission)에서 이 리포지토리의 delete* 메서드를 호출하지 말 것.
 * 행 삭제는 오직 자정 배치({@link #deleteAllByPopupIdAndCreatedAtBefore})만 허용한다.
 */
public interface PopupQueueEntryRepository extends JpaRepository<PopupQueueEntry, Long> {

    @Modifying
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

    // 자정 배치 전용 — 운영 경로에서 호출하지 말 것
    void deleteAllByPopupIdAndCreatedAtBefore(Long popupId, LocalDateTime cutoff);
}

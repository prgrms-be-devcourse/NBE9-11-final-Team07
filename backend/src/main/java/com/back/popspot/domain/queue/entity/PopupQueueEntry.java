package com.back.popspot.domain.queue.entity;

import com.back.popspot.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@Table(
    name = "popup_queue_entry",
    indexes = @Index(name = "idx_pqe_popup_status_seq", columnList = "popup_id, status, seq"),
    uniqueConstraints = @UniqueConstraint(name = "uq_pqe_dedup_key", columnNames = "dedup_key")
)
public class PopupQueueEntry extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "popup_id", nullable = false)
    private Long popupId;

    @Column(nullable = false)
    private Long seq;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QueueEntryStatus status;

    // WAITING 상태 중복 방지용 키. WAITING이면 "{popupId}:{userId}", ADMITTED이면 NULL.
    // MySQL UNIQUE + NULL 트릭: NULL은 중복 허용되므로 ADMITTED 행끼리는 충돌 없음.
    @Column(name = "dedup_key")
    private String dedupKey;

    private PopupQueueEntry(Long userId, Long popupId, Long seq) {
        this.userId = userId;
        this.popupId = popupId;
        this.seq = seq;
        this.status = QueueEntryStatus.WAITING;
    }

    public static PopupQueueEntry waiting(Long userId, Long popupId, Long seq) {
        PopupQueueEntry entry = new PopupQueueEntry(userId, popupId, seq);
        entry.dedupKey = popupId + ":" + userId;
        return entry;
    }

    public void admit() {
        this.status = QueueEntryStatus.ADMITTED;
    }
}

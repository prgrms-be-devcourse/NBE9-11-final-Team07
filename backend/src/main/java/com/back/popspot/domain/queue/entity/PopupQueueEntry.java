package com.back.popspot.domain.queue.entity;

import com.back.popspot.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@Table(
    name = "popup_queue_entry",
    indexes = @Index(name = "idx_pqe_popup_status_seq", columnList = "popup_id, status, seq")
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

    private PopupQueueEntry(Long userId, Long popupId, Long seq) {
        this.userId = userId;
        this.popupId = popupId;
        this.seq = seq;
        this.status = QueueEntryStatus.WAITING;
    }

    public static PopupQueueEntry waiting(Long userId, Long popupId, Long seq) {
        return new PopupQueueEntry(userId, popupId, seq);
    }

    public void admit() {
        this.status = QueueEntryStatus.ADMITTED;
    }
}

package com.back.popspot.global.queue.scheduler;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import com.back.popspot.domain.popupStore.repository.PopupStoreRepository;
import com.back.popspot.domain.queue.repository.PopupQueueEntryRepository;
import com.back.popspot.global.queue.config.QueueCleanupProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueueCleanupScheduler {

    private final PopupQueueEntryRepository entryRepository;
    private final PopupStoreRepository popupStoreRepository;
    private final QueueCleanupProperties properties;

    @Scheduled(cron = "0 0 0 * * *")
    @SchedulerLock(name = "queue-cleanup-scheduler", lockAtMostFor = "30m", lockAtLeastFor = "1m")
    public void cleanupExpiredQueues() {
        LockAssert.assertLocked();
        long startMs = System.currentTimeMillis();
        LocalDateTime now = LocalDateTime.now();

        List<Long> expiredPopupIds = popupStoreRepository.findExpiredPopupIds(now);
        log.info("[QueueCleanup] 배치 시작 — 대상 팝업 수: {}", expiredPopupIds.size());

        long totalDeleted = 0;
        for (Long popupId : expiredPopupIds) {
            long deleted = deleteInChunks(popupId);
            totalDeleted += deleted;
            log.info("[QueueCleanup] popup_id={} 삭제 완료: {}행", popupId, deleted);
        }

        long elapsedMs = System.currentTimeMillis() - startMs;
        log.info("[QueueCleanup] 배치 종료 — 총 {}행 삭제, 소요 {}ms", totalDeleted, elapsedMs);
    }

    private long deleteInChunks(Long popupId) {
        int chunkSize = properties.chunkSize();
        long deleted = 0;
        List<Long> ids;
        do {
            ids = entryRepository.findIdsByPopupId(popupId, PageRequest.of(0, chunkSize));
            if (!ids.isEmpty()) {
                entryRepository.deleteAllByIdInBatch(ids);
                deleted += ids.size();
            }
        } while (ids.size() == chunkSize);
        return deleted;
    }
}

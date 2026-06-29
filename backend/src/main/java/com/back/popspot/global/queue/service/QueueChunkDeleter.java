package com.back.popspot.global.queue.service;

import java.util.List;

import org.springframework.stereotype.Component;

import com.back.popspot.domain.queue.repository.PopupQueueEntryRepository;

import lombok.RequiredArgsConstructor;

/**
 * 대기열 청크 단위 삭제를 담당한다.
 *
 * <p>스케줄러는 대상 popup 조회와 ID 페이징 순회만 담당하고,
 * 실제 DELETE 실행은 이 클래스가 단독으로 수행한다.
 * {@link PopupQueueEntryRepository#deleteAllByIdInBatch}는 자체 {@code @Transactional}을
 * 보유하므로, 호출자에 트랜잭션이 없으면 청크마다 독립적으로 커밋된다.
 */
@Component
@RequiredArgsConstructor
public class QueueChunkDeleter {

    private final PopupQueueEntryRepository entryRepository;

    public void deleteChunk(List<Long> ids) {
        entryRepository.deleteAllByIdInBatch(ids);
    }
}

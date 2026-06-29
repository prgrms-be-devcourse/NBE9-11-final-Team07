package com.back.popspot.global.queue.exception;

import com.back.popspot.global.exception.BusinessException;
import com.back.popspot.global.exception.ErrorCode;

// Phase 2에서 큐 용량 제한 로직 추가 시 사용. CB ignore-exceptions 에 등록되어
// 큐 풀 차단은 Redis 장애로 카운트되지 않는다.
public class QueueFullException extends BusinessException {

    public QueueFullException() {
        super(ErrorCode.QUEUE_TEMPORARILY_UNAVAILABLE);
    }
}

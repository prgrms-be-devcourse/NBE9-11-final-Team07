package com.back.popspot.global.queue.exception;

public class QueueCircuitOpenException extends RuntimeException {

    public QueueCircuitOpenException(Throwable cause) {
        super("waitingQueueRedis circuit breaker is OPEN", cause);
    }
}

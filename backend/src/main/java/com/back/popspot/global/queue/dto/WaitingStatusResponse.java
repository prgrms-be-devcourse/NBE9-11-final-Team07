package com.back.popspot.global.queue.dto;

public record WaitingStatusResponse(
    Status status,
    Integer rank,
    Integer estimatedSeconds,
    Integer pollIntervalSeconds
) {
    public enum Status {
        ADMITTED, WAITING, NOT_IN_QUEUE
    }

    public static WaitingStatusResponse admitted() {
        return new WaitingStatusResponse(Status.ADMITTED, null, null, null);
    }

    public static WaitingStatusResponse waiting(int rank, int estimatedSeconds, int pollIntervalSeconds) {
        return new WaitingStatusResponse(Status.WAITING, rank, estimatedSeconds, pollIntervalSeconds);
    }

    public static WaitingStatusResponse notInQueue() {
        return new WaitingStatusResponse(Status.NOT_IN_QUEUE, null, null, null);
    }
}

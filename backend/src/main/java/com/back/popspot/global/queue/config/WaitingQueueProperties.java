package com.back.popspot.global.queue.config;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "waiting-queue")
public record WaitingQueueProperties(
    int batchSize,
    long schedulerFixedRateMs,
    long proceedTtlSeconds,
    int pollIntervalSeconds,
    long queueTtlBufferSeconds
) {
    public Instant computeExpireAt(LocalDateTime reservationEndAt) {
        return reservationEndAt
            .plusSeconds(queueTtlBufferSeconds())
            .atZone(ZoneId.systemDefault())
            .toInstant();
    }
}

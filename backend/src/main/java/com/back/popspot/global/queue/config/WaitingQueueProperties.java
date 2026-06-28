package com.back.popspot.global.queue.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "waiting-queue")
public record WaitingQueueProperties(
    int batchSize,
    long schedulerFixedRateMs,
    long proceedTtlSeconds,
    int pollIntervalSeconds
) {
}

package com.back.popspot.global.queue.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "queue-recovery")
public record QueueRecoveryProperties(
    int maxAttempts,
    long pollIntervalSeconds,
    long lockAtMostForSeconds
) {
}

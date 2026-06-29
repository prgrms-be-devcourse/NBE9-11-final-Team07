package com.back.popspot.global.queue.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "queue-cleanup")
public record QueueCleanupProperties(int chunkSize) {
}

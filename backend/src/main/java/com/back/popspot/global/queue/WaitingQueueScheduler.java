package com.back.popspot.global.queue;

import java.util.Set;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class WaitingQueueScheduler {

	private final WaitingQueueRedisService queueService;
	private final WaitingQueueProperties properties;

	@Scheduled(fixedRateString = "${waiting-queue.scheduler-fixed-rate-ms}")
	public void admitWaiting() {
		Set<Long> popupIds = queueService.getActivePopupIds();
		for (Long popupId : popupIds) {
			queueService.admitBatch(popupId, properties.batchSize());
		}
	}
}

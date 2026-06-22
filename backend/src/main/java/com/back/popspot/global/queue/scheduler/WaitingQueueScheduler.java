package com.back.popspot.global.queue.scheduler;

import java.util.Set;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import com.back.popspot.global.queue.config.WaitingQueueProperties;
import com.back.popspot.global.queue.service.WaitingQueueRedisService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class WaitingQueueScheduler {

	private final WaitingQueueRedisService queueService;
	private final WaitingQueueProperties properties;

	@Scheduled(fixedRateString = "${waiting-queue.scheduler-fixed-rate-ms}")
	@SchedulerLock(name = "popup-admission-scheduler", lockAtMostFor = "10s", lockAtLeastFor = "450ms")
	public void admitWaiting() {
		LockAssert.assertLocked();
		Set<Long> popupIds = queueService.getActivePopupIds();
		for (Long popupId : popupIds) {
			queueService.admitBatch(popupId, properties.batchSize());
		}
	}

	@Scheduled(fixedRateString = "${waiting-queue.sweeper-fixed-rate-ms}")
	@SchedulerLock(name = "popup-queue-sweeper", lockAtMostFor = "PT1M", lockAtLeastFor = "5s")
	public void sweepWaiting() {
		LockAssert.assertLocked();
		Set<Long> popupIds = queueService.getActivePopupIds();
		for (Long popupId : popupIds) {
			queueService.sweepAbsentMembers(popupId);
		}
	}
}

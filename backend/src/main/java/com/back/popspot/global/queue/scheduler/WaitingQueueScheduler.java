package com.back.popspot.global.queue.scheduler;

import java.util.Set;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import com.back.popspot.global.queue.config.WaitingQueueProperties;
import com.back.popspot.global.queue.service.WaitingQueueRedisService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
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
			// 팝업 단위 격리 — 한 팝업의 실패가 뒤쪽 팝업의 admission 을 막지 않도록 한다.
			// (admitBatch 의 CB fallback 이 대부분을 삼키지만, afterCommit 예외는
			//  AOP 순서에 따라 fallback 밖으로 샐 수 있다)
			try {
				queueService.admitBatch(popupId, properties.batchSize());
			} catch (Exception e) {
				log.warn("admitBatch 실패 — 다음 팝업 계속 진행: popupId={}", popupId, e);
			}
		}
	}

}

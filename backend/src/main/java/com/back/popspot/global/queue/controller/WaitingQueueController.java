package com.back.popspot.global.queue.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.back.popspot.global.queue.config.WaitingQueueProperties;
import com.back.popspot.global.queue.service.WaitingQueueRedisService;
import com.back.popspot.global.queue.dto.WaitingStatusResponse;
import com.back.popspot.global.response.CommonApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class WaitingQueueController {

	private final WaitingQueueRedisService queueService;
	private final WaitingQueueProperties properties;

	@GetMapping("/popups/{popupId}/waiting-status")
	public ResponseEntity<CommonApiResponse<WaitingStatusResponse>> getWaitingStatus(
		@PathVariable Long popupId,
		@AuthenticationPrincipal Long userId
	) {
		String userIdStr = userId.toString();

		if (queueService.hasProceedPermission(popupId, userIdStr)) {
			return ResponseEntity.ok(CommonApiResponse.success(WaitingStatusResponse.admitted()));
		}

		Long rank0 = queueService.getQueueRank(popupId, userIdStr);
		if (rank0 != null) {
			int rank = (int)(rank0 + 1);
			return ResponseEntity.ok(CommonApiResponse.success(
				WaitingStatusResponse.waiting(rank, calculateEta(rank), properties.pollIntervalSeconds())
			));
		}

		return ResponseEntity.ok(CommonApiResponse.success(WaitingStatusResponse.notInQueue()));
	}

	private int calculateEta(int rank) {
		double rateSeconds = properties.schedulerFixedRateMs() / 1000.0;
		return (int) Math.ceil(Math.ceil((double) rank / properties.batchSize()) * rateSeconds);
	}
}

package com.back.popspot.domain.payment.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.back.popspot.domain.payment.service.PaymentService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PaymentCompensationScheduler {
	private static final long RETRY_DELAY_MILLIS = 60_000L;

	private final PaymentService paymentService;

	// 실패한 보상 취소를 주기적으로 재시도
	@Scheduled(fixedDelay = RETRY_DELAY_MILLIS)
	public void retryFailedCompensations() {
		paymentService.retryFailedCompensations();
	}

	// 실패한 일반 취소를 주기적으로 재시도
	@Scheduled(fixedDelay = RETRY_DELAY_MILLIS)
	public void retryFailedCancels() {
		paymentService.retryFailedCancels();
	}
}

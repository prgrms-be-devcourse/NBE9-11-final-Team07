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

	@Scheduled(fixedDelay = RETRY_DELAY_MILLIS)
	public void retryFailedCompensations() {
		paymentService.retryFailedCompensations();
	}
}

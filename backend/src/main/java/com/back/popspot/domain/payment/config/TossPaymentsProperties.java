package com.back.popspot.domain.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// 환경변수의 키를 읽기
@ConfigurationProperties(prefix = "toss-payments")
public record TossPaymentsProperties(
	String clientKey,
	String secretKey
) {
}
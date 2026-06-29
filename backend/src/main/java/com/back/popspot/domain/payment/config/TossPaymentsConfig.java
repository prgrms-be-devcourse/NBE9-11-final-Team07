package com.back.popspot.domain.payment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

// API 주소와 인증 설정
@Configuration
public class TossPaymentsConfig {

	// 토스페이먼츠 요청용 RestClient를 생성
	@Bean
	public RestClient tossPaymentsRestClient(TossPaymentsProperties properties) {
		return RestClient.builder()
			.baseUrl("https://api.tosspayments.com")
			.defaultHeaders(headers ->
				headers.setBasicAuth(properties.secretKey(), "")
			)
			.build();
	}
}

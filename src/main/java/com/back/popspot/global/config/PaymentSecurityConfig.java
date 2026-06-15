package com.back.popspot.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class PaymentSecurityConfig {

	@Bean
	@Order(1)
	public SecurityFilterChain paymentSecurityFilterChain(HttpSecurity http) throws Exception {
		return http
			.securityMatcher("/api/payments/confirm")
			.csrf(csrf -> csrf.disable())
			.authorizeHttpRequests(authorize -> authorize
				.anyRequest().permitAll()
			)
			.build();
	}
}

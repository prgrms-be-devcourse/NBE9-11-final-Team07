package com.back.popspot.global.security;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.back.popspot.global.security.jwt.JwtAuthenticationEntryPoint;
import com.back.popspot.global.security.jwt.JwtAuthenticationFilter;
import com.back.popspot.global.security.jwt.JwtTokenProvider;
import com.back.popspot.global.security.oauth.CustomOAuth2UserService;
import com.back.popspot.global.security.oauth.HttpCookieOAuth2AuthorizationRequestRepository;
import com.back.popspot.global.security.oauth.OAuth2FailureHandler;
import com.back.popspot.global.security.oauth.OAuth2SuccessHandler;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

	private final JwtTokenProvider jwtTokenProvider;
	private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
	private final CustomOAuth2UserService customOAuth2UserService;
	private final OAuth2SuccessHandler oAuth2SuccessHandler;
	private final OAuth2FailureHandler oAuth2FailureHandler;
	private final HttpCookieOAuth2AuthorizationRequestRepository authorizationRequestRepository;

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
			// 프론트엔드(localhost:3000)에서 쿠키 기반 인증 요청을 허용하기 위한 CORS
			.cors(cors -> cors.configurationSource(corsConfigurationSource()))
			// JWT 기반이므로 form login / http basic / csrf / 세션 모두 사용하지 않는다.
			.csrf(csrf -> csrf.disable())
			.headers(headers -> headers.frameOptions(frameOptions -> frameOptions.sameOrigin()))
			.formLogin(formLogin -> formLogin.disable())
			.httpBasic(httpBasic -> httpBasic.disable())
			.sessionManagement(session ->
				session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

			// 인가 규칙: 비회원은 GET /popups/**, GET /api/v1/goods/** 허용, 나머지는 인증 필요
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/h2-console/**").permitAll()
				.requestMatchers("/api/payments/confirm").permitAll()
				.requestMatchers("/actuator/prometheus", "/actuator/health").permitAll()
				.requestMatchers(HttpMethod.GET, "/popups/**").permitAll()
				.requestMatchers(HttpMethod.GET, "/api/v1/goods/**").permitAll()
				.requestMatchers(HttpMethod.GET, "/api/v1/popups/*/goods").permitAll()
				.requestMatchers("/oauth2/**", "/login/**", "/error").permitAll()
				.requestMatchers(HttpMethod.POST, "/auth/logout").permitAll()
				.anyRequest().authenticated())

			// 구글 OAuth2 로그인
			.oauth2Login(oauth2 -> oauth2
				.authorizationEndpoint(endpoint -> endpoint
					.authorizationRequestRepository(authorizationRequestRepository))
				.redirectionEndpoint(endpoint -> endpoint
					.baseUri("/oauth2/callback/*"))
				.userInfoEndpoint(endpoint -> endpoint
					.userService(customOAuth2UserService))
				.successHandler(oAuth2SuccessHandler)
				.failureHandler(oAuth2FailureHandler))

			// 인증 실패(비로그인) 시 401 JSON 응답
			.exceptionHandling(handler -> handler
				.authenticationEntryPoint(jwtAuthenticationEntryPoint))

			// 모든 요청 전 JWT 검증 필터 실행
			.addFilterBefore(
				new JwtAuthenticationFilter(jwtTokenProvider),
				UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}

	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(List.of("http://localhost:3000"));
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("*"));
		// 쿠키(access_token) 전송을 위해 필수
		config.setAllowCredentials(true);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);
		return source;
	}
}

package com.back.popspot.global.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericToStringSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;

@Configuration
public class RedisConfig {

	@Value("${spring.data.redis.host}")
	private String host;

	@Value("${spring.data.redis.port}")
	private int port;

	@Value("${spring.data.redis.timeout:1s}")
	private Duration commandTimeout;

	@Value("${spring.data.redis.connect-timeout:1s}")
	private Duration connectTimeout;

	// 팩토리를 직접 만들면 Boot 자동구성의 spring.data.redis.timeout 이 적용되지 않으므로 여기서 넣는다.
	// 미설정 시 Lettuce 기본 command timeout 은 60s 라, 응답이 끊기면 요청 스레드가 60초 묶인다.
	@Bean
	public RedisConnectionFactory redisConnectionFactory() {
		LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
			.commandTimeout(commandTimeout)
			.clientOptions(ClientOptions.builder()
				.socketOptions(SocketOptions.builder().connectTimeout(connectTimeout).build())
				.build())
			.build();
		return new LettuceConnectionFactory(new RedisStandaloneConfiguration(host, port), clientConfig);
	}

	@Bean
	public RedisTemplate<String, Long> redisTemplate() {
		RedisTemplate<String, Long> template = new RedisTemplate<>();
		template.setConnectionFactory(redisConnectionFactory());
		template.setKeySerializer(new StringRedisSerializer());
		template.setValueSerializer(new GenericToStringSerializer<>(Long.class));
		return template;
	}
}
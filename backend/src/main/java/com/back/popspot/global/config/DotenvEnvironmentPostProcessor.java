package com.back.popspot.global.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

/**
 * 프로젝트 루트의 {@code .env} 파일을 읽어 Spring Environment 에 프로퍼티로 주입한다.
 *
 * <p>spring-dotenv 라이브러리는 Spring Boot 4 와 호환되지 않아(ConfigurableBootstrapContext 패키지 이동으로
 * environmentPrepared 훅이 더 이상 호출되지 않음) 직접 구현한다.
 *
 * <p>우선순위는 실제 OS 환경변수보다 낮게 둔다. 즉 운영 환경에서 진짜 환경변수가 있으면 그것이 이기고,
 * 없을 때만 {@code .env} 값이 사용된다.
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {

	private static final String PROPERTY_SOURCE_NAME = "dotenv";
	private static final String DOTENV_FILE = ".env";

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		Path envFile = Path.of(DOTENV_FILE);
		if (!Files.exists(envFile)) {
			return;
		}

		Map<String, Object> values = parse(envFile);
		if (values.isEmpty()) {
			return;
		}

		MapPropertySource propertySource = new MapPropertySource(PROPERTY_SOURCE_NAME, values);

		// 실제 OS 환경변수보다는 뒤(낮은 우선순위)에 둬서 운영 환경변수가 .env 를 덮어쓸 수 있게 한다.
		String systemEnvName = StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME;
		if (environment.getPropertySources().contains(systemEnvName)) {
			environment.getPropertySources().addAfter(systemEnvName, propertySource);
		} else {
			environment.getPropertySources().addLast(propertySource);
		}
	}

	private Map<String, Object> parse(Path envFile) {
		Map<String, Object> values = new LinkedHashMap<>();
		List<String> lines;
		try {
			lines = Files.readAllLines(envFile, StandardCharsets.UTF_8);
		} catch (IOException e) {
			// 부팅 매우 초기 단계라 로깅이 아직 준비되지 않았으므로 stderr 로만 알린다.
			System.err.println("[dotenv] .env 파일을 읽지 못했습니다: " + e.getMessage());
			return values;
		}

		for (String raw : lines) {
			String line = raw.strip();
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}
			if (line.startsWith("export ")) {
				line = line.substring("export ".length()).strip();
			}

			int eq = line.indexOf('=');
			if (eq <= 0) {
				continue;
			}

			String key = line.substring(0, eq).strip();
			String value = stripQuotes(line.substring(eq + 1).strip());
			values.put(key, value);
		}
		return values;
	}

	private String stripQuotes(String value) {
		if (value.length() >= 2
				&& ((value.startsWith("\"") && value.endsWith("\""))
				|| (value.startsWith("'") && value.endsWith("'")))) {
			return value.substring(1, value.length() - 1);
		}
		return value;
	}
}

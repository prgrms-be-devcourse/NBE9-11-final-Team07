package com.back.popspot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class PopspotApplication {

	public static void main(String[] args) {
		SpringApplication.run(PopspotApplication.class, args);
	}

}

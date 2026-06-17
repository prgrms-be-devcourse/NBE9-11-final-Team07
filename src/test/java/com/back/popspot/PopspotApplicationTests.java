package com.back.popspot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import org.springframework.test.context.ActiveProfiles;

import com.back.popspot.global.s3.S3Service;

@SpringBootTest
@ActiveProfiles("test")
class PopspotApplicationTests {

	@MockitoBean
	private ClientRegistrationRepository clientRegistrationRepository;

	@MockitoBean
	private S3Client s3Client;

	@MockitoBean
	private S3Presigner s3Presigner;

	@MockitoBean
	private S3Service s3Service;

	@Test
	void contextLoads() {
	}

}

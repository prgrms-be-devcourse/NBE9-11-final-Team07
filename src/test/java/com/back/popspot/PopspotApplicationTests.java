package com.back.popspot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.amazonaws.services.s3.AmazonS3;

@SpringBootTest
class PopspotApplicationTests {

	@MockitoBean
	private ClientRegistrationRepository clientRegistrationRepository;

	@MockitoBean
	private AmazonS3 amazonS3;

	@Test
	void contextLoads() {
	}

}

package com.back.popspot.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.amazonaws.services.s3.AmazonS3;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public abstract class IntegrationTestSupport {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    // OAuth2 Client 자동 구성이 실제 등록 정보를 요구하므로 모킹 처리
    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    // 테스트에서 실제 AWS 연결을 막기 위해 모킹
    @MockitoBean
    private AmazonS3 amazonS3;
}

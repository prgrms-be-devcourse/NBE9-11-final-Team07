package com.back.popspot.global.s3;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@EnableConfigurationProperties(S3Properties.class)
public class S3Config {

    @Bean
    public S3Client s3Client(S3Properties props) {
        return S3Client.builder()
            .region(Region.of(props.getRegion()))
            .credentialsProvider(credentialsProvider(props))
            .build();
    }

    @Bean
    public S3Presigner s3Presigner(S3Properties props) {
        return S3Presigner.builder()
            .region(Region.of(props.getRegion()))
            .credentialsProvider(credentialsProvider(props))
            .build();
    }

    /**
     * access-key/secret-key 가 설정되어 있으면 정적 자격증명을 사용하고,
     * 비어 있으면 기본 자격증명 체인(IAM Role, ~/.aws, 실제 환경변수 등)을 사용한다.
     */
    private AwsCredentialsProvider credentialsProvider(S3Properties props) {
        if (props.getAccessKey() != null && !props.getAccessKey().isBlank()
                && props.getSecretKey() != null && !props.getSecretKey().isBlank()) {
            return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey()));
        }
        return DefaultCredentialsProvider.create();
    }
}

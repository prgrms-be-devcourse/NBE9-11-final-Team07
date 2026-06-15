package com.back.popspot.global.s3;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;

@Configuration
@EnableConfigurationProperties(S3Properties.class)
public class S3Config {

    @Bean
    public AmazonS3 amazonS3(S3Properties props) {
        BasicAWSCredentials credentials = new BasicAWSCredentials(props.getAccessKey(), props.getSecretKey());
        return AmazonS3ClientBuilder.standard()
            .withRegion(props.getRegion())
            .withCredentials(new AWSStaticCredentialsProvider(credentials))
            .build();
    }
}

package com.back.popspot.global.s3;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "aws.s3")
public class S3Properties {

	private String region;
	private String bucket;
	private String accessKey;
	private String secretKey;
	private long presignedUrlExpiration = 600;
}

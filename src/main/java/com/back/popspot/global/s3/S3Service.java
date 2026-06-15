package com.back.popspot.global.s3;

import java.util.Date;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class S3Service {

    private static final String TEMP_PREFIX = "temp/";

    private final AmazonS3 amazonS3;
    private final S3Properties s3Properties;

    public String buildTempKey(String fileName) {
        String uuid = UUID.randomUUID().toString();
        String extension = extractExtension(fileName);
        String suffix = extension.isEmpty() ? uuid : uuid + "." + extension;
        return TEMP_PREFIX + suffix;
    }

    public void move(String sourceKey, String destKey) {
        String bucket = s3Properties.getBucket();
        amazonS3.copyObject(bucket, sourceKey, bucket, destKey);
        amazonS3.deleteObject(bucket, sourceKey);
    }

    public boolean isTempKey(String key) {
        return key != null && key.startsWith(TEMP_PREFIX);
    }

    public String extractFileName(String key) {
        return key.substring(key.lastIndexOf('/') + 1);
    }

    public String generatePresignedPutUrl(String key) {
        Date expiration = new Date(System.currentTimeMillis() + s3Properties.getPresignedUrlExpiration() * 1000);
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(s3Properties.getBucket(), key)
            .withMethod(HttpMethod.PUT)
            .withExpiration(expiration);
        return amazonS3.generatePresignedUrl(request).toString();
    }

    private String extractExtension(String fileName) {
        int idx = fileName.lastIndexOf('.');
        return (idx > 0 && idx < fileName.length() - 1) ? fileName.substring(idx + 1) : "";
    }
}

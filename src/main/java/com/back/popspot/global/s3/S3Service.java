package com.back.popspot.global.s3;

import java.time.Duration;
import java.util.UUID;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
@RequiredArgsConstructor
public class S3Service {

    private static final String TEMP_PREFIX = "temp/";

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Properties s3Properties;

    public String buildTempKey(String fileName) {
        String uuid = UUID.randomUUID().toString();
        String extension = extractExtension(fileName);
        String suffix = extension.isEmpty() ? uuid : uuid + "." + extension;
        return TEMP_PREFIX + suffix;
    }

    public void move(String sourceKey, String destKey) {
        String bucket = s3Properties.getBucket();
        s3Client.copyObject(CopyObjectRequest.builder()
            .sourceBucket(bucket)
            .sourceKey(sourceKey)
            .destinationBucket(bucket)
            .destinationKey(destKey)
            .build());
        s3Client.deleteObject(DeleteObjectRequest.builder()
            .bucket(bucket)
            .key(sourceKey)
            .build());
    }

    public void delete(String key) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
            .bucket(s3Properties.getBucket())
            .key(key)
            .build());
    }

    public boolean isTempKey(String key) {
        return key != null && key.startsWith(TEMP_PREFIX);
    }

    public String extractFileName(String key) {
        return key.substring(key.lastIndexOf('/') + 1);
    }

    public String generatePresignedPutUrl(String key) {
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
            .signatureDuration(Duration.ofSeconds(s3Properties.getPresignedUrlExpiration()))
            .putObjectRequest(PutObjectRequest.builder()
                .bucket(s3Properties.getBucket())
                .key(key)
                .build())
            .build();
        return s3Presigner.presignPutObject(presignRequest).url().toExternalForm();
    }

    private String extractExtension(String fileName) {
        int idx = fileName.lastIndexOf('.');
        return (idx > 0 && idx < fileName.length() - 1) ? fileName.substring(idx + 1) : "";
    }
}

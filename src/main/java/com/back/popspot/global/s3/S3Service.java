package com.back.popspot.global.s3;

import java.util.Date;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.back.popspot.domain.goods.entity.GoodsImageType;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class S3Service {

    private static final String TEMP_PREFIX = "temp/goods/";

    private final AmazonS3 amazonS3;
    private final S3Properties s3Properties;

    public String buildTempImageKey(String fileName) {
        String uuid = UUID.randomUUID().toString();
        String extension = extractExtension(fileName);
        String suffix = extension.isEmpty() ? uuid : uuid + "." + extension;
        return TEMP_PREFIX + suffix;
    }

    public String moveToFinalPath(String tempKey, Long goodsId, GoodsImageType imageType) {
        String fileName = tempKey.substring(TEMP_PREFIX.length());
        String finalKey = String.format("goods/%d/%s/%s", goodsId, imageType.name().toLowerCase(), fileName);

        String bucket = s3Properties.getBucket();
        amazonS3.copyObject(bucket, tempKey, bucket, finalKey);
        amazonS3.deleteObject(bucket, tempKey);

        return finalKey;
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

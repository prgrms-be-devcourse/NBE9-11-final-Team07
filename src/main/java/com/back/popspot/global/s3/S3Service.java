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

    private final AmazonS3 amazonS3;
    private final S3Properties s3Properties;

    public String generatePresignedPutUrl(String key) {
        Date expiration = new Date(System.currentTimeMillis() + s3Properties.getPresignedUrlExpiration() * 1000);
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(s3Properties.getBucket(), key)
            .withMethod(HttpMethod.PUT)
            .withExpiration(expiration);
        return amazonS3.generatePresignedUrl(request).toString();
    }

    public String buildGoodsImageKey(Long goodsId, GoodsImageType imageType, String fileName) {
        String uuid = UUID.randomUUID().toString();
        String extension = extractExtension(fileName);
        String suffix = extension.isEmpty() ? uuid : uuid + "." + extension;
        return String.format("goods/%d/%s/%s", goodsId, imageType.name().toLowerCase(), suffix);
    }

    private String extractExtension(String fileName) {
        int idx = fileName.lastIndexOf('.');
        return (idx > 0 && idx < fileName.length() - 1) ? fileName.substring(idx + 1) : "";
    }
}

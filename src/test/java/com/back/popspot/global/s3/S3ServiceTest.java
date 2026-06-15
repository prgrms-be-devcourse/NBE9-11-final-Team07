package com.back.popspot.global.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.amazonaws.services.s3.AmazonS3;

@DisplayName("S3Service")
@ExtendWith(MockitoExtension.class)
class S3ServiceTest {

    @Mock
    private AmazonS3 amazonS3;

    @Mock
    private S3Properties s3Properties;

    @InjectMocks
    private S3Service s3Service;

    @Test
    @DisplayName("move는 copyObject 후 deleteObject를 동일 버킷의 올바른 키로 호출한다")
    void move() {
        when(s3Properties.getBucket()).thenReturn("test-bucket");
        String src = "temp/uuid.jpg";
        String dest = "goods/1/product/uuid.jpg";

        s3Service.move(src, dest);

        verify(amazonS3).copyObject("test-bucket", src, "test-bucket", dest);
        verify(amazonS3).deleteObject("test-bucket", src);
    }

    @Test
    @DisplayName("temp/ 로 시작하는 키는 temp 키다")
    void isTempKey_tempPrefix_returnsTrue() {
        assertThat(s3Service.isTempKey("temp/uuid.jpg")).isTrue();
        assertThat(s3Service.isTempKey("temp/nested/uuid.jpg")).isTrue();
    }

    @Test
    @DisplayName("temp/ 로 시작하지 않는 키는 temp 키가 아니다")
    void isTempKey_nonTempPrefix_returnsFalse() {
        assertThat(s3Service.isTempKey("goods/1/product/uuid.jpg")).isFalse();
        assertThat(s3Service.isTempKey("popups/1/uuid.jpg")).isFalse();
    }

    @Test
    @DisplayName("null은 temp 키가 아니다")
    void isTempKey_null_returnsFalse() {
        assertThat(s3Service.isTempKey(null)).isFalse();
    }

    @Test
    @DisplayName("extractFileName은 마지막 / 이후 파일명을 반환한다")
    void extractFileName() {
        assertThat(s3Service.extractFileName("temp/uuid.jpg")).isEqualTo("uuid.jpg");
        assertThat(s3Service.extractFileName("goods/1/product/uuid.jpg")).isEqualTo("uuid.jpg");
        assertThat(s3Service.extractFileName("only-filename.jpg")).isEqualTo("only-filename.jpg");
    }
}

package com.back.popspot.global.s3;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ImageDomain")
class ImageDomainTest {

    @Test
    @DisplayName("GOODS.finalKey(typeSegment 포함)는 goods/{id}/{type}/{file} 형식이다")
    void goods_finalKey_withTypeSegment() {
        assertThat(ImageDomain.GOODS.finalKey(1L, "product", "a.png"))
            .isEqualTo("goods/1/product/a.png");
        assertThat(ImageDomain.GOODS.finalKey(42L, "detail", "b.jpg"))
            .isEqualTo("goods/42/detail/b.jpg");
    }

    @Test
    @DisplayName("POPUP.finalKey(typeSegment 없음)는 popups/{id}/{file} 형식이다")
    void popup_finalKey_withoutTypeSegment() {
        assertThat(ImageDomain.POPUP.finalKey(1L, "a.png"))
            .isEqualTo("popups/1/a.png");
        assertThat(ImageDomain.POPUP.finalKey(7L, "banner.jpg"))
            .isEqualTo("popups/7/banner.jpg");
    }
}

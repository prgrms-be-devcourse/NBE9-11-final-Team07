package com.back.popspot.domain.goods.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import com.back.popspot.domain.goods.dto.GoodsImagePresignRequest;
import com.back.popspot.domain.goods.dto.GoodsRegisterRequest;
import com.back.popspot.domain.goods.entity.Goods;
import com.back.popspot.domain.goods.entity.GoodsImage;
import com.back.popspot.domain.goods.entity.GoodsImageType;
import com.back.popspot.domain.goods.repository.GoodsImageRepository;
import com.back.popspot.domain.goods.repository.GoodsRepository;
import com.back.popspot.domain.popupStore.entity.PopupFeeType;
import com.back.popspot.domain.popupStore.entity.PopupStore;
import com.back.popspot.domain.popupStore.repository.PopupStoreRepository;
import com.back.popspot.domain.user.entity.User;
import com.back.popspot.domain.user.repository.UserRepository;
import com.back.popspot.global.s3.S3Service;
import com.back.popspot.support.IntegrationTestSupport;

@DisplayName("굿즈 이미지 API")
class GoodsImageControllerTest extends IntegrationTestSupport {

    @MockitoBean
    private S3Service s3Service;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PopupStoreRepository popupStoreRepository;

    @Autowired
    private GoodsRepository goodsRepository;

    @Autowired
    private GoodsImageRepository goodsImageRepository;

    private Long popupStoreId;
    private Long goodsId;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.create("host@test.com", "호스트"));

        PopupStore popupStore = new PopupStore();
        ReflectionTestUtils.setField(popupStore, "user", user);
        ReflectionTestUtils.setField(popupStore, "title", "테스트 팝업");
        ReflectionTestUtils.setField(popupStore, "location", "서울");
        ReflectionTestUtils.setField(popupStore, "feeType", PopupFeeType.FREE);
        ReflectionTestUtils.setField(popupStore, "reservationStartAt", LocalDateTime.now().plusDays(1));
        ReflectionTestUtils.setField(popupStore, "reservationEndAt", LocalDateTime.now().plusDays(30));
        popupStoreId = popupStoreRepository.save(popupStore).getId();

        Goods goods = Goods.register(popupStore, "테스트 굿즈", 10000, 50, "테스트 상세");
        goodsId = goodsRepository.save(goods).getId();
    }

    @Test
    @WithMockUser
    @DisplayName("presigned URL 발급에 성공한다")
    void generatePresignedUrls() throws Exception {
        String imageKey = "goods/" + goodsId + "/product/test-uuid.jpg";
        String presignedUrl = "https://bucket.s3.amazonaws.com/" + imageKey + "?signed=true";

        given(s3Service.buildGoodsImageKey(eq(goodsId), eq(GoodsImageType.PRODUCT), eq("test.jpg")))
            .willReturn(imageKey);
        given(s3Service.generatePresignedPutUrl(imageKey))
            .willReturn(presignedUrl);

        GoodsImagePresignRequest request = new GoodsImagePresignRequest(
            GoodsImageType.PRODUCT, List.of("test.jpg")
        );

        mockMvc.perform(post("/host/goods/{goodsId}/images", goodsId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].imageKey").value(imageKey))
            .andExpect(jsonPath("$.data[0].presignedUrl").value(presignedUrl));
    }

    @Test
    @WithMockUser
    @DisplayName("존재하지 않는 goodsId로 presigned URL 발급 시 404를 반환한다")
    void generatePresignedUrls_goodsNotFound() throws Exception {
        GoodsImagePresignRequest request = new GoodsImagePresignRequest(
            GoodsImageType.PRODUCT, List.of("test.jpg")
        );

        mockMvc.perform(post("/host/goods/{goodsId}/images", 999L)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("GOODS_NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("굿즈를 찾을 수 없습니다."));
    }

    @Test
    @WithMockUser
    @DisplayName("굿즈 등록 시 imageKey를 포함하면 GoodsImage가 DB에 저장된다")
    void registerGoods_withImages_savesGoodsImage() throws Exception {
        GoodsRegisterRequest request = new GoodsRegisterRequest(
            "신상 굿즈", 15000, 30, "상세 설명",
            List.of(
                new GoodsRegisterRequest.ImageKeyEntry("goods/product/uuid-1.jpg", GoodsImageType.PRODUCT),
                new GoodsRegisterRequest.ImageKeyEntry("goods/detail/uuid-2.jpg", GoodsImageType.DETAIL)
            )
        );

        mockMvc.perform(post("/host/popups/{popupStoreId}/goods", popupStoreId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("SUCCESS"));

        List<GoodsImage> savedImages = goodsImageRepository.findAll();
        assertThat(savedImages).hasSize(2);
        assertThat(savedImages)
            .extracting(GoodsImage::getImageType)
            .containsExactlyInAnyOrder(GoodsImageType.PRODUCT, GoodsImageType.DETAIL);
        assertThat(savedImages)
            .extracting(GoodsImage::getImageKey)
            .containsExactlyInAnyOrder("goods/product/uuid-1.jpg", "goods/detail/uuid-2.jpg");
    }
}

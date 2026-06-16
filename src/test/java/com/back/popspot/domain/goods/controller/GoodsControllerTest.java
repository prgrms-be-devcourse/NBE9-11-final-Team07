package com.back.popspot.domain.goods.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.back.popspot.domain.goods.dto.GoodsListResponse;
import com.back.popspot.domain.goods.dto.GoodsRegisterRequest;
import com.back.popspot.domain.goods.dto.GoodsRegisterResponse;
import com.back.popspot.domain.goods.dto.GoodsUpdateRequest;
import com.back.popspot.domain.goods.dto.GoodsUpdateResponse;
import com.back.popspot.domain.goods.entity.GoodsImageType;
import com.back.popspot.domain.goods.service.GoodsService;
import com.back.popspot.global.exception.BusinessException;
import com.back.popspot.global.exception.ErrorCode;
import com.back.popspot.support.IntegrationTestSupport;

@DisplayName("굿즈 API")
class GoodsControllerTest extends IntegrationTestSupport {

    @MockitoBean
    private GoodsService goodsService;

    @Test
    @WithMockUser
    @DisplayName("굿즈를 등록하면 201과 등록된 굿즈 정보를 반환한다")
    void registerGoods() throws Exception {
        Long popupStoreId = 1L;
        GoodsRegisterRequest request = new GoodsRegisterRequest("한정판 포스터", 15000, 30, "고화질 포스터", null);
        GoodsRegisterResponse response = new GoodsRegisterResponse(1L, popupStoreId, "한정판 포스터", 15000, 30, "고화질 포스터");

        given(goodsService.registerGoods(eq(popupStoreId), any())).willReturn(response);

        mockMvc.perform(post("/host/popups/{popupStoreId}/goods", popupStoreId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("굿즈가 등록되었습니다."))
            .andExpect(jsonPath("$.data.id").value(1))
            .andExpect(jsonPath("$.data.popupStoreId").value(popupStoreId))
            .andExpect(jsonPath("$.data.name").value("한정판 포스터"))
            .andExpect(jsonPath("$.data.price").value(15000))
            .andExpect(jsonPath("$.data.stock").value(30));
    }

    @Test
    @WithMockUser
    @DisplayName("대표이미지가 없으면 400을 반환한다")
    void registerGoods_productImageRequired() throws Exception {
        Long popupStoreId = 1L;
        GoodsRegisterRequest request = new GoodsRegisterRequest("한정판 포스터", 15000, 30, null, null);

        given(goodsService.registerGoods(eq(popupStoreId), any()))
            .willThrow(new BusinessException(ErrorCode.GOODS_PRODUCT_IMAGE_REQUIRED));

        mockMvc.perform(post("/host/popups/{popupStoreId}/goods", popupStoreId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("GOODS_PRODUCT_IMAGE_REQUIRED"))
            .andExpect(jsonPath("$.message").value("대표이미지는 필수입니다."));
    }

    @Test
    @WithMockUser
    @DisplayName("상세이미지가 없으면 400을 반환한다")
    void registerGoods_detailImageRequired() throws Exception {
        Long popupStoreId = 1L;
        GoodsRegisterRequest request = new GoodsRegisterRequest("한정판 포스터", 15000, 30, null, null);

        given(goodsService.registerGoods(eq(popupStoreId), any()))
            .willThrow(new BusinessException(ErrorCode.GOODS_DETAIL_IMAGE_REQUIRED));

        mockMvc.perform(post("/host/popups/{popupStoreId}/goods", popupStoreId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("GOODS_DETAIL_IMAGE_REQUIRED"))
            .andExpect(jsonPath("$.message").value("상세이미지는 필수입니다."));
    }

    @Test
    @WithMockUser
    @DisplayName("존재하지 않는 팝업스토어에 굿즈를 등록하면 404를 반환한다")
    void registerGoods_popupStoreNotFound() throws Exception {
        Long nonExistentId = 999L;
        GoodsRegisterRequest request = new GoodsRegisterRequest("한정판 포스터", 15000, 30, null, null);

        given(goodsService.registerGoods(eq(nonExistentId), any()))
            .willThrow(new BusinessException(ErrorCode.POPUP_STORE_NOT_FOUND));

        mockMvc.perform(post("/host/popups/{popupStoreId}/goods", nonExistentId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("POPUP_STORE_NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("팝업스토어를 찾을 수 없습니다."));
    }

    @Test
    @WithMockUser
    @DisplayName("상품명이 공백이면 400을 반환한다")
    void registerGoods_blankName() throws Exception {
        GoodsRegisterRequest request = new GoodsRegisterRequest("   ", 15000, 30, null, null);

        mockMvc.perform(post("/host/popups/{popupStoreId}/goods", 1L)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    @WithMockUser
    @DisplayName("가격이 null이면 400을 반환한다")
    void registerGoods_nullPrice() throws Exception {
        GoodsRegisterRequest request = new GoodsRegisterRequest("한정판 포스터", null, 30, null, null);

        mockMvc.perform(post("/host/popups/{popupStoreId}/goods", 1L)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    @WithMockUser
    @DisplayName("수량이 음수면 400을 반환한다")
    void registerGoods_negativeStock() throws Exception {
        GoodsRegisterRequest request = new GoodsRegisterRequest("한정판 포스터", 15000, -1, null, null);

        mockMvc.perform(post("/host/popups/{popupStoreId}/goods", 1L)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    @WithMockUser
    @DisplayName("굿즈 목록을 조회하면 200과 굿즈 목록을 반환한다")
    void getGoodsList() throws Exception {
        Long popupStoreId = 1L;
        List<GoodsListResponse> response = List.of(
            new GoodsListResponse(1L, "한정판 포스터", 15000, 30,
                "https://s3.example.com/product1.jpg", "https://s3.example.com/detail1.jpg"),
            new GoodsListResponse(2L, "에코백", 25000, 50,
                "https://s3.example.com/product2.jpg", null)
        );

        given(goodsService.getGoodsList(eq(popupStoreId))).willReturn(response);

        mockMvc.perform(get("/host/popups/{popupStoreId}/goods", popupStoreId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[0].id").value(1))
            .andExpect(jsonPath("$.data[0].name").value("한정판 포스터"))
            .andExpect(jsonPath("$.data[0].price").value(15000))
            .andExpect(jsonPath("$.data[0].stock").value(30))
            .andExpect(jsonPath("$.data[0].productImageUrl").value("https://s3.example.com/product1.jpg"))
            .andExpect(jsonPath("$.data[0].detailImageUrl").value("https://s3.example.com/detail1.jpg"));
    }

    @Test
    @WithMockUser
    @DisplayName("굿즈가 없으면 빈 배열을 반환한다")
    void getGoodsList_empty() throws Exception {
        Long popupStoreId = 1L;

        given(goodsService.getGoodsList(eq(popupStoreId))).willReturn(List.of());

        mockMvc.perform(get("/host/popups/{popupStoreId}/goods", popupStoreId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @WithMockUser
    @DisplayName("굿즈를 수정하면 200과 수정된 굿즈 정보를 반환한다")
    void updateGoods() throws Exception {
        Long goodsId = 1L;
        GoodsUpdateRequest request = new GoodsUpdateRequest("수정된 포스터", 20000, 50, "수정된 설명", null);
        GoodsUpdateResponse response = new GoodsUpdateResponse(goodsId, "수정된 포스터", 20000, 50, "수정된 설명");

        given(goodsService.updateGoods(eq(goodsId), any())).willReturn(response);

        mockMvc.perform(patch("/host/goods/{goodsId}", goodsId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.id").value(goodsId))
            .andExpect(jsonPath("$.data.name").value("수정된 포스터"))
            .andExpect(jsonPath("$.data.price").value(20000))
            .andExpect(jsonPath("$.data.stock").value(50));
    }

    @Test
    @WithMockUser
    @DisplayName("일부 필드만 전달하면 200과 수정된 굿즈 정보를 반환한다")
    void updateGoods_partial() throws Exception {
        Long goodsId = 1L;
        GoodsUpdateRequest request = new GoodsUpdateRequest(null, 20000, null, null, null);
        GoodsUpdateResponse response = new GoodsUpdateResponse(goodsId, "기존 이름", 20000, 30, "기존 설명");

        given(goodsService.updateGoods(eq(goodsId), any())).willReturn(response);

        mockMvc.perform(patch("/host/goods/{goodsId}", goodsId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.price").value(20000))
            .andExpect(jsonPath("$.data.name").value("기존 이름"));
    }

    @Test
    @WithMockUser
    @DisplayName("이미지 키를 포함해 수정하면 200과 수정된 굿즈 정보를 반환한다")
    void updateGoods_withImages() throws Exception {
        Long goodsId = 1L;
        GoodsUpdateRequest request = new GoodsUpdateRequest(null, null, null, null,
            List.of(
                new GoodsUpdateRequest.ImageKeyEntry("temp/new-product.jpg", GoodsImageType.PRODUCT),
                new GoodsUpdateRequest.ImageKeyEntry("temp/new-detail.jpg", GoodsImageType.DETAIL)
            ));
        GoodsUpdateResponse response = new GoodsUpdateResponse(goodsId, "원본 굿즈", 10000, 50, "원본 설명");

        given(goodsService.updateGoods(eq(goodsId), any())).willReturn(response);

        mockMvc.perform(patch("/host/goods/{goodsId}", goodsId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.id").value(goodsId));
    }

    @Test
    @WithMockUser
    @DisplayName("존재하지 않는 goodsId 수정 시 404를 반환한다")
    void updateGoods_goodsNotFound() throws Exception {
        Long nonExistentId = 999L;
        GoodsUpdateRequest request = new GoodsUpdateRequest("수정된 포스터", 20000, 50, null, null);

        given(goodsService.updateGoods(eq(nonExistentId), any()))
            .willThrow(new BusinessException(ErrorCode.GOODS_NOT_FOUND));

        mockMvc.perform(patch("/host/goods/{goodsId}", nonExistentId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("GOODS_NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("굿즈를 찾을 수 없습니다."));
    }

    @Test
    @WithMockUser
    @DisplayName("이미지 수정 시 대표이미지가 없으면 400을 반환한다")
    void updateGoods_productImageRequired() throws Exception {
        Long goodsId = 1L;
        GoodsUpdateRequest request = new GoodsUpdateRequest(null, null, null, null,
            List.of(new GoodsUpdateRequest.ImageKeyEntry("temp/new-detail.jpg", GoodsImageType.DETAIL)));

        given(goodsService.updateGoods(eq(goodsId), any()))
            .willThrow(new BusinessException(ErrorCode.GOODS_PRODUCT_IMAGE_REQUIRED));

        mockMvc.perform(patch("/host/goods/{goodsId}", goodsId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("GOODS_PRODUCT_IMAGE_REQUIRED"))
            .andExpect(jsonPath("$.message").value("대표이미지는 필수입니다."));
    }

    @Test
    @WithMockUser
    @DisplayName("이미지 수정 시 상세이미지가 없으면 400을 반환한다")
    void updateGoods_detailImageRequired() throws Exception {
        Long goodsId = 1L;
        GoodsUpdateRequest request = new GoodsUpdateRequest(null, null, null, null,
            List.of(new GoodsUpdateRequest.ImageKeyEntry("temp/new-product.jpg", GoodsImageType.PRODUCT)));

        given(goodsService.updateGoods(eq(goodsId), any()))
            .willThrow(new BusinessException(ErrorCode.GOODS_DETAIL_IMAGE_REQUIRED));

        mockMvc.perform(patch("/host/goods/{goodsId}", goodsId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("GOODS_DETAIL_IMAGE_REQUIRED"))
            .andExpect(jsonPath("$.message").value("상세이미지는 필수입니다."));
    }

    @Test
    @WithMockUser
    @DisplayName("동일한 imageType을 중복 전달하면 400을 반환한다")
    void updateGoods_duplicateImageType() throws Exception {
        Long goodsId = 1L;
        GoodsUpdateRequest request = new GoodsUpdateRequest(null, null, null, null,
            List.of(
                new GoodsUpdateRequest.ImageKeyEntry("temp/img1.jpg", GoodsImageType.PRODUCT),
                new GoodsUpdateRequest.ImageKeyEntry("temp/img2.jpg", GoodsImageType.PRODUCT)
            ));

        given(goodsService.updateGoods(eq(goodsId), any()))
            .willThrow(new BusinessException(ErrorCode.INVALID_INPUT_VALUE));

        mockMvc.perform(patch("/host/goods/{goodsId}", goodsId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    @WithMockUser
    @DisplayName("temp 경로가 아닌 키 전달 시 400을 반환한다")
    void updateGoods_invalidTempKey() throws Exception {
        Long goodsId = 1L;
        GoodsUpdateRequest request = new GoodsUpdateRequest(null, null, null, null,
            List.of(new GoodsUpdateRequest.ImageKeyEntry("goods/already-final.jpg", GoodsImageType.PRODUCT)));

        given(goodsService.updateGoods(eq(goodsId), any()))
            .willThrow(new BusinessException(ErrorCode.INVALID_IMAGE_TEMP_KEY));

        mockMvc.perform(patch("/host/goods/{goodsId}", goodsId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_IMAGE_TEMP_KEY"));
    }

    @Test
    @WithMockUser
    @DisplayName("해당 타입의 기존 이미지가 없으면 404를 반환한다")
    void updateGoods_imageNotFound() throws Exception {
        Long goodsId = 1L;
        GoodsUpdateRequest request = new GoodsUpdateRequest(null, null, null, null,
            List.of(new GoodsUpdateRequest.ImageKeyEntry("temp/detail.jpg", GoodsImageType.DETAIL)));

        given(goodsService.updateGoods(eq(goodsId), any()))
            .willThrow(new BusinessException(ErrorCode.GOODS_IMAGE_NOT_FOUND));

        mockMvc.perform(patch("/host/goods/{goodsId}", goodsId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("GOODS_IMAGE_NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("굿즈 이미지를 찾을 수 없습니다."));
    }

    @Test
    @WithMockUser
    @DisplayName("가격이 음수면 400을 반환한다")
    void updateGoods_negativePrice() throws Exception {
        GoodsUpdateRequest request = new GoodsUpdateRequest(null, -1, null, null, null);

        mockMvc.perform(patch("/host/goods/{goodsId}", 1L)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    @WithMockUser
    @DisplayName("수량이 음수면 400을 반환한다")
    void updateGoods_negativeStock() throws Exception {
        GoodsUpdateRequest request = new GoodsUpdateRequest(null, null, -1, null, null);

        mockMvc.perform(patch("/host/goods/{goodsId}", 1L)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    @WithMockUser
    @DisplayName("굿즈를 삭제하면 200과 성공 메시지를 반환한다")
    void deleteGoods() throws Exception {
        Long goodsId = 1L;

        willDoNothing().given(goodsService).deleteGoods(goodsId);

        mockMvc.perform(delete("/host/goods/{goodsId}", goodsId)
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("굿즈가 삭제되었습니다."))
            .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @WithMockUser
    @DisplayName("존재하지 않는 굿즈를 삭제하면 404를 반환한다")
    void deleteGoods_goodsNotFound() throws Exception {
        Long nonExistentId = 999L;

        willThrow(new BusinessException(ErrorCode.GOODS_NOT_FOUND))
            .given(goodsService).deleteGoods(nonExistentId);

        mockMvc.perform(delete("/host/goods/{goodsId}", nonExistentId)
                .with(csrf()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("GOODS_NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("굿즈를 찾을 수 없습니다."));
    }
}

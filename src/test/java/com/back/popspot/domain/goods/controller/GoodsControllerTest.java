package com.back.popspot.domain.goods.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.back.popspot.domain.goods.dto.GoodsRegisterRequest;
import com.back.popspot.domain.goods.dto.GoodsRegisterResponse;
import com.back.popspot.domain.goods.service.GoodsService;
import com.back.popspot.global.exception.BusinessException;
import com.back.popspot.global.exception.ErrorCode;
import com.back.popspot.support.IntegrationTestSupport;

@DisplayName("굿즈 등록 API")
class GoodsControllerTest extends IntegrationTestSupport {

    @MockitoBean
    private GoodsService goodsService;

    @Test
    @WithMockUser
    @DisplayName("굿즈를 등록하면 201과 등록된 굿즈 정보를 반환한다")
    void registerGoods() throws Exception {
        Long popupStoreId = 1L;
        GoodsRegisterRequest request = new GoodsRegisterRequest("한정판 포스터", 15000, 30, "고화질 포스터");
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
    @DisplayName("존재하지 않는 팝업스토어에 굿즈를 등록하면 404를 반환한다")
    void registerGoods_popupStoreNotFound() throws Exception {
        Long nonExistentId = 999L;
        GoodsRegisterRequest request = new GoodsRegisterRequest("한정판 포스터", 15000, 30, null);

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
        GoodsRegisterRequest request = new GoodsRegisterRequest("   ", 15000, 30, null);

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
        GoodsRegisterRequest request = new GoodsRegisterRequest("한정판 포스터", null, 30, null);

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
        GoodsRegisterRequest request = new GoodsRegisterRequest("한정판 포스터", 15000, -1, null);

        mockMvc.perform(post("/host/popups/{popupStoreId}/goods", 1L)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    }
}

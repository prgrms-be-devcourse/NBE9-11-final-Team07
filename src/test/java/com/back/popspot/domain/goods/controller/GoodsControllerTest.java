package com.back.popspot.domain.goods.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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

import com.back.popspot.domain.goods.dto.GoodsDetailResponse;
import com.back.popspot.domain.goods.dto.GoodsListResponse;
import com.back.popspot.domain.goods.dto.GoodsRegisterRequest;
import com.back.popspot.domain.goods.dto.GoodsRegisterResponse;
import com.back.popspot.domain.goods.dto.GoodsSummaryResponse;
import com.back.popspot.domain.goods.dto.GoodsUpdateRequest;
import com.back.popspot.domain.goods.dto.GoodsUpdateResponse;
import com.back.popspot.domain.goods.entity.GoodsImageType;
import com.back.popspot.domain.goods.entity.GoodsStatus;
import com.back.popspot.domain.goods.service.GoodsService;
import com.back.popspot.global.dto.PageResponse;
import com.back.popspot.global.exception.BusinessException;
import com.back.popspot.global.exception.ErrorCode;
import com.back.popspot.support.IntegrationTestSupport;

@DisplayName("굿즈 API")
class GoodsControllerTest extends IntegrationTestSupport {

    @MockitoBean
    private GoodsService goodsService;

    // ── 공개 조회 테스트 (/api/v1) ───────────────────────────────────────────

    @Test
    @DisplayName("굿즈 전체 목록을 조회하면 200과 페이지 응답을 반환한다")
    void getGoodsList_returnsOkWithPageResponse() throws Exception {
        GoodsSummaryResponse item = new GoodsSummaryResponse(1L, "팝업 티셔츠", 10000, "thumb.jpg", 50, GoodsStatus.ON_SALE);
        PageResponse<GoodsSummaryResponse> response = new PageResponse<>(List.of(item), 0, 20, 1L);
        given(goodsService.getGoodsList(isNull(), any())).willReturn(response);

        mockMvc.perform(get("/api/v1/goods"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.content[0].goodsId").value(1))
            .andExpect(jsonPath("$.data.content[0].name").value("팝업 티셔츠"))
            .andExpect(jsonPath("$.data.content[0].thumbnailImageKey").value("thumb.jpg"))
            .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("status 파라미터를 전달하면 서비스에 status가 전달된다")
    void getGoodsList_withStatusFilter_passesStatusToService() throws Exception {
        PageResponse<GoodsSummaryResponse> response = new PageResponse<>(List.of(), 0, 20, 0L);
        given(goodsService.getGoodsList(eq(GoodsStatus.ON_SALE), any())).willReturn(response);

        mockMvc.perform(get("/api/v1/goods").param("status", "ON_SALE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("팝업스토어별 굿즈 목록을 조회하면 200을 반환한다")
    void getGoodsByPopupStore_returnsOk() throws Exception {
        PageResponse<GoodsSummaryResponse> response = new PageResponse<>(List.of(), 0, 20, 0L);
        given(goodsService.getGoodsByPopupStore(eq(1L), isNull(), any())).willReturn(response);

        mockMvc.perform(get("/api/v1/popups/1/goods"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").exists());
    }

    @Test
    @DisplayName("존재하지 않는 팝업스토어의 굿즈 목록을 조회하면 404를 반환한다")
    void getGoodsByPopupStore_popupStoreNotFound_returns404() throws Exception {
        given(goodsService.getGoodsByPopupStore(eq(999L), any(), any()))
            .willThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        mockMvc.perform(get("/api/v1/popups/999/goods"))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("굿즈 상세를 조회하면 200과 이미지 포함 응답을 반환한다")
    void getGoodsDetail_returnsOkWithImages() throws Exception {
        GoodsDetailResponse response = new GoodsDetailResponse(
            1L, "팝업 티셔츠", "한정판 팝업 굿즈입니다.", 10000, 50, GoodsStatus.ON_SALE,
            List.of(new GoodsDetailResponse.GoodsImageResponse("img.jpg", GoodsImageType.PRODUCT)),
            1L, "서울 팝업 2026"
        );
        given(goodsService.getGoodsDetail(1L)).willReturn(response);

        mockMvc.perform(get("/api/v1/goods/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.goodsId").value(1))
            .andExpect(jsonPath("$.data.name").value("팝업 티셔츠"))
            .andExpect(jsonPath("$.data.images[0].imageKey").value("img.jpg"))
            .andExpect(jsonPath("$.data.images[0].imageType").value("PRODUCT"))
            .andExpect(jsonPath("$.data.popupStoreTitle").value("서울 팝업 2026"));
    }

    @Test
    @DisplayName("존재하지 않는 굿즈를 상세 조회하면 404를 반환한다")
    void getGoodsDetail_goodsNotFound_returns404() throws Exception {
        given(goodsService.getGoodsDetail(999L))
            .willThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        mockMvc.perform(get("/api/v1/goods/999"))
            .andExpect(status().isNotFound());
    }

    // ── 호스트 관리 테스트 (/host) ───────────────────────────────────────────

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

    @Test
    @WithMockUser
    @DisplayName("굿즈 목록을 조회하면 200과 굿즈 목록을 반환한다")
    void getHostGoodsList() throws Exception {
        Long userId = 1L;
        List<GoodsListResponse> response = List.of(
            new GoodsListResponse(1L, "한정판 포스터", 15000, 30),
            new GoodsListResponse(2L, "에코백", 25000, 50)
        );

        given(goodsService.getGoodsList(eq(userId))).willReturn(response);

        mockMvc.perform(get("/host/goods")
                .param("userId", String.valueOf(userId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[0].id").value(1))
            .andExpect(jsonPath("$.data[0].name").value("한정판 포스터"))
            .andExpect(jsonPath("$.data[0].price").value(15000))
            .andExpect(jsonPath("$.data[0].stock").value(30));
    }

    @Test
    @WithMockUser
    @DisplayName("굿즈가 없으면 빈 배열을 반환한다")
    void getHostGoodsList_empty() throws Exception {
        Long userId = 1L;

        given(goodsService.getGoodsList(eq(userId))).willReturn(List.of());

        mockMvc.perform(get("/host/goods")
                .param("userId", String.valueOf(userId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @WithMockUser
    @DisplayName("userId 파라미터가 없으면 500을 반환한다")
    void getHostGoodsList_missingUserId() throws Exception {
        mockMvc.perform(get("/host/goods"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
    }

    @Test
    @WithMockUser
    @DisplayName("굿즈를 수정하면 200과 수정된 굿즈 정보를 반환한다")
    void updateGoods() throws Exception {
        Long goodsId = 1L;
        GoodsUpdateRequest request = new GoodsUpdateRequest("수정된 포스터", 20000, 50, "수정된 설명");
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
        GoodsUpdateRequest request = new GoodsUpdateRequest(null, 20000, null, null);
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
    @DisplayName("존재하지 않는 굿즈를 수정하면 404를 반환한다")
    void updateGoods_goodsNotFound() throws Exception {
        Long nonExistentId = 999L;
        GoodsUpdateRequest request = new GoodsUpdateRequest("수정된 포스터", 20000, 50, null);

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
    @DisplayName("가격이 음수면 400을 반환한다")
    void updateGoods_negativePrice() throws Exception {
        GoodsUpdateRequest request = new GoodsUpdateRequest(null, -1, null, null);

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
        GoodsUpdateRequest request = new GoodsUpdateRequest(null, null, -1, null);

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

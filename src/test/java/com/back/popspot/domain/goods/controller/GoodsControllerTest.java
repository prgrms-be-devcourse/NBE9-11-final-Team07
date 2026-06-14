package com.back.popspot.domain.goods.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.back.popspot.domain.goods.dto.GoodsDetailResponse;
import com.back.popspot.domain.goods.dto.GoodsSummaryResponse;
import com.back.popspot.domain.goods.entity.GoodsImageType;
import com.back.popspot.domain.goods.entity.GoodsStatus;
import com.back.popspot.domain.goods.service.GoodsService;
import com.back.popspot.global.dto.PageResponse;
import com.back.popspot.global.exception.BusinessException;
import com.back.popspot.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    value = GoodsController.class,
    excludeAutoConfiguration = {
        SecurityAutoConfiguration.class,
        ServletWebSecurityAutoConfiguration.class,
        SecurityFilterAutoConfiguration.class,
        OAuth2ClientWebSecurityAutoConfiguration.class
    }
)
class GoodsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GoodsService goodsService;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
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
    void getGoodsList_withStatusFilter_passesStatusToService() throws Exception {
        PageResponse<GoodsSummaryResponse> response = new PageResponse<>(List.of(), 0, 20, 0L);
        given(goodsService.getGoodsList(eq(GoodsStatus.ON_SALE), any())).willReturn(response);

        mockMvc.perform(get("/api/v1/goods").param("status", "ON_SALE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void getGoodsByPopupStore_returnsOk() throws Exception {
        PageResponse<GoodsSummaryResponse> response = new PageResponse<>(List.of(), 0, 20, 0L);
        given(goodsService.getGoodsByPopupStore(eq(1L), isNull(), any())).willReturn(response);

        mockMvc.perform(get("/api/v1/popups/1/goods"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").exists());
    }

    @Test
    void getGoodsByPopupStore_popupStoreNotFound_returns404() throws Exception {
        given(goodsService.getGoodsByPopupStore(eq(999L), any(), any()))
            .willThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        mockMvc.perform(get("/api/v1/popups/999/goods"))
            .andExpect(status().isNotFound());
    }

    @Test
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
    void getGoodsDetail_goodsNotFound_returns404() throws Exception {
        given(goodsService.getGoodsDetail(999L))
            .willThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        mockMvc.perform(get("/api/v1/goods/999"))
            .andExpect(status().isNotFound());
    }
}

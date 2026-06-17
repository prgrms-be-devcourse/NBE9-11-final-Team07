package com.back.popspot.domain.goods.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Optional;

import com.back.popspot.domain.goods.dto.GoodsDetailResponse;
import com.back.popspot.domain.goods.dto.GoodsSummaryResponse;
import com.back.popspot.domain.goods.entity.Goods;
import com.back.popspot.domain.goods.entity.GoodsImage;
import com.back.popspot.domain.goods.entity.GoodsImageType;
import com.back.popspot.domain.goods.entity.GoodsStatus;
import com.back.popspot.domain.goods.repository.GoodsImageRepository;
import com.back.popspot.domain.goods.repository.GoodsRepository;
import com.back.popspot.domain.popupStore.entity.PopupStore;
import com.back.popspot.domain.popupStore.repository.PopupStoreRepository;
import com.back.popspot.global.dto.PageResponse;
import com.back.popspot.global.exception.BusinessException;
import com.back.popspot.global.s3.S3Service;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GoodsServiceTest {

    @Mock
    private GoodsRepository goodsRepository;

    @Mock
    private GoodsImageRepository goodsImageRepository;

    @Mock
    private PopupStoreRepository popupStoreRepository;

    @Mock
    private S3Service s3Service;

    @InjectMocks
    private GoodsService goodsService;

    @Test
    void getGoodsList_statusNull_callsFindByDeletedAtIsNull() {
        Pageable pageable = PageRequest.of(0, 20);
        given(goodsRepository.findByDeletedAtIsNull(pageable)).willReturn(Page.empty());

        goodsService.getGoodsList(null, pageable);

        then(goodsRepository).should().findByDeletedAtIsNull(pageable);
    }

    @Test
    void getGoodsList_statusProvided_callsFindByStatusAndDeletedAtIsNull() {
        Pageable pageable = PageRequest.of(0, 20);
        given(goodsRepository.findByStatusAndDeletedAtIsNull(GoodsStatus.ON_SALE, pageable))
            .willReturn(Page.empty());

        goodsService.getGoodsList(GoodsStatus.ON_SALE, pageable);

        then(goodsRepository).should().findByStatusAndDeletedAtIsNull(GoodsStatus.ON_SALE, pageable);
    }

    @Test
    void getGoodsList_withContent_returnsMappedPageResponse() {
        Goods goods = mockGoods(1L, "팝업 티셔츠", GoodsStatus.ON_SALE);
        GoodsImage image = mockGoodsImage(1L, "thumb.jpg", GoodsImageType.PRODUCT);
        Pageable pageable = PageRequest.of(0, 20);

        given(goodsRepository.findByDeletedAtIsNull(pageable))
            .willReturn(new PageImpl<>(List.of(goods)));
        given(goodsImageRepository.findByGoods_IdInAndImageTypeOrderByIdAsc(List.of(1L), GoodsImageType.PRODUCT))
            .willReturn(List.of(image));
        given(s3Service.generatePresignedGetUrl("thumb.jpg")).willReturn("https://s3.example.com/thumb.jpg");

        PageResponse<GoodsSummaryResponse> result = goodsService.getGoodsList(null, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getGoodsId()).isEqualTo(1L);
        assertThat(result.getContent().get(0).getName()).isEqualTo("팝업 티셔츠");
        assertThat(result.getContent().get(0).getThumbnailImageUrl()).isEqualTo("https://s3.example.com/thumb.jpg");
        assertThat(result.getTotalElements()).isEqualTo(1L);
    }

    @Test
    void getGoodsByPopupStore_popupStoreNotFound_throwsBusinessException() {
        given(popupStoreRepository.existsById(999L)).willReturn(false);

        assertThatThrownBy(() -> goodsService.getGoodsByPopupStore(999L, null, PageRequest.of(0, 20)))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void getGoodsByPopupStore_success_returnsPageResponse() {
        Pageable pageable = PageRequest.of(0, 20);
        given(popupStoreRepository.existsById(1L)).willReturn(true);
        given(goodsRepository.findByPopupStore_IdAndDeletedAtIsNull(1L, pageable))
            .willReturn(Page.empty());

        PageResponse<GoodsSummaryResponse> result = goodsService.getGoodsByPopupStore(1L, null, pageable);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    void getGoodsDetail_goodsNotFound_throwsBusinessException() {
        given(goodsRepository.findByIdAndDeletedAtIsNull(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> goodsService.getGoodsDetail(999L))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void getGoodsDetail_success_returnsDetailWithAllImages() {
        Goods goods = mockGoods(1L, "팝업 티셔츠", GoodsStatus.ON_SALE);
        GoodsImage productImg = mockGoodsImage(1L, "product.jpg", GoodsImageType.PRODUCT);
        GoodsImage detailImg = mockGoodsImage(2L, "detail.jpg", GoodsImageType.DETAIL);

        given(goodsRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(goods));
        given(goodsImageRepository.findByGoods_IdOrderByIdAsc(1L))
            .willReturn(List.of(productImg, detailImg));
        given(s3Service.generatePresignedGetUrl(anyString()))
            .willAnswer(invocation -> "https://s3.example.com/" + invocation.getArgument(0));

        GoodsDetailResponse result = goodsService.getGoodsDetail(1L);

        assertThat(result.getGoodsId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("팝업 티셔츠");
        assertThat(result.getImages()).hasSize(2);
        assertThat(result.getImages().get(0).getImageUrl()).isEqualTo("https://s3.example.com/product.jpg");
        assertThat(result.getPopupStoreId()).isEqualTo(1L);
        assertThat(result.getPopupStoreTitle()).isEqualTo("서울 팝업 2026");
    }

    private Goods mockGoods(Long id, String name, GoodsStatus status) {
        Goods goods = mock(Goods.class);
        PopupStore popupStore = mock(PopupStore.class);
        given(goods.getId()).willReturn(id);
        given(goods.getName()).willReturn(name);
        given(goods.getPrice()).willReturn(10000);
        given(goods.getStock()).willReturn(50);
        given(goods.getDescription()).willReturn("한정판 팝업 굿즈입니다.");
        given(goods.getStatus()).willReturn(status);
        given(goods.getPopupStore()).willReturn(popupStore);
        given(popupStore.getId()).willReturn(1L);
        given(popupStore.getTitle()).willReturn("서울 팝업 2026");
        return goods;
    }

    private GoodsImage mockGoodsImage(Long id, String imageKey, GoodsImageType imageType) {
        GoodsImage image = mock(GoodsImage.class);
        Goods goods = mock(Goods.class);
        given(goods.getId()).willReturn(1L);
        given(image.getGoods()).willReturn(goods);
        given(image.getImageKey()).willReturn(imageKey);
        given(image.getImageType()).willReturn(imageType);
        return image;
    }
}

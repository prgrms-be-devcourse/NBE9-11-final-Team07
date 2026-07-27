package com.back.popspot.domain.goods.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import java.util.List;
import java.util.Optional;

import com.back.popspot.domain.goods.dto.GoodsDetailResponse;
import com.back.popspot.domain.goods.dto.GoodsRegisterRequest;
import com.back.popspot.domain.goods.dto.GoodsSummaryResponse;
import com.back.popspot.domain.goods.dto.GoodsUpdateRequest;
import com.back.popspot.domain.goods.entity.Goods;
import com.back.popspot.domain.goods.entity.GoodsImage;
import com.back.popspot.domain.goods.entity.GoodsImageType;
import com.back.popspot.domain.goods.entity.GoodsStatus;
import com.back.popspot.domain.goods.repository.GoodsImageRepository;
import com.back.popspot.domain.goods.repository.GoodsRepository;
import com.back.popspot.domain.popupStore.entity.PopupStore;
import com.back.popspot.domain.popupStore.repository.PopupStoreRepository;
import com.back.popspot.domain.user.entity.User;
import com.back.popspot.global.dto.PageResponse;
import com.back.popspot.global.exception.BusinessException;
import com.back.popspot.global.s3.S3Service;
import org.junit.jupiter.api.DisplayName;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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

    @Test
    @DisplayName("registerHostGoods: 트랜잭션 활성 중에는 S3 move 를 즉시 실행하지 않고 afterCommit 에 등록한다")
    void registerHostGoods_withActiveTx_moveIsRegisteredForAfterCommit() {
        Long userId = 1L;
        Long popupStoreId = 2L;
        User user = mock(User.class);
        PopupStore popupStore = mock(PopupStore.class);
        given(popupStore.getUser()).willReturn(user);
        given(user.getId()).willReturn(userId);
        given(popupStore.getId()).willReturn(popupStoreId);
        given(popupStoreRepository.findById(popupStoreId)).willReturn(Optional.of(popupStore));
        willAnswer(invocation -> {
            Goods saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 10L);
            return saved;
        }).given(goodsRepository).save(any(Goods.class));
        given(s3Service.isTempKey("temp/product.jpg")).willReturn(true);
        given(s3Service.isTempKey("temp/detail.jpg")).willReturn(true);
        given(s3Service.extractFileName("temp/product.jpg")).willReturn("product.jpg");
        given(s3Service.extractFileName("temp/detail.jpg")).willReturn("detail.jpg");

        GoodsRegisterRequest request = new GoodsRegisterRequest("상품명", 10000, 50, null, List.of(
            new GoodsRegisterRequest.ImageKeyEntry("temp/product.jpg", GoodsImageType.PRODUCT),
            new GoodsRegisterRequest.ImageKeyEntry("temp/detail.jpg", GoodsImageType.DETAIL)
        ));

        TransactionSynchronizationManager.initSynchronization();
        try {
            goodsService.registerHostGoods(userId, popupStoreId, request);

            // 커밋 전: move 미호출
            then(s3Service).should(never()).move(any(), any());

            // afterCommit 수동 실행
            TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

            // 커밋 후: 각 키 쌍별로 1회씩
            then(s3Service).should().move("temp/product.jpg", "goods/10/product/product.jpg");
            then(s3Service).should().move("temp/detail.jpg", "goods/10/detail/detail.jpg");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("updateHostGoods: 트랜잭션 활성 중에는 S3 move, delete 를 즉시 실행하지 않고 afterCommit 에 등록한다")
    void updateHostGoods_withActiveTx_moveAndDeleteAreRegisteredForAfterCommit() {
        Long userId = 1L;
        Long goodsId = 10L;
        User user = mock(User.class);
        PopupStore popupStore = mock(PopupStore.class);
        Goods goods = mock(Goods.class);
        given(goods.getId()).willReturn(goodsId);
        given(goods.getPopupStore()).willReturn(popupStore);
        given(popupStore.getUser()).willReturn(user);
        given(user.getId()).willReturn(userId);
        given(goodsRepository.findById(goodsId)).willReturn(Optional.of(goods));

        GoodsImage productImage = mock(GoodsImage.class);
        GoodsImage detailImage = mock(GoodsImage.class);
        given(productImage.getImageType()).willReturn(GoodsImageType.PRODUCT);
        given(productImage.getImageKey()).willReturn("goods/10/product/old-product.jpg");
        given(detailImage.getImageType()).willReturn(GoodsImageType.DETAIL);
        given(detailImage.getImageKey()).willReturn("goods/10/detail/old-detail.jpg");
        given(goodsImageRepository.findByGoods(goods)).willReturn(List.of(productImage, detailImage));

        given(s3Service.isTempKey("temp/new-product.jpg")).willReturn(true);
        given(s3Service.isTempKey("temp/new-detail.jpg")).willReturn(true);
        given(s3Service.extractFileName("temp/new-product.jpg")).willReturn("new-product.jpg");
        given(s3Service.extractFileName("temp/new-detail.jpg")).willReturn("new-detail.jpg");

        GoodsUpdateRequest request = new GoodsUpdateRequest(null, null, null, null, List.of(
            new GoodsUpdateRequest.ImageKeyEntry("temp/new-product.jpg", GoodsImageType.PRODUCT),
            new GoodsUpdateRequest.ImageKeyEntry("temp/new-detail.jpg", GoodsImageType.DETAIL)
        ));

        TransactionSynchronizationManager.initSynchronization();
        try {
            goodsService.updateHostGoods(userId, goodsId, request);

            // 커밋 전: move, delete 미호출
            then(s3Service).should(never()).move(any(), any());
            then(s3Service).should(never()).delete(any());

            // afterCommit 수동 실행
            TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

            // 커밋 후: 새 이미지 move + 기존 이미지 delete
            then(s3Service).should().move("temp/new-product.jpg", "goods/10/product/new-product.jpg");
            then(s3Service).should().move("temp/new-detail.jpg", "goods/10/detail/new-detail.jpg");
            then(s3Service).should().delete("goods/10/product/old-product.jpg");
            then(s3Service).should().delete("goods/10/detail/old-detail.jpg");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("registerHostGoods: tempKey 가 유효하지 않으면 예외를 던지고 S3 move 를 호출하지 않는다")
    void registerHostGoods_invalidTempKey_throwsAndMoveNeverCalled() {
        Long userId = 1L;
        Long popupStoreId = 2L;
        User user = mock(User.class);
        PopupStore popupStore = mock(PopupStore.class);
        given(popupStore.getUser()).willReturn(user);
        given(user.getId()).willReturn(userId);
        given(popupStore.getId()).willReturn(popupStoreId);
        given(popupStoreRepository.findById(popupStoreId)).willReturn(Optional.of(popupStore));
        willAnswer(invocation -> {
            Goods saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 10L);
            return saved;
        }).given(goodsRepository).save(any(Goods.class));
        given(s3Service.isTempKey("invalid/product.jpg")).willReturn(false);

        GoodsRegisterRequest request = new GoodsRegisterRequest("상품명", 10000, 50, null, List.of(
            new GoodsRegisterRequest.ImageKeyEntry("invalid/product.jpg", GoodsImageType.PRODUCT),
            new GoodsRegisterRequest.ImageKeyEntry("temp/detail.jpg", GoodsImageType.DETAIL)
        ));

        assertThatThrownBy(() -> goodsService.registerHostGoods(userId, popupStoreId, request))
            .isInstanceOf(BusinessException.class);
        then(s3Service).should(never()).move(any(), any());
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

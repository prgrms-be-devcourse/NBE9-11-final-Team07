package com.back.popspot.domain.goods.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.back.popspot.domain.goods.dto.GoodsDetailResponse;
import com.back.popspot.domain.goods.dto.GoodsListResponse;
import com.back.popspot.domain.goods.dto.GoodsRegisterRequest;
import com.back.popspot.domain.goods.dto.GoodsRegisterResponse;
import com.back.popspot.domain.goods.dto.GoodsSummaryResponse;
import com.back.popspot.domain.goods.dto.GoodsUpdateRequest;
import com.back.popspot.domain.goods.dto.GoodsUpdateResponse;
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
import com.back.popspot.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GoodsService {

    private final GoodsRepository goodsRepository;
    private final GoodsImageRepository goodsImageRepository;
    private final PopupStoreRepository popupStoreRepository;

    public PageResponse<GoodsSummaryResponse> getGoodsList(GoodsStatus status, Pageable pageable) {
        Page<Goods> goodsPage = (status != null)
            ? goodsRepository.findByStatusAndDeletedAtIsNull(status, pageable)
            : goodsRepository.findByDeletedAtIsNull(pageable);
        return toPageResponse(goodsPage);
    }

    public PageResponse<GoodsSummaryResponse> getGoodsByPopupStore(
        Long popupStoreId, GoodsStatus status, Pageable pageable
    ) {
        if (!popupStoreRepository.existsById(popupStoreId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        Page<Goods> goodsPage = (status != null)
            ? goodsRepository.findByPopupStore_IdAndStatusAndDeletedAtIsNull(popupStoreId, status, pageable)
            : goodsRepository.findByPopupStore_IdAndDeletedAtIsNull(popupStoreId, pageable);
        return toPageResponse(goodsPage);
    }

    public GoodsDetailResponse getGoodsDetail(Long goodsId) {
        Goods goods = goodsRepository.findByIdAndDeletedAtIsNull(goodsId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        List<GoodsImage> images = goodsImageRepository.findByGoods_IdOrderByIdAsc(goodsId);
        return GoodsDetailResponse.from(goods, images);
    }

    public List<GoodsListResponse> getGoodsList(Long userId) {
        return goodsRepository.findByPopupStoreUserIdAndDeletedAtIsNull(userId)
            .stream()
            .map(GoodsListResponse::from)
            .toList();
    }

    @Transactional
    public GoodsRegisterResponse registerGoods(Long popupStoreId, GoodsRegisterRequest request) {
        PopupStore popupStore = popupStoreRepository.findById(popupStoreId)
            .orElseThrow(() -> new BusinessException(ErrorCode.POPUP_STORE_NOT_FOUND));

        Goods goods = Goods.register(
            popupStore,
            request.name(),
            request.price(),
            request.stock(),
            request.description()
        );

        return GoodsRegisterResponse.from(goodsRepository.save(goods));
    }

    @Transactional
    public GoodsUpdateResponse updateGoods(Long goodsId, GoodsUpdateRequest request) {
        Goods goods = goodsRepository.findById(goodsId)
            .orElseThrow(() -> new BusinessException(ErrorCode.GOODS_NOT_FOUND));

        goods.update(request.name(), request.price(), request.stock(), request.description());

        return GoodsUpdateResponse.from(goods);
    }

    @Transactional
    public void deleteGoods(Long goodsId) {
        Goods goods = goodsRepository.findById(goodsId)
            .orElseThrow(() -> new BusinessException(ErrorCode.GOODS_NOT_FOUND));

        goods.softDelete();
    }

    private PageResponse<GoodsSummaryResponse> toPageResponse(Page<Goods> goodsPage) {
        List<Long> goodsIds = goodsPage.getContent().stream()
            .map(Goods::getId)
            .toList();

        Map<Long, String> thumbnailMap = goodsIds.isEmpty()
            ? Map.of()
            : goodsImageRepository
                .findByGoods_IdInAndImageTypeOrderByIdAsc(goodsIds, GoodsImageType.PRODUCT)
                .stream()
                .collect(Collectors.toMap(
                    img -> img.getGoods().getId(),
                    GoodsImage::getImageKey,
                    (first, second) -> first
                ));

        return PageResponse.from(goodsPage.map(
            goods -> GoodsSummaryResponse.from(goods, thumbnailMap.get(goods.getId()))
        ));
    }
}

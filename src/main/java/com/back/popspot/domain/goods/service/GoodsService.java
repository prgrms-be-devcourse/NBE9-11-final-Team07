package com.back.popspot.domain.goods.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.back.popspot.domain.goods.dto.GoodsListResponse;
import com.back.popspot.domain.goods.dto.GoodsRegisterRequest;
import com.back.popspot.domain.goods.dto.GoodsRegisterResponse;
import com.back.popspot.domain.goods.dto.GoodsUpdateRequest;
import com.back.popspot.domain.goods.dto.GoodsUpdateResponse;
import com.back.popspot.domain.goods.entity.Goods;
import com.back.popspot.domain.goods.entity.GoodsStatus;
import com.back.popspot.domain.goods.repository.GoodsRepository;
import com.back.popspot.domain.popupStore.entity.PopupStore;
import com.back.popspot.domain.popupStore.repository.PopupStoreRepository;
import com.back.popspot.global.exception.BusinessException;
import com.back.popspot.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GoodsService {

    private final GoodsRepository goodsRepository;
    private final PopupStoreRepository popupStoreRepository;

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

    @Transactional(readOnly = true)
    public List<GoodsListResponse> getGoodsList(Long userId) {
        return goodsRepository.findByPopupStoreUserIdAndStatusNot(userId, GoodsStatus.ENDED)
            .stream()
            .map(GoodsListResponse::from)
            .toList();
    }
}

package com.back.popspot.domain.goods.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.back.popspot.domain.goods.dto.GoodsDetailResponse;
import com.back.popspot.domain.goods.dto.GoodsImagePresignRequest;
import com.back.popspot.domain.goods.dto.GoodsImagePresignResponse;
import com.back.popspot.domain.goods.dto.GoodsRegisterRequest;
import com.back.popspot.domain.goods.dto.GoodsRegisterResponse;
import com.back.popspot.domain.goods.dto.GoodsSummaryResponse;
import com.back.popspot.domain.goods.dto.GoodsUpdateRequest;
import com.back.popspot.domain.goods.dto.GoodsUpdateResponse;
import com.back.popspot.domain.goods.dto.HostGoodsDetailResponse;
import com.back.popspot.domain.goods.dto.HostGoodsListResponse;
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
import com.back.popspot.global.s3.ImageDomain;
import com.back.popspot.global.s3.S3Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GoodsService {

	private final GoodsRepository goodsRepository;
	private final GoodsImageRepository goodsImageRepository;
	private final PopupStoreRepository popupStoreRepository;
	private final S3Service s3Service;

	@Transactional(readOnly = true)
    public PageResponse<GoodsSummaryResponse> getGoodsList(GoodsStatus status, Pageable pageable) {
        Page<Goods> goodsPage = (status != null)
            ? goodsRepository.findByStatusAndDeletedAtIsNull(status, pageable)
            : goodsRepository.findByDeletedAtIsNull(pageable);
        return toPageResponse(goodsPage);
    }

	@Transactional(readOnly = true)
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

	@Transactional(readOnly = true)
    public GoodsDetailResponse getGoodsDetail(Long goodsId) {
        Goods goods = goodsRepository.findByIdAndDeletedAtIsNull(goodsId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        List<GoodsDetailResponse.GoodsImageResponse> images = goodsImageRepository
            .findByGoods_IdOrderByIdAsc(goodsId)
            .stream()
            .map(image -> {
                String imageUrl = image.getImageKey() != null
                    ? s3Service.generatePresignedGetUrl(image.getImageKey())
                    : null;
                return GoodsDetailResponse.GoodsImageResponse.from(image, imageUrl);
            })
            .toList();
        return GoodsDetailResponse.from(goods, images);
    }

    @Transactional
    public GoodsRegisterResponse registerHostGoods(Long userId, Long popupStoreId, GoodsRegisterRequest request) {
        PopupStore popupStore = popupStoreRepository.findById(popupStoreId)
            .orElseThrow(() -> new BusinessException(ErrorCode.POPUP_STORE_NOT_FOUND));
        if (!popupStore.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

		List<GoodsRegisterRequest.ImageKeyEntry> imageKeys =
			request.imageKeys() != null ? request.imageKeys() : List.of();

		boolean hasProductImage = imageKeys.stream()
			.anyMatch(entry -> entry.imageType() == GoodsImageType.PRODUCT);
		if (!hasProductImage) {
			throw new BusinessException(ErrorCode.GOODS_PRODUCT_IMAGE_REQUIRED);
		}
		boolean hasDetailImage = imageKeys.stream()
			.anyMatch(entry -> entry.imageType() == GoodsImageType.DETAIL);
		if (!hasDetailImage) {
			throw new BusinessException(ErrorCode.GOODS_DETAIL_IMAGE_REQUIRED);
		}

		Goods goods = Goods.register(
			popupStore,
			request.name(),
			request.price(),
			request.stock(),
			request.description()
		);
		goodsRepository.save(goods);

		List<GoodsImage> images = imageKeys.stream()
			.map(entry -> {
				String tempKey = entry.imageKey();
				if (!s3Service.isTempKey(tempKey)) {
					throw new BusinessException(ErrorCode.INVALID_IMAGE_TEMP_KEY);
				}
				String fileName = s3Service.extractFileName(tempKey);
				String finalKey = ImageDomain.GOODS.finalKey(goods.getId(), entry.imageType().code(), fileName);
				s3Service.move(tempKey, finalKey);
				return GoodsImage.create(goods, finalKey, entry.imageType());
			})
			.toList();
		goodsImageRepository.saveAll(images);

		return GoodsRegisterResponse.from(goods);
	}

	public List<GoodsImagePresignResponse> generatePresignedUrls(GoodsImagePresignRequest request) {
		return request.fileNames().stream()
			.map(fileName -> {
				String key = s3Service.buildTempKey(fileName);
				String presignedUrl = s3Service.generatePresignedPutUrl(key);
				return new GoodsImagePresignResponse(key, presignedUrl);
			})
			.toList();
	}

	@Transactional(readOnly = true)
	public HostGoodsDetailResponse getHostGoodsDetail(Long userId, Long goodsId) {
		Goods goods = goodsRepository.findById(goodsId)
			.orElseThrow(() -> new BusinessException(ErrorCode.GOODS_NOT_FOUND));
		if (!goods.getPopupStore().getUser().getId().equals(userId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}
		Map<GoodsImageType, String> imageMap = goodsImageRepository.findByGoods(goods)
			.stream()
			.filter(img -> img.getImageKey() != null)
			.collect(Collectors.toMap(GoodsImage::getImageType, GoodsImage::getImageKey));
		String productUrl = imageMap.containsKey(GoodsImageType.PRODUCT)
			? s3Service.generatePresignedGetUrl(imageMap.get(GoodsImageType.PRODUCT))
			: null;
		String detailUrl = imageMap.containsKey(GoodsImageType.DETAIL)
			? s3Service.generatePresignedGetUrl(imageMap.get(GoodsImageType.DETAIL))
			: null;
		return HostGoodsDetailResponse.from(goods, productUrl, detailUrl);
	}

	@Transactional
	public GoodsUpdateResponse updateHostGoods(Long userId, Long goodsId, GoodsUpdateRequest request) {
		Goods goods = goodsRepository.findById(goodsId)
			.orElseThrow(() -> new BusinessException(ErrorCode.GOODS_NOT_FOUND));
		if (!goods.getPopupStore().getUser().getId().equals(userId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}

		goods.update(request.name(), request.price(), request.stock(), request.description());

		List<GoodsUpdateRequest.ImageKeyEntry> changes =
			request.imageKeys() != null ? request.imageKeys() : List.of();

		if (!changes.isEmpty()) {
			long distinctCount = changes.stream()
				.map(GoodsUpdateRequest.ImageKeyEntry::imageType)
				.distinct()
				.count();
			if (distinctCount != changes.size()) {
				throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
			}

			boolean hasProductChange = changes.stream()
				.anyMatch(e -> e.imageType() == GoodsImageType.PRODUCT);
			if (!hasProductChange) {
				throw new BusinessException(ErrorCode.GOODS_PRODUCT_IMAGE_REQUIRED);
			}
			boolean hasDetailChange = changes.stream()
				.anyMatch(e -> e.imageType() == GoodsImageType.DETAIL);
			if (!hasDetailChange) {
				throw new BusinessException(ErrorCode.GOODS_DETAIL_IMAGE_REQUIRED);
			}

			Map<GoodsImageType, GoodsImage> existing = goodsImageRepository.findByGoods(goods).stream()
				.collect(Collectors.toMap(GoodsImage::getImageType, Function.identity()));

			List<String> oldKeys = new ArrayList<>();
			for (GoodsUpdateRequest.ImageKeyEntry entry : changes) {
				String tempKey = entry.imageKey();
				if (!s3Service.isTempKey(tempKey)) {
					throw new BusinessException(ErrorCode.INVALID_IMAGE_TEMP_KEY);
				}
				GoodsImage image = existing.get(entry.imageType());
				if (image == null) {
					throw new BusinessException(ErrorCode.GOODS_IMAGE_NOT_FOUND);
				}
				String fileName = s3Service.extractFileName(tempKey);
				String newKey = ImageDomain.GOODS.finalKey(goodsId, entry.imageType().code(), fileName);
				s3Service.move(tempKey, newKey);
				oldKeys.add(image.getImageKey());
				image.changeImageKey(newKey);
			}
			registerAfterCommitDeletion(oldKeys);
		}

		return GoodsUpdateResponse.from(goods);
	}

	@Transactional
	public void deleteHostGoods(Long userId, Long goodsId) {
		Goods goods = goodsRepository.findById(goodsId)
			.orElseThrow(() -> new BusinessException(ErrorCode.GOODS_NOT_FOUND));
		if (!goods.getPopupStore().getUser().getId().equals(userId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}

		goods.softDelete();
	}

	@Transactional(readOnly = true)
	public List<HostGoodsListResponse> getHostGoodsList(Long userId, Long popupStoreId) {
		PopupStore popupStore = popupStoreRepository.findById(popupStoreId)
			.orElseThrow(() -> new BusinessException(ErrorCode.POPUP_STORE_NOT_FOUND));
		if (!popupStore.getUser().getId().equals(userId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}
		List<Goods> goodsList = goodsRepository.findByPopupStoreIdAndDeletedAtIsNull(popupStoreId);
		if (goodsList.isEmpty()) {
			return List.of();
		}
		Map<Long, Map<GoodsImageType, String>> imageMap = goodsImageRepository.findByGoodsIn(goodsList)
			.stream()
			.collect(Collectors.groupingBy(
				img -> img.getGoods().getId(),
				Collectors.toMap(GoodsImage::getImageType, GoodsImage::getImageKey)
			));
		return goodsList.stream()
			.map(goods -> {
				Map<GoodsImageType, String> images = imageMap.getOrDefault(goods.getId(), Map.of());
				String productUrl = images.containsKey(GoodsImageType.PRODUCT)
					? s3Service.generatePresignedGetUrl(images.get(GoodsImageType.PRODUCT))
					: null;
				return HostGoodsListResponse.from(goods, productUrl);
			})
			.toList();
	}

	private void registerAfterCommitDeletion(List<String> keys) {
		if (keys.isEmpty())
			return;
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				keys.forEach(s3Service::delete);
			}
		});
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
                .filter(img -> img.getImageKey() != null)
                .collect(Collectors.toMap(
                    img -> img.getGoods().getId(),
                    img -> s3Service.generatePresignedGetUrl(img.getImageKey()),
                    (first, second) -> first
                ));

        return PageResponse.from(goodsPage.map(
            goods -> GoodsSummaryResponse.from(goods, thumbnailMap.get(goods.getId()))
        ));
    }
}

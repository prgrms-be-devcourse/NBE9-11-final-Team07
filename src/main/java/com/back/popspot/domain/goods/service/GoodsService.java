package com.back.popspot.domain.goods.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.back.popspot.domain.goods.dto.GoodsImagePresignRequest;
import com.back.popspot.domain.goods.dto.GoodsImagePresignResponse;
import com.back.popspot.domain.goods.dto.GoodsListResponse;
import com.back.popspot.domain.goods.dto.GoodsRegisterRequest;
import com.back.popspot.domain.goods.dto.GoodsRegisterResponse;
import com.back.popspot.domain.goods.dto.GoodsUpdateRequest;
import com.back.popspot.domain.goods.dto.GoodsUpdateResponse;
import com.back.popspot.domain.goods.entity.Goods;
import com.back.popspot.domain.goods.entity.GoodsImage;
import com.back.popspot.domain.goods.entity.GoodsImageType;
import com.back.popspot.domain.goods.repository.GoodsImageRepository;
import com.back.popspot.domain.goods.repository.GoodsRepository;
import com.back.popspot.domain.popupStore.entity.PopupStore;
import com.back.popspot.domain.popupStore.repository.PopupStoreRepository;
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

	@Transactional
	public GoodsRegisterResponse registerGoods(Long popupStoreId, GoodsRegisterRequest request) {
		PopupStore popupStore = popupStoreRepository.findById(popupStoreId)
			.orElseThrow(() -> new BusinessException(ErrorCode.POPUP_STORE_NOT_FOUND));

		List<GoodsRegisterRequest.ImageKeyEntry> imageKeys =
			request.imageKeys() != null ? request.imageKeys() : List.of();

		boolean hasProductImage = imageKeys.stream()
			.anyMatch(entry -> entry.imageType() == GoodsImageType.PRODUCT);
		if (!hasProductImage) {
			throw new BusinessException(ErrorCode.GOODS_PRODUCT_IMAGE_REQUIRED);
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

	@Transactional(readOnly = true)
	public List<GoodsImagePresignResponse> generatePresignedUrls(GoodsImagePresignRequest request) {
		return request.fileNames().stream()
			.map(fileName -> {
				String key = s3Service.buildTempKey(fileName);
				String presignedUrl = s3Service.generatePresignedPutUrl(key);
				return new GoodsImagePresignResponse(key, presignedUrl);
			})
			.toList();
	}

	@Transactional
	public GoodsUpdateResponse updateGoods(Long goodsId, GoodsUpdateRequest request) {
		Goods goods = goodsRepository.findById(goodsId)
			.orElseThrow(() -> new BusinessException(ErrorCode.GOODS_NOT_FOUND));

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
	public void deleteGoods(Long goodsId) {
		Goods goods = goodsRepository.findById(goodsId)
			.orElseThrow(() -> new BusinessException(ErrorCode.GOODS_NOT_FOUND));

		goods.softDelete();
	}

	@Transactional(readOnly = true)
	public List<GoodsListResponse> getGoodsList(Long userId) {
		return goodsRepository.findByPopupStoreUserIdAndDeletedAtIsNull(userId)
			.stream()
			.map(GoodsListResponse::from)
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
}

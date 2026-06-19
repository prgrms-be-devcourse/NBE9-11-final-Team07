package com.back.popspot.domain.popupStore.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.back.popspot.domain.popupStore.dto.PopupImagePresignResponse;
import com.back.popspot.domain.popupStore.dto.PopupStoreDetailResponse;
import com.back.popspot.domain.popupStore.dto.PopupStoreListResponse;
import com.back.popspot.domain.popupStore.dto.ReservationSlotResponse;
import com.back.popspot.domain.popupStore.entity.PopupStatus;
import com.back.popspot.domain.popupStore.entity.PopupStore;
import com.back.popspot.domain.popupStore.repository.PopupStoreRepository;
import com.back.popspot.domain.popupStore.repository.ReservationSlotRepository;
import com.back.popspot.global.exception.BusinessException;
import com.back.popspot.global.exception.ErrorCode;
import com.back.popspot.global.s3.S3Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class
PopupStoreService {

	private final PopupStoreRepository popupStoreRepository;
	private final ReservationSlotRepository reservationSlotRepository;
	private final S3Service s3Service;

	/**
	 * 팝업스토어 목록을 조회한다.
	 * status 가 없으면 전체 조회, 있으면 예약 기간 조건으로 필터링한다.
	 * 각 팝업의 status 는 조회 시점 기준으로 계산해 응답에 담는다.
	 */
	public Page<PopupStoreListResponse> getPopupStores(PopupStatus status, Pageable pageable) {
		LocalDateTime now = LocalDateTime.now();
		Page<PopupStore> popupStores = findByStatus(status, now, pageable);
		return popupStores.map(popupStore ->
			PopupStoreListResponse.from(
				popupStore,
				popupStore.calculateStatus(now),
				presignedImageUrl(popupStore.getImageKey())));
	}

	/**
	 * 팝업스토어 단건 상세 조회. 없으면 RESOURCE_NOT_FOUND.
	 * status 는 조회 시점 기준으로 계산해 응답에 담는다.
	 */
	public PopupStoreDetailResponse getPopupStore(Long popupStoreId) {
		PopupStore popupStore = popupStoreRepository.findById(popupStoreId)
			.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

		PopupStatus status = popupStore.calculateStatus(LocalDateTime.now());
		return PopupStoreDetailResponse.from(popupStore, status, presignedImageUrl(popupStore.getImageKey()));
	}

	// imageKey 로 presigned GET URL 발급 (없으면 null)
	private String presignedImageUrl(String imageKey) {
		return imageKey != null ? s3Service.generatePresignedGetUrl(imageKey) : null;
	}

	/**
	 * 이미지 업로드용 presigned PUT URL 을 발급한다.
	 * 반환된 tempKey 를 업로드 후 등록/수정 요청의 imageKey 로 전달한다.
	 */
	public PopupImagePresignResponse generatePresignedUrl(String fileName) {
		String tempKey = s3Service.buildTempKey(fileName);
		String presignedUrl = s3Service.generatePresignedPutUrl(tempKey);
		return new PopupImagePresignResponse(tempKey, presignedUrl);
	}

	/**
	 * 특정 팝업스토어의 특정 날짜 예약 슬롯 목록을 조회한다.
	 * 팝업이 없으면 RESOURCE_NOT_FOUND.
	 */
	public List<ReservationSlotResponse> getSlots(Long popupStoreId, LocalDate date) {
		if (!popupStoreRepository.existsById(popupStoreId)) {
			throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
		}

		return reservationSlotRepository.findByPopupStoreIdAndSlotDate(popupStoreId, date)
			.stream()
			.map(ReservationSlotResponse::from)
			.toList();
	}

	private Page<PopupStore> findByStatus(PopupStatus status, LocalDateTime now, Pageable pageable) {
		if (status == null) {
			return popupStoreRepository.findAll(pageable);
		}
		return switch (status) {
			case UPCOMING -> popupStoreRepository.findUpcoming(now, pageable);
			case OPEN -> popupStoreRepository.findOpen(now, pageable);
			case CLOSED -> popupStoreRepository.findClosed(now, pageable);
		};
	}
}

package com.back.popspot.domain.popupStore.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.back.popspot.domain.popupStore.dto.PopupImagePresignResponse;
import com.back.popspot.domain.popupStore.dto.PopupStoreDetailResponse;
import com.back.popspot.domain.popupStore.dto.PopupStoreListResponse;
import com.back.popspot.domain.popupStore.dto.ReservationSlotResponse;
import com.back.popspot.domain.popupStore.entity.PopupStatus;
import com.back.popspot.domain.popupStore.service.PopupStoreService;
import com.back.popspot.global.response.CommonApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/popups")
public class PopupStoreController {

	private final PopupStoreService popupStoreService;

	// 팝업스토어 목록 조회 (비회원 허용). status 없으면 전체, 있으면 상태별 필터
	@GetMapping
	public ResponseEntity<CommonApiResponse<Page<PopupStoreListResponse>>> getPopupStores(
			@RequestParam(required = false) PopupStatus status,
			Pageable pageable
	) {
		Page<PopupStoreListResponse> popupStores = popupStoreService.getPopupStores(status, pageable);
		return ResponseEntity.ok(CommonApiResponse.success(popupStores));
	}

	// 팝업스토어 상세 조회 (비회원 허용)
	@GetMapping("/{popupStoreId}")
	public ResponseEntity<CommonApiResponse<PopupStoreDetailResponse>> getPopupStore(
			@PathVariable Long popupStoreId
	) {
		PopupStoreDetailResponse popupStore = popupStoreService.getPopupStore(popupStoreId);
		return ResponseEntity.ok(CommonApiResponse.success(popupStore));
	}

	// 예약 슬롯 조회 (비회원 허용). 특정 날짜(date)의 슬롯 목록
	@GetMapping("/{popupStoreId}/slots")
	public ResponseEntity<CommonApiResponse<List<ReservationSlotResponse>>> getSlots(
			@PathVariable Long popupStoreId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
	) {
		List<ReservationSlotResponse> slots = popupStoreService.getSlots(popupStoreId, date);
		return ResponseEntity.ok(CommonApiResponse.success(slots));
	}

	// 이미지 업로드용 presigned PUT URL 발급
	@GetMapping("/images/presigned-url")
	public ResponseEntity<CommonApiResponse<PopupImagePresignResponse>> getPresignedUrl(
			@RequestParam String fileName
	) {
		PopupImagePresignResponse response = popupStoreService.generatePresignedUrl(fileName);
		return ResponseEntity.ok(CommonApiResponse.success(response));
	}
}

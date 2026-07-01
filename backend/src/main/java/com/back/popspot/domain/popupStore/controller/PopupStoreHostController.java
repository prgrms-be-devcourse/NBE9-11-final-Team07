package com.back.popspot.domain.popupStore.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.back.popspot.domain.popupStore.dto.PopupStoreCreateRequest;
import com.back.popspot.domain.popupStore.dto.PopupStoreListResponse;
import com.back.popspot.domain.popupStore.dto.PopupStoreUpdateRequest;
import com.back.popspot.domain.popupStore.dto.ReservationSlotCreateRequest;
import com.back.popspot.domain.popupStore.dto.ReservationSlotUpdateRequest;
import com.back.popspot.domain.popupStore.service.PopupStoreHostService;
import com.back.popspot.global.response.CommonApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 주최자(host) 전용 팝업스토어 API. 인증 필요.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/host/popups")
public class PopupStoreHostController {

	private final PopupStoreHostService popupStoreHostService;

	@GetMapping
	public ResponseEntity<CommonApiResponse<List<PopupStoreListResponse>>> getMyPopupStores(
			@AuthenticationPrincipal Long userId
	) {
		List<PopupStoreListResponse> popupStores = popupStoreHostService.getMyPopupStores(userId);
		return ResponseEntity.ok(CommonApiResponse.success(popupStores));
	}

	// 팝업스토어 등록 (주최자)
	@PostMapping
	public ResponseEntity<CommonApiResponse<Long>> createPopupStore(
			@AuthenticationPrincipal Long userId,
			@RequestBody @Valid PopupStoreCreateRequest request
	) {
		Long popupStoreId = popupStoreHostService.createPopupStore(userId, request);
		return ResponseEntity
				.status(HttpStatus.CREATED)
				.body(CommonApiResponse.created("생성이 완료되었습니다.", popupStoreId));
	}

	// 팝업스토어 부분 수정 (주최자, 소유자만)
	@PatchMapping("/{popupStoreId}")
	public ResponseEntity<CommonApiResponse<Void>> updatePopupStore(
			@AuthenticationPrincipal Long userId,
			@PathVariable Long popupStoreId,
			@RequestBody @Valid PopupStoreUpdateRequest request
	) {
		popupStoreHostService.updatePopupStore(userId, popupStoreId, request);
		return ResponseEntity.ok(CommonApiResponse.successMessage("수정이 완료되었습니다."));
	}

	// 팝업스토어 삭제 (주최자, 소유자만 / 운영 시작 전까지)
	@DeleteMapping("/{popupStoreId}")
	public ResponseEntity<CommonApiResponse<Void>> deletePopupStore(
			@AuthenticationPrincipal Long userId,
			@PathVariable Long popupStoreId
	) {
		popupStoreHostService.deletePopupStore(userId, popupStoreId);
		return ResponseEntity.ok(CommonApiResponse.successMessage("삭제가 완료되었습니다."));
	}

	// 예약 슬롯 생성 (주최자, 소유자만)
	@PostMapping("/{popupStoreId}/slots")
	public ResponseEntity<CommonApiResponse<Long>> createSlot(
			@AuthenticationPrincipal Long userId,
			@PathVariable Long popupStoreId,
			@RequestBody @Valid ReservationSlotCreateRequest request
	) {
		Long slotId = popupStoreHostService.createSlot(userId, popupStoreId, request);
		return ResponseEntity
				.status(HttpStatus.CREATED)
				.body(CommonApiResponse.created("슬롯 생성이 완료되었습니다.", slotId));
	}

	@PatchMapping("/{popupStoreId}/slots/{slotId}")
	public ResponseEntity<CommonApiResponse<Void>> updateSlot(
		@AuthenticationPrincipal Long userId,
		@PathVariable Long popupStoreId,
		@PathVariable Long slotId,
		@Valid @RequestBody ReservationSlotUpdateRequest request
	) {
		popupStoreHostService.updateSlot(userId, popupStoreId, slotId, request);
		return ResponseEntity.ok(CommonApiResponse.successMessage("수정이 완료되었습니다."));
	}

	@DeleteMapping("/{popupStoreId}/slots/{slotId}")
	public ResponseEntity<CommonApiResponse<Void>> deleteSlot(
		@AuthenticationPrincipal Long userId,
		@PathVariable Long popupStoreId,
		@PathVariable Long slotId
	) {
		popupStoreHostService.deleteSlot(userId, popupStoreId, slotId);
		return ResponseEntity.ok(CommonApiResponse.successMessage("삭제가 완료되었습니다."));
	}
}

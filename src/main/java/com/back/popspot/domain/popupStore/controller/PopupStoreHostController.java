package com.back.popspot.domain.popupStore.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.back.popspot.domain.popupStore.dto.PopupStoreCreateRequest;
import com.back.popspot.domain.popupStore.service.PopupStoreHostService;
import com.back.popspot.global.response.CommonApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 주최자(host) 전용 팝업스토어 API. 인증 필요.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/host/popups")
public class PopupStoreHostController {

	private final PopupStoreHostService popupStoreHostService;

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
}

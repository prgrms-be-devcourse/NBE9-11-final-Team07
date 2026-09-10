package com.back.popspot.global.admin.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.back.popspot.global.admin.dto.SlotStockResetRequest;
import com.back.popspot.global.admin.dto.SlotStockResetResponse;
import com.back.popspot.global.admin.service.SlotStockResetService;
import com.back.popspot.global.response.CommonApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 부하 테스트 반복 실행용 관리 API.
 *
 * <p>운영(prod) 프로파일에서는 등록되지 않는다. 그 외 프로파일에서는 인증된 사용자면 호출할 수 있다.
 */
@Validated
@RestController
@Profile("!prod")
@RequiredArgsConstructor
@RequestMapping("/admin/load-test")
public class SlotStockAdminController {

	private final SlotStockResetService slotStockResetService;

	@PostMapping("/slots/{slotId}/stock/reset")
	public ResponseEntity<CommonApiResponse<SlotStockResetResponse>> resetSlotStock(
		@PathVariable Long slotId,
		@Valid @RequestBody SlotStockResetRequest request
	) {
		SlotStockResetResponse response = slotStockResetService.reset(slotId, request);

		return ResponseEntity.ok(CommonApiResponse.success(response));
	}
}

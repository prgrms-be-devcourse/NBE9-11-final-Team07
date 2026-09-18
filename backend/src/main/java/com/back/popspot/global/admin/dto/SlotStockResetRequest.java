package com.back.popspot.global.admin.dto;

import jakarta.validation.constraints.Min;

/**
 * 슬롯 재고 리셋 요청. 모든 필드가 선택값이며, 생략하면 아래 기본값으로 동작한다.
 *
 * @param capacity            새 슬롯 정원. null 이면 기존 capacity 를 유지한다.
 * @param remaining           리셋할 남은 재고. null 이면 적용된 capacity 와 동일(전량 복구)하게 둔다.
 * @param purgeReservations   이 슬롯의 예약/결제/대기/취소풀 행을 함께 삭제할지 여부. null 이면 true.
 * @param proceedUserIdFrom   proceed flag 를 재발급할 유저 id 시작(포함). proceedUserIdTo 와 함께 줘야 한다.
 * @param proceedUserIdTo     proceed flag 를 재발급할 유저 id 끝(포함).
 * @param proceedTtlSeconds   재발급할 proceed flag 의 TTL(초). null 이면 waiting-queue.proceed-ttl-seconds 를 쓴다.
 */
public record SlotStockResetRequest(
	@Min(value = 1, message = "capacity 는 1 이상이어야 합니다.")
	Integer capacity,

	@Min(value = 0, message = "remaining 은 0 이상이어야 합니다.")
	Integer remaining,

	Boolean purgeReservations,

	@Min(value = 1, message = "proceedUserIdFrom 은 1 이상이어야 합니다.")
	Long proceedUserIdFrom,

	@Min(value = 1, message = "proceedUserIdTo 는 1 이상이어야 합니다.")
	Long proceedUserIdTo,

	@Min(value = 1, message = "proceedTtlSeconds 는 1 이상이어야 합니다.")
	Long proceedTtlSeconds
) {

	public boolean purgeReservationsOrDefault() {
		return purgeReservations == null || purgeReservations;
	}

	// 두 경계가 모두 있어야 재발급한다. 한쪽만 준 경우는 서비스에서 잘못된 요청으로 거절한다.
	public boolean hasProceedRange() {
		return proceedUserIdFrom != null && proceedUserIdTo != null;
	}

	public boolean hasPartialProceedRange() {
		return (proceedUserIdFrom == null) != (proceedUserIdTo == null);
	}
}

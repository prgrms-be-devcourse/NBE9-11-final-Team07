package com.back.popspot.global.admin.dto;

/**
 * 슬롯 재고 리셋 결과. 어떤 값이 어떻게 바뀌었고 어떤 행이 몇 건 지워졌는지 그대로 돌려준다.
 *
 * @param slotId                  리셋한 슬롯 id
 * @param popupStoreId            슬롯이 속한 팝업 id (proceed flag 키에 쓰인 값)
 * @param capacity                리셋 후 슬롯 정원
 * @param previousRedisRemaining  리셋 직전의 Redis remaining (키가 없었으면 null)
 * @param resetRemaining          리셋 후 Redis remaining (= DB capacity - reservedCount)
 * @param reservedCount           리셋 후 DB reserved_count
 * @param ttlSeconds              Redis remaining 키에 설정한 TTL(초). 팝업 종료일이 지났으면 null(무기한)
 * @param deleted                 삭제된 행 수. purgeReservations=false 면 모두 0
 * @param proceed                 재발급한 proceed flag 정보. 요청하지 않았으면 null
 */
public record SlotStockResetResponse(
	Long slotId,
	Long popupStoreId,
	int capacity,
	Long previousRedisRemaining,
	long resetRemaining,
	int reservedCount,
	Long ttlSeconds,
	DeletedRows deleted,
	GrantedProceedFlags proceed
) {

	public record DeletedRows(
		int payments,
		int reservations,
		int waitlists,
		int cancelPools
	) {

		public static DeletedRows none() {
			return new DeletedRows(0, 0, 0, 0);
		}
	}

	/**
	 * @param userIdFrom  재발급 시작 유저 id(포함)
	 * @param userIdTo    재발급 끝 유저 id(포함)
	 * @param granted     발급한 flag 개수
	 * @param ttlSeconds  각 flag 에 건 TTL(초)
	 */
	public record GrantedProceedFlags(
		long userIdFrom,
		long userIdTo,
		long granted,
		long ttlSeconds
	) {
	}
}

package com.back.popspot.domain.popupStore.entity;

/**
 * 예약 기간을 기준으로 계산되는 팝업스토어의 진행 상태.
 * DB 에 저장되지 않고 {@link PopupStore#calculateStatus} 로 매 조회 시점에 계산된다.
 */
public enum PopupStatus {
	UPCOMING, // 예약 시작 전
	OPEN,     // 예약 진행 중
	CLOSED    // 예약 종료
}

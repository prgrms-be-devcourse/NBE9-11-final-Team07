package com.back.popspot.domain.reservation.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.back.popspot.domain.popupStore.entity.PopupStore;
import com.back.popspot.domain.popupStore.repository.PopupStoreRepository;
import com.back.popspot.domain.popupStore.repository.ReservationSlotRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 열린 팝업의 모든 예약 슬롯에 대해 Redis 잔여 정원을 DB 원장 기준으로 재구축한다.
 *
 * <p>대기열의 {@code QueueRecoveryService.recoverAll()}을 예약 도메인에 이식한 것.
 * {@link ReservationCapacityRebuildService#rebuildSlotRemaining(Long)}의 로직은 건드리지 않고 호출만 한다.
 *
 * <p>예약용 Redis 인덱스는 만들지 않는다 — 복구 대상 슬롯은 DB({@code findOpen} + 슬롯 조회)에서 직접 뽑는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationCapacityRecoveryService {

	private final PopupStoreRepository popupStoreRepository;
	private final ReservationSlotRepository reservationSlotRepository;
	private final ReservationCapacityRebuildService reservationCapacityRebuildService;

	public void recoverAll() {
		LocalDateTime now = LocalDateTime.now();

		// 열린 팝업 전체를 DB에서 직접 조회한다 (Redis 인덱스에 의존하지 않는다).
		List<Long> openPopupIds = popupStoreRepository.findOpen(now, Pageable.unpaged())
			.map(PopupStore::getId)
			.getContent();

		if (openPopupIds.isEmpty()) {
			log.info("[ReservationCapacityRecovery] 열린 팝업 없음 — 복구 스킵");
			return;
		}

		List<Long> slotIds = reservationSlotRepository.findIdsByPopupStoreIdIn(openPopupIds);
		log.info("[ReservationCapacityRecovery] 복구 시작 — 열린 팝업 {}건, 대상 슬롯 {}건",
			openPopupIds.size(), slotIds.size());

		int success = 0;
		int failed = 0;
		for (Long slotId : slotIds) {
			try {
				reservationCapacityRebuildService.rebuildSlotRemaining(slotId);
				success++;
			} catch (RuntimeException e) {
				// 슬롯 단위 예외 격리 — rebuildSlotRemaining은 예외를 던질 수 있고(오버부킹 의심/Redis 쓰기 실패 등),
				// 한 슬롯의 실패가 나머지 슬롯 복구를 막아서는 안 된다.
				failed++;
				log.error("[ReservationCapacityRecovery] 슬롯 복구 실패 — slotId={}, 다음 슬롯 계속 진행", slotId, e);
			}
		}

		log.info("[ReservationCapacityRecovery] 복구 완료 — 성공 {}건, 실패 {}건", success, failed);
	}
}

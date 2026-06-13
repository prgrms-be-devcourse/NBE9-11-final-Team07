package com.back.popspot.domain.popupStore.service;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.back.popspot.domain.popupStore.dto.PopupStoreListResponse;
import com.back.popspot.domain.popupStore.entity.PopupStatus;
import com.back.popspot.domain.popupStore.entity.PopupStore;
import com.back.popspot.domain.popupStore.repository.PopupStoreRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class
PopupStoreService {

	private final PopupStoreRepository popupStoreRepository;

	/**
	 * 팝업스토어 목록을 조회한다.
	 * status 가 없으면 전체 조회, 있으면 예약 기간 조건으로 필터링한다.
	 * 각 팝업의 status 는 조회 시점 기준으로 계산해 응답에 담는다.
	 */
	public Page<PopupStoreListResponse> getPopupStores(PopupStatus status, Pageable pageable) {
		LocalDateTime now = LocalDateTime.now();
		Page<PopupStore> popupStores = findByStatus(status, now, pageable);
		return popupStores.map(popupStore ->
			PopupStoreListResponse.from(popupStore, popupStore.calculateStatus(now)));
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

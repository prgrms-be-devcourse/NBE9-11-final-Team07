package com.back.popspot.domain.popupStore.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.back.popspot.domain.popupStore.dto.PopupStoreCreateRequest;
import com.back.popspot.domain.popupStore.entity.PopupFeeType;
import com.back.popspot.domain.popupStore.entity.PopupStore;
import com.back.popspot.domain.popupStore.repository.PopupStoreRepository;
import com.back.popspot.domain.user.entity.User;
import com.back.popspot.global.exception.BusinessException;
import com.back.popspot.global.exception.ErrorCode;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;

/**
 * 주최자(host) 전용 팝업스토어 쓰기 작업 서비스.
 */
@Service
@RequiredArgsConstructor
public class PopupStoreHostService {

	private final PopupStoreRepository popupStoreRepository;

	@PersistenceContext
	private EntityManager entityManager;

	/**
	 * 팝업스토어를 등록한다. (주최자)
	 * PAID 인데 price 가 없거나 0 이하, 예약/운영 기간이 역전되면 INVALID_INPUT_VALUE.
	 */
	@Transactional
	public Long createPopupStore(Long userId, PopupStoreCreateRequest request) {
		if (request.feeType() == PopupFeeType.PAID
				&& (request.price() == null || request.price() <= 0)) {
			throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
		}
		if (!request.reservationStartAt().isBefore(request.reservationEndAt())) {
			throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
		}
		if (!request.openDate().isBefore(request.closeDate())) {
			throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
		}

		User user = entityManager.getReference(User.class, userId);
		PopupStore popupStore = PopupStore.of(user, request);
		popupStoreRepository.save(popupStore);
		return popupStore.getId();
	}
}

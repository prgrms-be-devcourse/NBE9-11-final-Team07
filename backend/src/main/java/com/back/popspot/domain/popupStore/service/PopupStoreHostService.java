package com.back.popspot.domain.popupStore.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.back.popspot.domain.popupStore.dto.PopupStoreCreateRequest;
import com.back.popspot.domain.popupStore.dto.PopupStoreUpdateRequest;
import com.back.popspot.domain.popupStore.dto.ReservationSlotCreateRequest;
import com.back.popspot.domain.popupStore.dto.ReservationSlotUpdateRequest;
import com.back.popspot.domain.popupStore.entity.PopupFeeType;
import com.back.popspot.domain.popupStore.entity.PopupStore;
import com.back.popspot.domain.popupStore.entity.ReservationSlot;
import com.back.popspot.domain.popupStore.repository.PopupStoreRepository;
import com.back.popspot.domain.popupStore.repository.ReservationSlotRepository;
import com.back.popspot.domain.user.entity.User;
import com.back.popspot.global.exception.BusinessException;
import com.back.popspot.global.exception.ErrorCode;
import com.back.popspot.global.s3.S3Service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 주최자(host) 전용 팝업스토어 쓰기 작업 서비스.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PopupStoreHostService {

	private final PopupStoreRepository popupStoreRepository;
	private final ReservationSlotRepository reservationSlotRepository;
	private final S3Service s3Service;

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

		// 임시 업로드 이미지면 정식 위치(popup/{id}/{fileName})로 이동 후 키 갱신
		if (s3Service.isTempKey(popupStore.getImageKey())) {
			String srcKey = popupStore.getImageKey();
			String destKey = buildPopupImageKey(popupStore.getId(), popupStore.getImageKey());
			popupStore.updateImageKey(destKey);
			registerAfterCommitMove(srcKey, destKey);
		}

		return popupStore.getId();
	}

	/**
	 * 팝업스토어를 부분 수정한다. (주최자, 소유자만)
	 * 없으면 RESOURCE_NOT_FOUND, 소유자가 아니면 FORBIDDEN.
	 * null 이 아닌 필드만 반영하며, dirty checking 으로 자동 저장된다.
	 */
	@Transactional
	public void updatePopupStore(Long userId, Long popupStoreId, PopupStoreUpdateRequest request) {
		PopupStore popupStore = popupStoreRepository.findById(popupStoreId)
			.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

		if (!popupStore.getUser().getId().equals(userId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}

		if (!LocalDateTime.now().isBefore(popupStore.getOpenDate())) {
			throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
		}

		if (request.title() != null) {
			popupStore.updateTitle(request.title());
		}
		if (request.location() != null) {
			popupStore.updateLocation(request.location());
		}
		if (request.feeType() != null) {
			popupStore.updateFeeType(request.feeType());
		}
		if (request.price() != null) {
			popupStore.updatePrice(request.price());
		}
		if (request.reservationStartAt() != null) {
			popupStore.updateReservationStartAt(request.reservationStartAt());
		}
		if (request.reservationEndAt() != null) {
			popupStore.updateReservationEndAt(request.reservationEndAt());
		}
		if (request.openDate() != null) {
			popupStore.updateOpenDate(request.openDate());
		}
		if (request.closeDate() != null) {
			popupStore.updateCloseDate(request.closeDate());
		}
		if (s3Service.isTempKey(request.imageKey())) {
			// 기존 이미지는 커밋 성공 후 삭제, 새 임시 이미지는 정식 위치로 이동
			registerAfterCommitDeletion(popupStore.getImageKey());
			String destKey = buildPopupImageKey(popupStoreId, request.imageKey());
			popupStore.updateImageKey(destKey);
			registerAfterCommitMove(request.imageKey(), destKey);
		} else if (request.imageKey() != null) {
			popupStore.updateImageKey(request.imageKey());
		}
		if (request.description() != null) {
			popupStore.updateDescription(request.description());
		}
	}

	/**
	 * 팝업스토어를 삭제한다. (주최자, 소유자만)
	 * 없으면 RESOURCE_NOT_FOUND, 소유자가 아니면 FORBIDDEN,
	 * 운영 시작일(openDate) 이후면 INVALID_INPUT_VALUE.
	 */
	@Transactional
	public void deletePopupStore(Long userId, Long popupStoreId) {
		PopupStore popupStore = popupStoreRepository.findById(popupStoreId)
			.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

		if (!popupStore.getUser().getId().equals(userId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}

		// 운영이 이미 시작됐으면(now >= openDate) 삭제 불가
		if (!LocalDateTime.now().isBefore(popupStore.getOpenDate())) {
			throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
		}

		// 슬롯 먼저 삭제
		reservationSlotRepository.deleteByPopupStoreId(popupStoreId);

		// S3 이미지는 커밋 성공 후 삭제 (DB 롤백 시 파일 유실 방지)
		registerAfterCommitDeletion(popupStore.getImageKey());

		popupStoreRepository.delete(popupStore);
	}

	/**
	 * 예약 슬롯을 생성한다. (주최자, 소유자만)
	 * 없으면 RESOURCE_NOT_FOUND, 소유자가 아니면 FORBIDDEN,
	 * 슬롯 날짜가 운영 기간(openDate~closeDate) 밖이면 INVALID_INPUT_VALUE.
	 */
	@Transactional
	public Long createSlot(Long userId, Long popupStoreId, ReservationSlotCreateRequest request) {
		PopupStore popupStore = popupStoreRepository.findById(popupStoreId)
			.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

		if (!popupStore.getUser().getId().equals(userId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}

		LocalDate openDate = popupStore.getOpenDate().toLocalDate();
		LocalDate closeDate = popupStore.getCloseDate().toLocalDate();
		if (request.slotDate().isBefore(openDate) || request.slotDate().isAfter(closeDate)) {
			throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
		}

		ReservationSlot slot = ReservationSlot.of(popupStore, request);
		reservationSlotRepository.save(slot);
		return slot.getId();
	}

	@Transactional
	public void updateSlot(Long userId, Long popupStoreId, Long slotId, ReservationSlotUpdateRequest request) {
		PopupStore popupStore = popupStoreRepository.findById(popupStoreId)
			.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

		ReservationSlot slot = reservationSlotRepository.findById(slotId)
			.orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_SLOT_NOT_FOUND));

		if (!slot.getPopupStore().getId().equals(popupStoreId)) {
			throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
		}

		if (!popupStore.getUser().getId().equals(userId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}

		if (!LocalDateTime.now().isBefore(popupStore.getReservationStartAt())) {
			throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
		}

		LocalDate targetSlotDate = request.slotDate() != null ? request.slotDate() : slot.getSlotDate();
		LocalTime targetStartTime = request.startTime() != null ? request.startTime() : slot.getStartTime();

		LocalDate openDate = popupStore.getOpenDate().toLocalDate();
		LocalDate closeDate = popupStore.getCloseDate().toLocalDate();
		if (targetSlotDate.isBefore(openDate) || targetSlotDate.isAfter(closeDate)) {
			throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
		}

		if (reservationSlotRepository.existsByPopupStoreIdAndSlotDateAndStartTimeAndIdNot(
			popupStoreId,
			targetSlotDate,
			targetStartTime,
			slotId
		)) {
			throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
		}

		if (request.slotDate() != null) {
			slot.updateSlotDate(request.slotDate());
		}
		if (request.startTime() != null) {
			slot.updateStartTime(request.startTime());
		}
		if (request.capacity() != null) {
			slot.updateCapacity(request.capacity());
		}
	}

	@Transactional
	public void deleteSlot(Long userId, Long popupStoreId, Long slotId) {
		PopupStore popupStore = popupStoreRepository.findById(popupStoreId)
			.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

		ReservationSlot slot = reservationSlotRepository.findById(slotId)
			.orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_SLOT_NOT_FOUND));

		if (!slot.getPopupStore().getId().equals(popupStoreId)) {
			throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
		}

		if (!popupStore.getUser().getId().equals(userId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}

		if (!LocalDateTime.now().isBefore(popupStore.getReservationStartAt())) {
			throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
		}

		reservationSlotRepository.delete(slot);
	}

	// 임시 키(temp/...)의 파일명을 팝업 정식 경로(popup/{id}/{fileName})로 변환
	private String buildPopupImageKey(Long popupStoreId, String tempKey) {
		return "popup/" + popupStoreId + "/" + s3Service.extractFileName(tempKey);
	}

	// 트랜잭션 커밋 성공 후에 S3 이미지를 삭제한다. (롤백 시 파일이 사라지는 것을 방지)
	private void registerAfterCommitDeletion(String key) {
		if (key == null) {
			return;
		}
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			s3Service.delete(key);
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				s3Service.delete(key);
			}
		});
	}

	// 트랜잭션 커밋 성공 후에 S3 이미지를 이동한다. (롤백 시 파일 이동 방지)
	private void registerAfterCommitMove(String srcKey, String destKey) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			try {
				s3Service.move(srcKey, destKey);
			} catch (Exception e) {
				log.error("S3 move 실패 (트랜잭션 외부): srcKey={}, destKey={}", srcKey, destKey, e);
			}
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				try {
					s3Service.move(srcKey, destKey);
				} catch (Exception e) {
					log.error("S3 move 실패 (afterCommit): srcKey={}, destKey={}", srcKey, destKey, e);
				}
			}
		});
	}

}

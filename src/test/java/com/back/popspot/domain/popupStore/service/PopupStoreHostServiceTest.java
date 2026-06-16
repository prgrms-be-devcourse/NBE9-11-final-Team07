package com.back.popspot.domain.popupStore.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
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
import com.back.popspot.global.s3.S3Service;
import com.back.popspot.domain.user.entity.User;
import com.back.popspot.global.exception.BusinessException;
import com.back.popspot.global.exception.ErrorCode;

import jakarta.persistence.EntityManager;

/**
 * PopupStoreHostService 등록 검증/저장 로직 단위 테스트 (repository, entityManager 는 mock).
 */
@ExtendWith(MockitoExtension.class)
class PopupStoreHostServiceTest {

	@Mock
	private PopupStoreRepository popupStoreRepository;

	@Mock
	private ReservationSlotRepository reservationSlotRepository;

	@Mock
	private EntityManager entityManager;

	@Mock
	private S3Service s3Service;

	@InjectMocks
	private PopupStoreHostService popupStoreHostService;

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 13, 12, 0);
	private static final LocalDateTime FUTURE = LocalDateTime.of(2999, 1, 1, 0, 0); // 운영 시작 전
	private static final LocalDateTime PAST = LocalDateTime.of(2000, 1, 1, 0, 0);   // 운영 이미 시작
	private static final Long USER_ID = 1L;

	@BeforeEach
	void setUp() {
		// @InjectMocks 가 생성자 주입을 택하면 @PersistenceContext 필드는 주입되지 않으므로 직접 세팅
		ReflectionTestUtils.setField(popupStoreHostService, "entityManager", entityManager);
	}

	@Test
	@DisplayName("등록: 유효한 요청이면 저장하고 생성된 id 를 반환한다")
	void createPopupStore_valid_savesAndReturnsId() {
		PopupStoreCreateRequest request = request(PopupFeeType.FREE, null,
				NOW, NOW.plusDays(1), NOW.plusDays(2), NOW.plusDays(3));
		when(entityManager.getReference(User.class, USER_ID)).thenReturn(User.create("owner@test.com", "owner"));
		when(popupStoreRepository.save(any(PopupStore.class))).thenAnswer(invocation -> {
			PopupStore saved = invocation.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", 100L);
			return saved;
		});

		Long id = popupStoreHostService.createPopupStore(USER_ID, request);

		assertThat(id).isEqualTo(100L);
		verify(popupStoreRepository).save(any(PopupStore.class));
	}

	@Test
	@DisplayName("등록: PAID 인데 price 가 null 이면 INVALID_INPUT_VALUE")
	void createPopupStore_paidWithoutPrice_throws() {
		PopupStoreCreateRequest request = request(PopupFeeType.PAID, null,
				NOW, NOW.plusDays(1), NOW.plusDays(2), NOW.plusDays(3));

		assertThatThrownBy(() -> popupStoreHostService.createPopupStore(USER_ID, request))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
		verify(popupStoreRepository, never()).save(any());
	}

	@Test
	@DisplayName("등록: PAID 인데 price 가 0 이하면 INVALID_INPUT_VALUE")
	void createPopupStore_paidWithNonPositivePrice_throws() {
		PopupStoreCreateRequest request = request(PopupFeeType.PAID, 0,
				NOW, NOW.plusDays(1), NOW.plusDays(2), NOW.plusDays(3));

		assertThatThrownBy(() -> popupStoreHostService.createPopupStore(USER_ID, request))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
	}

	@Test
	@DisplayName("등록: 예약 시작 >= 종료면 INVALID_INPUT_VALUE (경계: 같을 때도)")
	void createPopupStore_reservationPeriodInverted_throws() {
		PopupStoreCreateRequest request = request(PopupFeeType.FREE, null,
				NOW, NOW, NOW.plusDays(2), NOW.plusDays(3)); // start == end

		assertThatThrownBy(() -> popupStoreHostService.createPopupStore(USER_ID, request))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
	}

	@Test
	@DisplayName("등록: 운영 시작 >= 종료면 INVALID_INPUT_VALUE")
	void createPopupStore_operationPeriodInverted_throws() {
		PopupStoreCreateRequest request = request(PopupFeeType.FREE, null,
				NOW, NOW.plusDays(1), NOW.plusDays(3), NOW.plusDays(2)); // open > close

		assertThatThrownBy(() -> popupStoreHostService.createPopupStore(USER_ID, request))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
	}

	@Test
	@DisplayName("수정: 소유자가 부분 수정하면 null 아닌 필드만 반영된다")
	void updatePopupStore_owner_appliesOnlyNonNullFields() {
		PopupStore popupStore = existingPopup(USER_ID);
		when(popupStoreRepository.findById(10L)).thenReturn(Optional.of(popupStore));

		// title, price 만 수정 (나머지 null)
		PopupStoreUpdateRequest request = new PopupStoreUpdateRequest(
				"새 제목", null, null, 2000, null, null, null, null, null, null);

		popupStoreHostService.updatePopupStore(USER_ID, 10L, request);

		assertThat(popupStore.getTitle()).isEqualTo("새 제목");   // 반영됨
		assertThat(popupStore.getPrice()).isEqualTo(2000);        // 반영됨
		assertThat(popupStore.getLocation()).isEqualTo("기존 위치"); // null 이라 그대로
	}

	@Test
	@DisplayName("수정: 팝업이 없으면 RESOURCE_NOT_FOUND")
	void updatePopupStore_notFound_throws() {
		when(popupStoreRepository.findById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> popupStoreHostService.updatePopupStore(USER_ID, 99L, emptyUpdate()))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
	}

	@Test
	@DisplayName("수정: 소유자가 아니면 FORBIDDEN 이고 값이 바뀌지 않는다")
	void updatePopupStore_notOwner_throwsForbidden() {
		PopupStore popupStore = existingPopup(USER_ID); // 소유자 = 1L
		when(popupStoreRepository.findById(10L)).thenReturn(Optional.of(popupStore));

		PopupStoreUpdateRequest request = new PopupStoreUpdateRequest(
				"새 제목", null, null, null, null, null, null, null, null, null);

		assertThatThrownBy(() -> popupStoreHostService.updatePopupStore(2L, 10L, request)) // 다른 사용자
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.FORBIDDEN);
		assertThat(popupStore.getTitle()).isEqualTo("기존 제목"); // 변경 안 됨
	}

	@Test
	@DisplayName("삭제: 소유자가 운영 시작 전이면 삭제한다")
	void deletePopupStore_ownerBeforeOpen_deletes() {
		PopupStore popupStore = popupWithOpenDate(USER_ID, FUTURE);
		when(popupStoreRepository.findById(10L)).thenReturn(Optional.of(popupStore));

		popupStoreHostService.deletePopupStore(USER_ID, 10L);

		verify(popupStoreRepository).delete(popupStore);
	}

	@Test
	@DisplayName("삭제: 팝업이 없으면 RESOURCE_NOT_FOUND")
	void deletePopupStore_notFound_throws() {
		when(popupStoreRepository.findById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> popupStoreHostService.deletePopupStore(USER_ID, 99L))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
	}

	@Test
	@DisplayName("삭제: 소유자가 아니면 FORBIDDEN 이고 삭제하지 않는다")
	void deletePopupStore_notOwner_throwsForbidden() {
		PopupStore popupStore = popupWithOpenDate(USER_ID, FUTURE); // 소유자 1L
		when(popupStoreRepository.findById(10L)).thenReturn(Optional.of(popupStore));

		assertThatThrownBy(() -> popupStoreHostService.deletePopupStore(2L, 10L)) // 다른 사용자
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.FORBIDDEN);
		verify(popupStoreRepository, never()).delete(any());
	}

	@Test
	@DisplayName("삭제: 운영이 이미 시작됐으면 INVALID_INPUT_VALUE 이고 삭제하지 않는다")
	void deletePopupStore_afterOpen_throws() {
		PopupStore popupStore = popupWithOpenDate(USER_ID, PAST); // openDate 가 과거 = 운영 시작됨
		when(popupStoreRepository.findById(10L)).thenReturn(Optional.of(popupStore));

		assertThatThrownBy(() -> popupStoreHostService.deletePopupStore(USER_ID, 10L))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
		verify(popupStoreRepository, never()).delete(any());
	}

	@Test
	@DisplayName("슬롯 생성: 소유자 + 운영 기간 내 날짜면 저장하고 id 를 반환한다")
	void createSlot_validOwnerInRange_savesAndReturnsId() {
		when(popupStoreRepository.findById(10L)).thenReturn(Optional.of(popupForSlot(USER_ID)));
		when(reservationSlotRepository.save(any(ReservationSlot.class))).thenAnswer(invocation -> {
			ReservationSlot saved = invocation.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", 500L);
			return saved;
		});

		Long id = popupStoreHostService.createSlot(USER_ID, 10L, slotRequest(LocalDate.of(2026, 7, 5)));

		assertThat(id).isEqualTo(500L);
		verify(reservationSlotRepository).save(any(ReservationSlot.class));
	}

	@Test
	@DisplayName("슬롯 생성: 팝업이 없으면 RESOURCE_NOT_FOUND")
	void createSlot_notFound_throws() {
		when(popupStoreRepository.findById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> popupStoreHostService.createSlot(USER_ID, 99L, slotRequest(LocalDate.of(2026, 7, 5))))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
	}

	@Test
	@DisplayName("슬롯 생성: 소유자가 아니면 FORBIDDEN 이고 저장하지 않는다")
	void createSlot_notOwner_throwsForbidden() {
		when(popupStoreRepository.findById(10L)).thenReturn(Optional.of(popupForSlot(USER_ID)));

		assertThatThrownBy(() -> popupStoreHostService.createSlot(2L, 10L, slotRequest(LocalDate.of(2026, 7, 5))))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.FORBIDDEN);
		verify(reservationSlotRepository, never()).save(any());
	}

	@Test
	@DisplayName("슬롯 생성: slotDate 가 운영 기간 밖이면 INVALID_INPUT_VALUE 이고 저장하지 않는다")
	void createSlot_dateOutOfRange_throws() {
		when(popupStoreRepository.findById(10L)).thenReturn(Optional.of(popupForSlot(USER_ID)));

		// 운영기간 2026-07-01 ~ 07-10, 슬롯 날짜 07-11 (마감 이후)
		assertThatThrownBy(() -> popupStoreHostService.createSlot(USER_ID, 10L, slotRequest(LocalDate.of(2026, 7, 11))))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
		verify(reservationSlotRepository, never()).save(any());
	}

	@Test
	@DisplayName("슬롯 수정: 소유자 + 예약 시작 전 + 운영 기간 내 날짜면 수정한다")
	void updateSlot_validOwnerBeforeReservationStart_updates() {
		PopupStore popupStore = popupForSlotManagement(USER_ID, 10L, FUTURE);
		ReservationSlot slot = slot(popupStore, LocalDate.of(2026, 7, 5), LocalTime.of(13, 0), 10);
		when(popupStoreRepository.findById(10L)).thenReturn(Optional.of(popupStore));
		when(reservationSlotRepository.findById(500L)).thenReturn(Optional.of(slot));

		ReservationSlotUpdateRequest request = new ReservationSlotUpdateRequest(
				LocalDate.of(2026, 7, 6), LocalTime.of(14, 0), 20);

		popupStoreHostService.updateSlot(USER_ID, 10L, 500L, request);

		assertThat(slot.getSlotDate()).isEqualTo(LocalDate.of(2026, 7, 6));
		assertThat(slot.getStartTime()).isEqualTo(LocalTime.of(14, 0));
		assertThat(slot.getCapacity()).isEqualTo(20);
	}

	@Test
	@DisplayName("슬롯 수정: 소유자가 아니면 FORBIDDEN 이고 수정하지 않는다")
	void updateSlot_notOwner_throwsForbidden() {
		PopupStore popupStore = popupForSlotManagement(USER_ID, 10L, FUTURE);
		ReservationSlot slot = slot(popupStore, LocalDate.of(2026, 7, 5), LocalTime.of(13, 0), 10);
		when(popupStoreRepository.findById(10L)).thenReturn(Optional.of(popupStore));
		when(reservationSlotRepository.findById(500L)).thenReturn(Optional.of(slot));

		ReservationSlotUpdateRequest request = new ReservationSlotUpdateRequest(
				LocalDate.of(2026, 7, 6), null, null);

		assertThatThrownBy(() -> popupStoreHostService.updateSlot(2L, 10L, 500L, request))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.FORBIDDEN);
		assertThat(slot.getSlotDate()).isEqualTo(LocalDate.of(2026, 7, 5));
	}

	@Test
	@DisplayName("슬롯 수정: 수정 후 slotDate 가 운영 기간 밖이면 INVALID_INPUT_VALUE")
	void updateSlot_dateOutOfRange_throws() {
		PopupStore popupStore = popupForSlotManagement(USER_ID, 10L, FUTURE);
		ReservationSlot slot = slot(popupStore, LocalDate.of(2026, 7, 5), LocalTime.of(13, 0), 10);
		when(popupStoreRepository.findById(10L)).thenReturn(Optional.of(popupStore));
		when(reservationSlotRepository.findById(500L)).thenReturn(Optional.of(slot));

		ReservationSlotUpdateRequest request = new ReservationSlotUpdateRequest(
				LocalDate.of(2026, 7, 11), null, null);

		assertThatThrownBy(() -> popupStoreHostService.updateSlot(USER_ID, 10L, 500L, request))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
		assertThat(slot.getSlotDate()).isEqualTo(LocalDate.of(2026, 7, 5));
	}

	@Test
	@DisplayName("슬롯 삭제: 소유자 + 예약 시작 전이면 삭제한다")
	void deleteSlot_validOwnerBeforeReservationStart_deletes() {
		PopupStore popupStore = popupForSlotManagement(USER_ID, 10L, FUTURE);
		ReservationSlot slot = slot(popupStore, LocalDate.of(2026, 7, 5), LocalTime.of(13, 0), 10);
		when(popupStoreRepository.findById(10L)).thenReturn(Optional.of(popupStore));
		when(reservationSlotRepository.findById(500L)).thenReturn(Optional.of(slot));

		popupStoreHostService.deleteSlot(USER_ID, 10L, 500L);

		verify(reservationSlotRepository).delete(slot);
	}

	@Test
	@DisplayName("슬롯 삭제: 소유자가 아니면 FORBIDDEN 이고 삭제하지 않는다")
	void deleteSlot_notOwner_throwsForbidden() {
		PopupStore popupStore = popupForSlotManagement(USER_ID, 10L, FUTURE);
		ReservationSlot slot = slot(popupStore, LocalDate.of(2026, 7, 5), LocalTime.of(13, 0), 10);
		when(popupStoreRepository.findById(10L)).thenReturn(Optional.of(popupStore));
		when(reservationSlotRepository.findById(500L)).thenReturn(Optional.of(slot));

		assertThatThrownBy(() -> popupStoreHostService.deleteSlot(2L, 10L, 500L))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.FORBIDDEN);
		verify(reservationSlotRepository, never()).delete(any());
	}

	@Test
	@DisplayName("슬롯 삭제: 예약 시작 이후면 INVALID_INPUT_VALUE 이고 삭제하지 않는다")
	void deleteSlot_afterReservationStart_throws() {
		PopupStore popupStore = popupForSlotManagement(USER_ID, 10L, PAST);
		ReservationSlot slot = slot(popupStore, LocalDate.of(2026, 7, 5), LocalTime.of(13, 0), 10);
		when(popupStoreRepository.findById(10L)).thenReturn(Optional.of(popupStore));
		when(reservationSlotRepository.findById(500L)).thenReturn(Optional.of(slot));

		assertThatThrownBy(() -> popupStoreHostService.deleteSlot(USER_ID, 10L, 500L))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
		verify(reservationSlotRepository, never()).delete(any());
	}

	@Test
	@DisplayName("S3-등록: 임시 이미지면 popup/{id}/{file} 로 move 하고 키를 갱신한다")
	void createPopupStore_tempImage_movesAndUpdatesKey() {
		PopupStoreCreateRequest request = new PopupStoreCreateRequest(
				"제목", "위치", PopupFeeType.FREE, null,
				NOW, NOW.plusDays(1), NOW.plusDays(2), NOW.plusDays(3),
				"temp/abc.jpg", "설명");
		when(entityManager.getReference(User.class, USER_ID)).thenReturn(User.create("h@test.com", "h"));
		PopupStore[] holder = new PopupStore[1];
		when(popupStoreRepository.save(any(PopupStore.class))).thenAnswer(invocation -> {
			PopupStore saved = invocation.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", 100L);
			holder[0] = saved;
			return saved;
		});
		when(s3Service.isTempKey("temp/abc.jpg")).thenReturn(true);
		when(s3Service.extractFileName("temp/abc.jpg")).thenReturn("abc.jpg");

		popupStoreHostService.createPopupStore(USER_ID, request);

		verify(s3Service).move("temp/abc.jpg", "popup/100/abc.jpg");
		assertThat(holder[0].getImageKey()).isEqualTo("popup/100/abc.jpg");
	}

	@Test
	@DisplayName("S3-삭제: 팝업 이미지 삭제는 트랜잭션 커밋 후(afterCommit)에 실행된다")
	void deletePopupStore_deletesImageAfterCommit() {
		PopupStore popupStore = popupWithOpenDate(USER_ID, FUTURE);
		ReflectionTestUtils.setField(popupStore, "imageKey", "popup/10/img.jpg");
		when(popupStoreRepository.findById(10L)).thenReturn(Optional.of(popupStore));

		TransactionSynchronizationManager.initSynchronization();
		try {
			popupStoreHostService.deletePopupStore(USER_ID, 10L);

			// 커밋 전: 아직 S3 삭제 안 함
			verify(s3Service, never()).delete(any());

			// 등록된 동기화의 afterCommit 수동 실행
			TransactionSynchronizationManager.getSynchronizations()
					.forEach(TransactionSynchronization::afterCommit);

			// 커밋 후: S3 삭제됨
			verify(s3Service).delete("popup/10/img.jpg");
		} finally {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}

	@Test
	@DisplayName("S3-수정: 임시 이미지면 새 이미지 move + 기존 이미지는 커밋 후 삭제")
	void updatePopupStore_tempImage_movesNewAndDeletesOldAfterCommit() {
		PopupStore popupStore = existingPopup(USER_ID);
		ReflectionTestUtils.setField(popupStore, "imageKey", "popup/10/old.jpg");
		when(popupStoreRepository.findById(10L)).thenReturn(Optional.of(popupStore));
		when(s3Service.isTempKey("temp/new.jpg")).thenReturn(true);
		when(s3Service.extractFileName("temp/new.jpg")).thenReturn("new.jpg");

		PopupStoreUpdateRequest request = new PopupStoreUpdateRequest(
				null, null, null, null, null, null, null, null, "temp/new.jpg", null);

		TransactionSynchronizationManager.initSynchronization();
		try {
			popupStoreHostService.updatePopupStore(USER_ID, 10L, request);

			// 새 이미지는 즉시 move + 키 갱신
			verify(s3Service).move("temp/new.jpg", "popup/10/new.jpg");
			assertThat(popupStore.getImageKey()).isEqualTo("popup/10/new.jpg");
			// 기존 이미지는 커밋 전엔 삭제 안 함
			verify(s3Service, never()).delete(any());

			TransactionSynchronizationManager.getSynchronizations()
					.forEach(TransactionSynchronization::afterCommit);

			// 커밋 후: 기존 이미지 삭제
			verify(s3Service).delete("popup/10/old.jpg");
		} finally {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}

	private PopupStore popupForSlot(Long ownerId) {
		User owner = User.create("owner@test.com", "owner");
		ReflectionTestUtils.setField(owner, "id", ownerId);
		PopupStore popupStore = new PopupStore();
		ReflectionTestUtils.setField(popupStore, "user", owner);
		ReflectionTestUtils.setField(popupStore, "openDate", LocalDateTime.of(2026, 7, 1, 10, 0));
		ReflectionTestUtils.setField(popupStore, "closeDate", LocalDateTime.of(2026, 7, 10, 18, 0));
		return popupStore;
	}

	private PopupStore popupForSlotManagement(Long ownerId, Long popupStoreId, LocalDateTime reservationStartAt) {
		PopupStore popupStore = popupForSlot(ownerId);
		ReflectionTestUtils.setField(popupStore, "id", popupStoreId);
		ReflectionTestUtils.setField(popupStore, "reservationStartAt", reservationStartAt);
		return popupStore;
	}

	private ReservationSlot slot(PopupStore popupStore, LocalDate slotDate, LocalTime startTime, int capacity) {
		ReservationSlot slot = new ReservationSlot();
		ReflectionTestUtils.setField(slot, "popupStore", popupStore);
		ReflectionTestUtils.setField(slot, "slotDate", slotDate);
		ReflectionTestUtils.setField(slot, "startTime", startTime);
		ReflectionTestUtils.setField(slot, "capacity", capacity);
		return slot;
	}

	private ReservationSlotCreateRequest slotRequest(LocalDate slotDate) {
		return new ReservationSlotCreateRequest(slotDate, LocalTime.of(13, 0), 10);
	}

	private PopupStore popupWithOpenDate(Long ownerId, LocalDateTime openDate) {
		User owner = User.create("owner@test.com", "owner");
		ReflectionTestUtils.setField(owner, "id", ownerId);
		PopupStore popupStore = new PopupStore();
		ReflectionTestUtils.setField(popupStore, "user", owner);
		ReflectionTestUtils.setField(popupStore, "openDate", openDate);
		return popupStore;
	}

	private PopupStore existingPopup(Long ownerId) {
		User owner = User.create("owner@test.com", "owner");
		ReflectionTestUtils.setField(owner, "id", ownerId);
		PopupStore popupStore = new PopupStore();
		ReflectionTestUtils.setField(popupStore, "user", owner);
		ReflectionTestUtils.setField(popupStore, "title", "기존 제목");
		ReflectionTestUtils.setField(popupStore, "location", "기존 위치");
		ReflectionTestUtils.setField(popupStore, "price", 1000);
		return popupStore;
	}

	private PopupStoreUpdateRequest emptyUpdate() {
		return new PopupStoreUpdateRequest(null, null, null, null, null, null, null, null, null, null);
	}

	private PopupStoreCreateRequest request(PopupFeeType feeType, Integer price,
			LocalDateTime reservationStartAt, LocalDateTime reservationEndAt,
			LocalDateTime openDate, LocalDateTime closeDate) {
		return new PopupStoreCreateRequest(
				"title", "location", feeType, price,
				reservationStartAt, reservationEndAt, openDate, closeDate,
				"imageKey", "description");
	}
}

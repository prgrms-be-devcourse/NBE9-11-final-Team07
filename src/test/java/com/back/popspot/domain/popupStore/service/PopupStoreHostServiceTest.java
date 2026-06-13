package com.back.popspot.domain.popupStore.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.back.popspot.domain.popupStore.dto.PopupStoreCreateRequest;
import com.back.popspot.domain.popupStore.entity.PopupFeeType;
import com.back.popspot.domain.popupStore.entity.PopupStore;
import com.back.popspot.domain.popupStore.repository.PopupStoreRepository;
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
	private EntityManager entityManager;

	@InjectMocks
	private PopupStoreHostService popupStoreHostService;

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 13, 12, 0);
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
		when(entityManager.getReference(User.class, USER_ID)).thenReturn(new User());
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

	private PopupStoreCreateRequest request(PopupFeeType feeType, Integer price,
			LocalDateTime reservationStartAt, LocalDateTime reservationEndAt,
			LocalDateTime openDate, LocalDateTime closeDate) {
		return new PopupStoreCreateRequest(
				"title", "location", feeType, price,
				reservationStartAt, reservationEndAt, openDate, closeDate,
				"imageKey", "description");
	}
}

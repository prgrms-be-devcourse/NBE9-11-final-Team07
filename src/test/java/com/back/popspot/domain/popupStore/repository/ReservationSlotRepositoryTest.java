package com.back.popspot.domain.popupStore.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.util.ReflectionTestUtils;

import com.back.popspot.domain.popupStore.entity.PopupFeeType;
import com.back.popspot.domain.popupStore.entity.PopupStore;
import com.back.popspot.domain.popupStore.entity.ReservationSlot;
import com.back.popspot.domain.user.entity.User;
import com.back.popspot.domain.user.entity.UserRole;

/**
 * findByPopupStoreIdAndSlotDate 파생 쿼리가 popupStoreId + slotDate 로 정확히 거르는지 실제 H2 로 검증.
 */
@DataJpaTest
class ReservationSlotRepositoryTest {

	@Autowired
	private TestEntityManager entityManager;

	@Autowired
	private ReservationSlotRepository reservationSlotRepository;

	private static final LocalDate DATE = LocalDate.of(2026, 6, 14);

	private PopupStore popupA;
	private PopupStore popupB;
	private ReservationSlot slotA_onDate;      // popupA, DATE        → 조회 대상
	private ReservationSlot slotA_otherDate;   // popupA, DATE + 1일  → 날짜 달라 제외
	private ReservationSlot slotB_onDate;      // popupB, DATE        → 팝업 달라 제외

	@BeforeEach
	void setUp() {
		User user = persistUser();
		popupA = persistPopup(user);
		popupB = persistPopup(user);

		slotA_onDate = persistSlot(popupA, DATE);
		slotA_otherDate = persistSlot(popupA, DATE.plusDays(1));
		slotB_onDate = persistSlot(popupB, DATE);

		entityManager.flush();
		entityManager.clear();
	}

	@Test
	@DisplayName("해당 팝업 + 해당 날짜의 슬롯만 조회한다 (다른 팝업/다른 날짜 제외)")
	void findByPopupStoreIdAndSlotDate() {
		List<ReservationSlot> result =
				reservationSlotRepository.findByPopupStoreIdAndSlotDate(popupA.getId(), DATE);

		assertThat(result)
				.extracting(ReservationSlot::getId)
				.containsExactly(slotA_onDate.getId());
	}

	@Test
	@DisplayName("해당 날짜에 슬롯이 없으면 빈 리스트")
	void findByPopupStoreIdAndSlotDate_noMatch_returnsEmpty() {
		List<ReservationSlot> result =
				reservationSlotRepository.findByPopupStoreIdAndSlotDate(popupA.getId(), DATE.plusDays(10));

		assertThat(result).isEmpty();
	}

	private User persistUser() {
		User user = new User();
		ReflectionTestUtils.setField(user, "email", "owner@test.com");
		ReflectionTestUtils.setField(user, "name", "owner");
		ReflectionTestUtils.setField(user, "role", UserRole.ORGANIZER);
		return entityManager.persist(user);
	}

	private PopupStore persistPopup(User user) {
		PopupStore popupStore = new PopupStore();
		ReflectionTestUtils.setField(popupStore, "user", user);
		ReflectionTestUtils.setField(popupStore, "title", "title");
		ReflectionTestUtils.setField(popupStore, "location", "location");
		ReflectionTestUtils.setField(popupStore, "feeType", PopupFeeType.FREE);
		ReflectionTestUtils.setField(popupStore, "reservationStartAt", LocalDateTime.of(2026, 6, 1, 0, 0));
		ReflectionTestUtils.setField(popupStore, "reservationEndAt", LocalDateTime.of(2026, 6, 30, 0, 0));
		return entityManager.persist(popupStore);
	}

	private ReservationSlot persistSlot(PopupStore popupStore, LocalDate slotDate) {
		ReservationSlot slot = new ReservationSlot();
		ReflectionTestUtils.setField(slot, "popupStore", popupStore);
		ReflectionTestUtils.setField(slot, "slotDate", slotDate);
		ReflectionTestUtils.setField(slot, "startTime", LocalTime.of(10, 0));
		ReflectionTestUtils.setField(slot, "capacity", 10);
		ReflectionTestUtils.setField(slot, "reservedCount", 0);
		return entityManager.persist(slot);
	}
}

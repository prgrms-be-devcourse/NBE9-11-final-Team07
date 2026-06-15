package com.back.popspot.domain.popupStore.repository;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import com.back.popspot.domain.popupStore.entity.PopupFeeType;
import com.back.popspot.domain.popupStore.entity.PopupStore;
import com.back.popspot.domain.user.entity.User;

/**
 * status 별 @Query(JPQL)가 예약 기간 조건대로 필터링하는지 실제 H2 로 검증.
 */
@DataJpaTest
class PopupStoreRepositoryTest {

	@Autowired
	private TestEntityManager entityManager;

	@Autowired
	private PopupStoreRepository popupStoreRepository;

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 13, 12, 0);
	private static final PageRequest PAGE = PageRequest.of(0, 10);

	private PopupStore upcoming; // start = now+1d, end = now+2d
	private PopupStore open;     // start = now-1d, end = now+1d
	private PopupStore closed;   // start = now-2d, end = now-1d

	@BeforeEach
	void setUp() {
		User user = persistUser();
		upcoming = persistPopup(user, NOW.plusDays(1), NOW.plusDays(2));
		open = persistPopup(user, NOW.minusDays(1), NOW.plusDays(1));
		closed = persistPopup(user, NOW.minusDays(2), NOW.minusDays(1));
		entityManager.flush();
		entityManager.clear();
	}

	@Test
	@DisplayName("findUpcoming: 예약 시작 전(start > now) 팝업만 조회")
	void findUpcoming() {
		Page<PopupStore> result = popupStoreRepository.findUpcoming(NOW, PAGE);

		assertThat(result.getContent())
			.extracting(PopupStore::getId)
			.containsExactly(upcoming.getId());
	}

	@Test
	@DisplayName("findOpen: 예약 진행 중(start <= now < end) 팝업만 조회")
	void findOpen() {
		Page<PopupStore> result = popupStoreRepository.findOpen(NOW, PAGE);

		assertThat(result.getContent())
			.extracting(PopupStore::getId)
			.containsExactly(open.getId());
	}

	@Test
	@DisplayName("findClosed: 예약 종료(end <= now) 팝업만 조회")
	void findClosed() {
		Page<PopupStore> result = popupStoreRepository.findClosed(NOW, PAGE);

		assertThat(result.getContent())
			.extracting(PopupStore::getId)
			.containsExactly(closed.getId());
	}

	private User persistUser() {
		return entityManager.persist(User.create("owner@test.com", "owner"));
	}

	private PopupStore persistPopup(User user, LocalDateTime reservationStartAt, LocalDateTime reservationEndAt) {
		PopupStore popupStore = new PopupStore();
		ReflectionTestUtils.setField(popupStore, "user", user);
		ReflectionTestUtils.setField(popupStore, "title", "title");
		ReflectionTestUtils.setField(popupStore, "location", "location");
		ReflectionTestUtils.setField(popupStore, "feeType", PopupFeeType.FREE);
		ReflectionTestUtils.setField(popupStore, "reservationStartAt", reservationStartAt);
		ReflectionTestUtils.setField(popupStore, "reservationEndAt", reservationEndAt);
		return entityManager.persist(popupStore);
	}
}

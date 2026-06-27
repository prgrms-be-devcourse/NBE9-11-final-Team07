package com.back.popspot.domain.reservation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

import com.back.popspot.domain.payment.entity.Payment;
import com.back.popspot.domain.payment.entity.PaymentStatus;
import com.back.popspot.domain.payment.repository.PaymentRepository;
import com.back.popspot.domain.popupStore.dto.PopupStoreCreateRequest;
import com.back.popspot.domain.popupStore.dto.ReservationSlotCreateRequest;
import com.back.popspot.domain.popupStore.entity.PopupFeeType;
import com.back.popspot.domain.popupStore.entity.PopupStore;
import com.back.popspot.domain.popupStore.entity.ReservationSlot;
import com.back.popspot.domain.popupStore.repository.PopupStoreRepository;
import com.back.popspot.domain.popupStore.repository.ReservationSlotRepository;
import com.back.popspot.domain.reservation.entity.Reservation;
import com.back.popspot.domain.reservation.entity.ReservationStatus;
import com.back.popspot.domain.reservation.entity.ReservationWaitlist;
import com.back.popspot.domain.reservation.repository.ReservationRepository;
import com.back.popspot.domain.reservation.repository.ReservationWaitlistRepository;
import com.back.popspot.domain.user.entity.User;
import com.back.popspot.domain.user.repository.UserRepository;
import com.back.popspot.global.redis.RedisKeys;
import com.back.popspot.support.IntegrationTestSupport;

import jakarta.persistence.EntityManager;

@DisplayName("예약 통합 테스트")
class ReservationIntegrationTest extends IntegrationTestSupport {

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PopupStoreRepository popupStoreRepository;

	@Autowired
	private ReservationSlotRepository reservationSlotRepository;

	@Autowired
	private ReservationRepository reservationRepository;

	@Autowired
	private ReservationWaitlistRepository reservationWaitlistRepository;

	@Autowired
	private PaymentRepository paymentRepository;

	@Autowired
	private RedisTemplate<String, Long> redisTemplate;

	@Autowired
	private StringRedisTemplate stringRedisTemplate;

	// 테스트에서 초기화한 재고 카운터 키를 추적해 @AfterEach 에서 정리한다.
	// (Redis 는 JPA 트랜잭션 롤백 대상이 아니라 키가 남기 때문에 직접 지워야 한다.)
	private final List<Long> persistedSlotIds = new ArrayList<>();
	private final List<String> proceedFlagKeys = new ArrayList<>();

	@AfterEach
	void cleanUpRedisCounters() {
		for (Long slotId : persistedSlotIds) {
			redisTemplate.delete(RedisKeys.reservationSlotRemaining(slotId));
		}
		persistedSlotIds.clear();

		for (String key : proceedFlagKeys) {
			stringRedisTemplate.delete(key);
		}
		proceedFlagKeys.clear();
	}

	@Test
	@DisplayName("예약 선점에 성공하면 HELD 예약을 저장하고 남은 재고(remaining)를 차감한다")
	void createReservation_success() throws Exception {
		User user = persistUser("user@test.com");
		PopupStore popup = persistPopup(user, PopupFeeType.FREE, null, "무료 팝업");
		ReservationSlot slot = persistSlot(popup, 10, 0);
		grantProceedPermission(popup.getId(), user.getId());

		mockMvc.perform(post("/reservations")
				.with(authentication(auth(user.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "slotId": %d
					}
					""".formatted(slot.getId())))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.code").value("SUCCESS"))
			.andExpect(jsonPath("$.data.status").value("HELD"))
			.andExpect(jsonPath("$.data.slotId").value(slot.getId()));

		flushAndClear();

		List<Reservation> reservations = reservationRepository.findAll();
		Reservation savedReservation = reservations.get(0);

		assertThat(reservations).hasSize(1);
		assertThat(savedReservation.getUser().getId()).isEqualTo(user.getId());
		assertThat(savedReservation.getStatus()).isEqualTo(ReservationStatus.HELD);
		// 단일 카운터: 재고의 source of truth 는 Redis remaining (10 → 9 차감)
		Long remaining = redisTemplate.opsForValue().get(RedisKeys.reservationSlotRemaining(slot.getId()));
		assertThat(remaining).isEqualTo(9L);
		assertThat(savedReservation.getActiveUniqueKey()).isEqualTo(1);
	}

	@Test
	@DisplayName("취소 또는 만료 이력만 있으면 같은 슬롯을 다시 예약할 수 있다")
	void createReservation_success_whenOnlyInactiveReservationsExist() throws Exception {
		User user = persistUser("inactive-history@test.com");
		PopupStore popup = persistPopup(user, PopupFeeType.FREE, null, "재예약 팝업");
		ReservationSlot slot = persistSlot(popup, 10, 0);
		grantProceedPermission(popup.getId(), user.getId());
		Reservation canceled = persistReservation(user, slot, ReservationStatus.CANCELED);
		Reservation expired = persistReservation(user, slot, ReservationStatus.EXPIRED);

		flushAndClear();

		mockMvc.perform(post("/reservations")
				.with(authentication(auth(user.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "slotId": %d
					}
					""".formatted(slot.getId())))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.code").value("SUCCESS"))
			.andExpect(jsonPath("$.data.status").value("HELD"))
			.andExpect(jsonPath("$.data.slotId").value(slot.getId()));

		flushAndClear();

		List<Reservation> reservations = reservationRepository.findAll();
		Reservation savedCanceled = reservationRepository.findById(canceled.getId()).orElseThrow();
		Reservation savedExpired = reservationRepository.findById(expired.getId()).orElseThrow();

		assertThat(reservations).hasSize(3);
		assertThat(savedCanceled.getActiveUniqueKey()).isNull();
		assertThat(savedExpired.getActiveUniqueKey()).isNull();
		assertThat(reservations)
			.filteredOn(reservation -> reservation.getStatus() == ReservationStatus.HELD)
			.hasSize(1)
			.allSatisfy(reservation -> assertThat(reservation.getActiveUniqueKey()).isEqualTo(1));
		Long remaining = redisTemplate.opsForValue().get(RedisKeys.reservationSlotRemaining(slot.getId()));
		assertThat(remaining).isEqualTo(9L);
	}

	@Test
	@DisplayName("정원 초과 시 기존 409 응답을 유지하고 대기 row를 생성한다")
	void createReservation_capacityExceeded_registersWaitlist() throws Exception {
		User user = persistUser("waitlist@test.com");
		PopupStore popup = persistPopup(user, PopupFeeType.FREE, null, "대기 팝업");
		ReservationSlot slot = persistSlot(popup, 1, 1);
		grantProceedPermission(popup.getId(), user.getId());

		mockMvc.perform(post("/reservations")
				.with(authentication(auth(user.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "slotId": %d
					}
					""".formatted(slot.getId())))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RESERVATION_CAPACITY_EXCEEDED"));

		flushAndClear();

		assertThat(reservationWaitlistRepository.existsByUserIdAndSlotId(user.getId(), slot.getId())).isTrue();
		assertThat(reservationWaitlistRepository.countBySlotId(slot.getId())).isEqualTo(1);
	}

	@Test
	@DisplayName("이미 대기 row가 있으면 정원 초과 재요청에도 중복 생성하지 않는다")
	void createReservation_capacityExceeded_skipDuplicateWaitlist() throws Exception {
		User user = persistUser("waitlist-duplicate@test.com");
		PopupStore popup = persistPopup(user, PopupFeeType.FREE, null, "대기 중복 팝업");
		ReservationSlot slot = persistSlot(popup, 10, 10);
		persistWaitlist(user, slot);
		grantProceedPermission(popup.getId(), user.getId());

		mockMvc.perform(post("/reservations")
				.with(authentication(auth(user.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "slotId": %d
					}
					""".formatted(slot.getId())))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RESERVATION_CAPACITY_EXCEEDED"));

		flushAndClear();

		assertThat(reservationWaitlistRepository.countBySlotId(slot.getId())).isEqualTo(1);
	}

	@Test
	@DisplayName("대기열이 슬롯 정원만큼 차 있으면 추가 대기 row를 생성하지 않는다")
	void createReservation_capacityExceeded_skipWhenWaitlistFull() throws Exception {
		User owner = persistUser("waitlist-full-owner@test.com");
		User waitingUser = persistUser("waitlist-full-existing@test.com");
		User requestUser = persistUser("waitlist-full-request@test.com");
		PopupStore popup = persistPopup(owner, PopupFeeType.FREE, null, "대기 만석 팝업");
		ReservationSlot slot = persistSlot(popup, 1, 1);
		persistWaitlist(waitingUser, slot);
		grantProceedPermission(popup.getId(), requestUser.getId());

		mockMvc.perform(post("/reservations")
				.with(authentication(auth(requestUser.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "slotId": %d
					}
					""".formatted(slot.getId())))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RESERVATION_CAPACITY_EXCEEDED"));

		flushAndClear();

		assertThat(reservationWaitlistRepository.countBySlotId(slot.getId())).isEqualTo(1);
		assertThat(reservationWaitlistRepository.existsByUserIdAndSlotId(requestUser.getId(), slot.getId())).isFalse();
	}

	@Test
	@DisplayName("정원 초과 외 실패에서는 대기 row를 생성하지 않는다")
	void createReservation_nonCapacityFailure_doesNotRegisterWaitlist() throws Exception {
		User user = persistUser("waitlist-no-admission@test.com");
		PopupStore popup = persistPopup(user, PopupFeeType.FREE, null, "대기 미대상 팝업");
		ReservationSlot slot = persistSlot(popup, 1, 1);

		mockMvc.perform(post("/reservations")
				.with(authentication(auth(user.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "slotId": %d
					}
					""".formatted(slot.getId())))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("RESERVATION_ADMISSION_REQUIRED"));

		flushAndClear();

		assertThat(reservationWaitlistRepository.countBySlotId(slot.getId())).isZero();
	}

	@Test
	@DisplayName("대기 row가 있어도 HELD 예약 생성만으로는 삭제하지 않는다")
	void createReservation_success_doesNotDeleteWaitlistOnHeld() throws Exception {
		User user = persistUser("waitlist-held@test.com");
		PopupStore popup = persistPopup(user, PopupFeeType.FREE, null, "대기 유지 팝업");
		ReservationSlot slot = persistSlot(popup, 1, 0);
		persistWaitlist(user, slot);
		grantProceedPermission(popup.getId(), user.getId());

		mockMvc.perform(post("/reservations")
				.with(authentication(auth(user.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "slotId": %d
					}
					""".formatted(slot.getId())))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.status").value("HELD"));

		flushAndClear();

		assertThat(reservationWaitlistRepository.existsByUserIdAndSlotId(user.getId(), slot.getId())).isTrue();
	}

	@Test
	@DisplayName("무료 예약 결제 시작에 성공하면 예약자 정보를 저장하고 예약을 확정한다")
	void startFreeReservationPayment_success() throws Exception {
		User user = persistUser("free@test.com");
		ReservationSlot slot = persistSlot(persistPopup(user, PopupFeeType.FREE, null, "무료 팝업"), 10, 1);
		Reservation reservation = persistReservation(user, slot, ReservationStatus.HELD);

		mockMvc.perform(post("/reservations/{reservationId}/payments", reservation.getId())
				.with(authentication(auth(user.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "홍길동",
					  "phone": "010-1234-5678",
					  "idempotencyKey": "free-payment-key"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("SUCCESS"))
			.andExpect(jsonPath("$.data.reservationId").value(reservation.getId()))
			.andExpect(jsonPath("$.data.status").value("CONFIRMED"));

		flushAndClear();

		Reservation savedReservation = reservationRepository.findById(reservation.getId()).orElseThrow();

		assertThat(savedReservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
		assertThat(savedReservation.getReservationName()).isEqualTo("홍길동");
		assertThat(savedReservation.getReservationPhone()).isEqualTo("010-1234-5678");
		assertThat(paymentRepository.findAll()).isEmpty();
	}

	@Test
	@DisplayName("무료 예약이 확정되면 같은 사용자와 슬롯의 대기 row를 삭제한다")
	void startFreeReservationPayment_success_deletesWaitlist() throws Exception {
		User user = persistUser("free-waitlist@test.com");
		ReservationSlot slot = persistSlot(persistPopup(user, PopupFeeType.FREE, null, "무료 대기 팝업"), 10, 1);
		Reservation reservation = persistReservation(user, slot, ReservationStatus.HELD);
		persistWaitlist(user, slot);

		mockMvc.perform(post("/reservations/{reservationId}/payments", reservation.getId())
				.with(authentication(auth(user.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "홍길동",
					  "phone": "010-1234-5678",
					  "idempotencyKey": "free-waitlist-payment-key"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("CONFIRMED"));

		flushAndClear();

		assertThat(reservationWaitlistRepository.existsByUserIdAndSlotId(user.getId(), slot.getId())).isFalse();
	}

	@Test
	@DisplayName("유료 예약 결제 시작에 성공하면 READY 결제를 만들고 예약은 HELD로 유지한다")
	void startPaidReservationPayment_success() throws Exception {
		User user = persistUser("paid@test.com");
		PopupStore popupStore = persistPopup(user, PopupFeeType.PAID, 15000, "유료 팝업");
		ReservationSlot slot = persistSlot(popupStore, 10, 1);
		Reservation reservation = persistReservation(user, slot, ReservationStatus.HELD);

		mockMvc.perform(post("/reservations/{reservationId}/payments", reservation.getId())
				.with(authentication(auth(user.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "홍길동",
					  "phone": "010-1234-5678",
					  "idempotencyKey": "paid-payment-key"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("SUCCESS"))
			.andExpect(jsonPath("$.data.orderName").value("유료 팝업 예약"))
			.andExpect(jsonPath("$.data.amount").value(15000));

		flushAndClear();

		Reservation savedReservation = reservationRepository.findById(reservation.getId()).orElseThrow();
		Payment payment = paymentRepository.findAll().get(0);

		assertThat(savedReservation.getStatus()).isEqualTo(ReservationStatus.HELD);
		assertThat(paymentRepository.findAll()).hasSize(1);
		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
		assertThat(payment.getReservation().getId()).isEqualTo(reservation.getId());
		assertThat(payment.getIdempotencyKey()).isEqualTo("paid-payment-key");
	}

	@Test
	@DisplayName("유료 예약 결제 시작만으로는 대기 row를 삭제하지 않는다")
	void startPaidReservationPayment_success_doesNotDeleteWaitlist() throws Exception {
		User user = persistUser("paid-waitlist@test.com");
		PopupStore popupStore = persistPopup(user, PopupFeeType.PAID, 15000, "유료 대기 팝업");
		ReservationSlot slot = persistSlot(popupStore, 10, 1);
		Reservation reservation = persistReservation(user, slot, ReservationStatus.HELD);
		persistWaitlist(user, slot);

		mockMvc.perform(post("/reservations/{reservationId}/payments", reservation.getId())
				.with(authentication(auth(user.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "홍길동",
					  "phone": "010-1234-5678",
					  "idempotencyKey": "paid-waitlist-payment-key"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.orderName").value("유료 대기 팝업 예약"));

		flushAndClear();

		assertThat(reservationWaitlistRepository.existsByUserIdAndSlotId(user.getId(), slot.getId())).isTrue();
	}

	@Test
	@DisplayName("확정 예약 취소에 성공하면 예약을 취소하고 슬롯 예약 수를 감소시킨다")
	void cancelReservation_success() throws Exception {
		User user = persistUser("cancel@test.com");
		ReservationSlot slot = persistSlot(persistPopup(user, PopupFeeType.FREE, null, "취소 팝업"), 10, 1);
		Reservation reservation = persistReservation(user, slot, ReservationStatus.CONFIRMED);

		mockMvc.perform(delete("/reservations/{reservationId}", reservation.getId())
				.with(authentication(auth(user.getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("SUCCESS"))
			.andExpect(jsonPath("$.message").value("예약 취소가 완료되었습니다."));

		flushAndClear();

		Reservation savedReservation = reservationRepository.findById(reservation.getId()).orElseThrow();
		ReservationSlot savedSlot = reservationSlotRepository.findById(slot.getId()).orElseThrow();

		assertThat(savedReservation.getStatus()).isEqualTo(ReservationStatus.CANCELED);
		assertThat(savedReservation.getCanceledAt()).isNotNull();
		assertThat(savedSlot.getReservedCount()).isZero();
	}

	@Test
	@DisplayName("내 예약 조회에 성공하면 확정/취소 예약만 조회한다")
	void getMyReservations_success() throws Exception {
		User user = persistUser("me@test.com");
		User otherUser = persistUser("other@test.com");
		PopupStore popupStore = persistPopup(user, PopupFeeType.FREE, null, "조회 팝업");
		ReservationSlot confirmedSlot = persistSlot(popupStore, 10, 1);
		ReservationSlot canceledSlot = persistSlot(popupStore, 10, 1);
		ReservationSlot heldSlot = persistSlot(popupStore, 10, 1);
		ReservationSlot expiredSlot = persistSlot(popupStore, 10, 1);
		ReservationSlot otherSlot = persistSlot(popupStore, 10, 1);

		Reservation confirmed = persistReservation(user, confirmedSlot, ReservationStatus.CONFIRMED);
		Reservation canceled = persistReservation(user, canceledSlot, ReservationStatus.CANCELED);
		persistReservation(user, heldSlot, ReservationStatus.HELD);
		persistReservation(user, expiredSlot, ReservationStatus.EXPIRED);
		persistReservation(otherUser, otherSlot, ReservationStatus.CONFIRMED);

		mockMvc.perform(get("/me/reservations")
				.with(authentication(auth(user.getId())))
				.param("page", "0")
				.param("size", "10"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("SUCCESS"))
			.andExpect(jsonPath("$.data.totalElements").value(2))
			.andExpect(jsonPath("$.data.content.length()").value(2))
			.andExpect(jsonPath("$.data.content[0].reservationId").value(canceled.getId()))
			.andExpect(jsonPath("$.data.content[0].status").value("CANCELED"))
			.andExpect(jsonPath("$.data.content[1].reservationId").value(confirmed.getId()))
			.andExpect(jsonPath("$.data.content[1].status").value("CONFIRMED"));
	}

	private UsernamePasswordAuthenticationToken auth(Long userId) {
		return new UsernamePasswordAuthenticationToken(userId, null, List.of());
	}

	private User persistUser(String email) {
		return userRepository.save(User.create(email, "테스터"));
	}

	private PopupStore persistPopup(User user, PopupFeeType feeType, Integer price, String title) {
		LocalDateTime now = LocalDateTime.now();
		PopupStoreCreateRequest request = new PopupStoreCreateRequest(
			title,
			"서울",
			feeType,
			price,
			now.minusDays(1),
			now.plusDays(10),
			now.plusDays(1),
			now.plusDays(20),
			null,
			null
		);
		return popupStoreRepository.save(PopupStore.of(user, request));
	}

	private ReservationSlot persistSlot(PopupStore popupStore, int capacity, int reservedCount) {
		ReservationSlotCreateRequest request = new ReservationSlotCreateRequest(
			LocalDate.now().plusDays(3),
			LocalTime.of(10, 0),
			capacity
		);
		ReservationSlot slot = ReservationSlot.of(popupStore, request);
		ReflectionTestUtils.setField(slot, "reservedCount", reservedCount);
		ReservationSlot saved = reservationSlotRepository.save(slot);

		// 운영에선 슬롯 생성 커밋 후 카운터가 초기화되지만, 통합 테스트는 @Transactional 이라
		// afterCommit 이 돌지 않는다. 이미 잡힌 reservedCount 와 정합하도록 직접 초기화한다.
		// (remaining = 남은 자리)
		redisTemplate.opsForValue().set(RedisKeys.reservationSlotRemaining(saved.getId()), (long) (capacity - reservedCount));
		persistedSlotIds.add(saved.getId());

		return saved;
	}

	private Reservation persistReservation(User user, ReservationSlot slot, ReservationStatus status) {
		Reservation reservation = Reservation.createHeld(
			user,
			slot,
			LocalDateTime.now(),
			LocalDateTime.now().plusMinutes(5)
		);
		if (status == ReservationStatus.CONFIRMED) {
			reservation.confirm();
		}
		if (status == ReservationStatus.CANCELED) {
			reservation.cancel(LocalDateTime.now());
		}
		if (status == ReservationStatus.EXPIRED) {
			reservation.expire();
		}
		return reservationRepository.save(reservation);
	}

	private ReservationWaitlist persistWaitlist(User user, ReservationSlot slot) {
		return reservationWaitlistRepository.save(ReservationWaitlist.of(user, slot));
	}

	private void grantProceedPermission(Long popupId, Long userId) {
		String key = RedisKeys.popupProceedFlag(popupId, userId.toString());
		stringRedisTemplate.opsForValue().set(key, "1");
		proceedFlagKeys.add(key);
	}

	private void flushAndClear() {
		entityManager.flush();
		entityManager.clear();
	}
}

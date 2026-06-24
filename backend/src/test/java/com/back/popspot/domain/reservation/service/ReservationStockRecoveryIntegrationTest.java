package com.back.popspot.domain.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
import com.back.popspot.domain.reservation.repository.ReservationRepository;
import com.back.popspot.domain.user.entity.User;
import com.back.popspot.domain.user.repository.UserRepository;
import com.back.popspot.global.redis.RedisKeys;
import com.back.popspot.support.IntegrationTestSupport;

/**
 * 예약 재고 복구의 트랜잭션 경계를 검증하는 통합 테스트.
 *
 * <p>핵심 검증 포인트:
 * <ul>
 *   <li>취소/만료 시 Redis remaining 복구(INCR)가 <b>DB 커밋 이후</b>에만 일어난다.</li>
 *   <li>결제 경로 만료의 {@code expireInTx}(REQUIRES_NEW)가 바깥 메서드의 예외와 무관하게 독립 커밋된다.</li>
 *   <li>DB 커밋이 실패(롤백)하면 Redis 를 건드리지 않는다.</li>
 * </ul>
 *
 * <p>부모 {@link IntegrationTestSupport} 의 {@code @Transactional}(자동 롤백)을
 * {@code NOT_SUPPORTED} 로 덮어쓴다. 실제 커밋이 일어나야 위 순서/전파를 검증할 수 있기 때문이다.
 * 자동 롤백이 없으므로 {@link #cleanUp()} 에서 커밋된 데이터와 Redis 키를 직접 정리한다.
 *
 * <p>실행 전제: 로컬 MySQL(3306) + Redis(6379) 가 떠 있어야 한다(test 프로파일이 실 DB 사용).
 */
@DisplayName("예약 재고 복구 트랜잭션 통합 테스트 (커밋 후 Redis 복구 / REQUIRES_NEW)")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ReservationStockRecoveryIntegrationTest extends IntegrationTestSupport {

	@Autowired
	private ReservationService reservationService;

	@Autowired
	private ReservationExpirationService reservationExpirationService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PopupStoreRepository popupStoreRepository;

	@Autowired
	private ReservationSlotRepository reservationSlotRepository;

	@Autowired
	private ReservationRepository reservationRepository;

	@Autowired
	private PaymentRepository paymentRepository;

	@Autowired
	private RedisTemplate<String, Long> redisTemplate;

	// 자동 롤백이 없으므로 커밋된 재고 카운터 키를 추적해 직접 정리한다.
	private final List<Long> slotIds = new ArrayList<>();

	@AfterEach
	void cleanUp() {
		// FK 순서: payment -> reservation -> slot -> popup -> user
		paymentRepository.deleteAllInBatch();
		reservationRepository.deleteAllInBatch();
		reservationSlotRepository.deleteAllInBatch();
		popupStoreRepository.deleteAllInBatch();
		userRepository.deleteAllInBatch();

		slotIds.forEach(id -> redisTemplate.delete(RedisKeys.reservationSlotRemaining(id)));
		slotIds.clear();
	}

	// ── 1. 취소: DB 커밋(cancelInTx) 후 Redis remaining 복구 ──────────────────
	@Test
	@DisplayName("확정 예약 취소가 커밋되면 slot reservedCount 감소가 영속되고 Redis remaining 이 복구된다")
	void cancel_commitsDbThenRestoresRedis() throws Exception {
		User user = persistUser("cancel@test.com");
		ReservationSlot slot = persistSlot(persistPopup(user, PopupFeeType.FREE, null, "취소"), 10, 1); // remaining=9
		Reservation reservation = persistReservation(user, slot, ReservationStatus.CONFIRMED, future());

		// cancelReservation 은 @Transactional 이 없어 lazy 초기화에 OSIV 가 필요하다 → MockMvc(HTTP)로 구동.
		mockMvc.perform(delete("/reservations/{id}", reservation.getId())
				.with(authentication(auth(user.getId()))))
			.andExpect(status().isOk());

		// 테스트 자신은 트랜잭션이 없으므로(NOT_SUPPORTED) 아래 조회는 커밋된 상태를 새로 읽는다.
		Reservation saved = reservationRepository.findById(reservation.getId()).orElseThrow();
		ReservationSlot savedSlot = reservationSlotRepository.findById(slot.getId()).orElseThrow();
		Long remaining = redisTemplate.opsForValue().get(RedisKeys.reservationSlotRemaining(slot.getId()));

		assertThat(saved.getStatus()).isEqualTo(ReservationStatus.CANCELED); // DB 커밋됨
		assertThat(saved.getCanceledAt()).isNotNull();
		assertThat(savedSlot.getReservedCount()).isZero();
		assertThat(remaining).isEqualTo(10L);                                // 커밋 후 INCR
	}

	// ── 2. 스케줄러 만료: 건별 커밋 후 Redis 복구 (서비스 직접 호출) ───────────
	@Test
	@DisplayName("만료 스케줄러가 HELD 를 EXPIRED 로 커밋하면 Redis remaining 이 복구된다")
	void scheduledExpire_commitsDbThenRestoresRedis() {
		User user = persistUser("expire@test.com");
		ReservationSlot slot = persistSlot(persistPopup(user, PopupFeeType.FREE, null, "만료"), 10, 1); // remaining=9
		Reservation reservation = persistReservation(user, slot, ReservationStatus.HELD, past()); // heldUntil 과거

		// 스케줄러 경로는 getSlot().getId() 만 접근(프록시 init 불필요)하므로 OSIV 없이 직접 호출 가능.
		reservationExpirationService.expireExpiredReservations();

		Reservation saved = reservationRepository.findById(reservation.getId()).orElseThrow();
		ReservationSlot savedSlot = reservationSlotRepository.findById(slot.getId()).orElseThrow();
		Long remaining = redisTemplate.opsForValue().get(RedisKeys.reservationSlotRemaining(slot.getId()));

		assertThat(saved.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
		assertThat(savedSlot.getReservedCount()).isZero();
		assertThat(remaining).isEqualTo(10L);
	}

	// ── 3. 결제경로 만료: REQUIRES_NEW 가 바깥 예외와 무관하게 독립 커밋 ───────
	@Test
	@DisplayName("결제 시작 중 만료되면 바깥 메서드가 예외(400)를 던져도 expireInTx(REQUIRES_NEW)는 커밋되어 DB EXPIRED + Redis 복구가 남는다")
	void paymentPathExpire_requiresNewCommitsIndependently() throws Exception {
		User user = persistUser("pay-expire@test.com");
		// 슬롯 자체는 미래(시작 전), heldUntil 만 과거여야 '결제 중 만료' 분기를 탄다.
		ReservationSlot slot = persistSlot(persistPopup(user, PopupFeeType.PAID, 15000, "유료"), 10, 1); // remaining=9
		Reservation reservation = persistReservation(user, slot, ReservationStatus.HELD, past());

		mockMvc.perform(post("/reservations/{id}/payments", reservation.getId())
				.with(authentication(auth(user.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{ "name": "홍길동", "phone": "010-1234-5678", "idempotencyKey": "k-expire" }
					"""))
			// RESERVATION_PAYMENT_EXPIRED -> HttpStatus.BAD_REQUEST(400)
			.andExpect(status().isBadRequest());

		Reservation saved = reservationRepository.findById(reservation.getId()).orElseThrow();
		ReservationSlot savedSlot = reservationSlotRepository.findById(slot.getId()).orElseThrow();
		Long remaining = redisTemplate.opsForValue().get(RedisKeys.reservationSlotRemaining(slot.getId()));

		assertThat(saved.getStatus()).isEqualTo(ReservationStatus.EXPIRED); // REQUIRES_NEW 가 독립 커밋
		assertThat(savedSlot.getReservedCount()).isZero();
		assertThat(remaining).isEqualTo(10L);                              // 커밋 후 INCR
	}

	// ── 4. 취소 DB 커밋 실패: 롤백되면 Redis 를 건드리지 않는다 ────────────────
	@Test
	@DisplayName("취소 중 slot 정원 복구가 0건이면 트랜잭션이 롤백되고 예약 상태/Redis 가 그대로 유지된다")
	void cancel_rollsBackAndKeepsRedisWhenDecreaseFails() throws Exception {
		User user = persistUser("cancel-fail@test.com");
		// reservedCount=0 → decreaseReservedCount 가 0건 → cancelInTx 가 INTERNAL_SERVER_ERROR 로 롤백.
		ReservationSlot slot = persistSlot(persistPopup(user, PopupFeeType.FREE, null, "취소실패"), 10, 0); // remaining=10
		Reservation reservation = persistReservation(user, slot, ReservationStatus.CONFIRMED, future());

		mockMvc.perform(delete("/reservations/{id}", reservation.getId())
				.with(authentication(auth(user.getId()))))
			// INTERNAL_SERVER_ERROR(500)
			.andExpect(status().isInternalServerError());

		Reservation saved = reservationRepository.findById(reservation.getId()).orElseThrow();
		Long remaining = redisTemplate.opsForValue().get(RedisKeys.reservationSlotRemaining(slot.getId()));

		assertThat(saved.getStatus()).isEqualTo(ReservationStatus.CONFIRMED); // 롤백되어 그대로
		assertThat(saved.getCanceledAt()).isNull();
		assertThat(remaining).isEqualTo(10L);                                // 커밋 실패 → INCR 없음
	}

	// ── helpers ───────────────────────────────────────────────────────────────
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
		ReservationSlot slot = ReservationSlot.of(popupStore,
			new ReservationSlotCreateRequest(LocalDate.now().plusDays(3), LocalTime.of(10, 0), capacity));
		ReflectionTestUtils.setField(slot, "reservedCount", reservedCount);
		ReservationSlot saved = reservationSlotRepository.save(slot);

		// 운영의 afterCommit 카운터 초기화를 대신해 remaining = 남은 자리 로 세팅한다.
		redisTemplate.opsForValue()
			.set(RedisKeys.reservationSlotRemaining(saved.getId()), (long) (capacity - reservedCount));
		slotIds.add(saved.getId());

		return saved;
	}

	// heldUntil 을 명시적으로 받아 '만료' 케이스(과거)를 만들 수 있게 한다.
	private Reservation persistReservation(User user, ReservationSlot slot, ReservationStatus status,
		LocalDateTime heldUntil) {
		Reservation reservation = Reservation.createHeld(user, slot, LocalDateTime.now(), heldUntil);
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

	private LocalDateTime future() {
		return LocalDateTime.now().plusMinutes(5);
	}

	private LocalDateTime past() {
		return LocalDateTime.now().minusMinutes(1);
	}
}

package com.back.popspot.global.initData;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import com.back.popspot.domain.coupon.dto.CouponCreateRequest;
import com.back.popspot.domain.coupon.entity.Coupon;
import com.back.popspot.domain.coupon.entity.CouponDiscountType;
import com.back.popspot.domain.coupon.repository.CouponRepository;
import com.back.popspot.domain.popupStore.dto.PopupStoreCreateRequest;
import com.back.popspot.domain.popupStore.dto.ReservationSlotCreateRequest;
import com.back.popspot.domain.popupStore.entity.PopupFeeType;
import com.back.popspot.domain.popupStore.entity.PopupStore;
import com.back.popspot.domain.popupStore.entity.ReservationSlot;
import com.back.popspot.domain.popupStore.repository.PopupStoreRepository;
import com.back.popspot.domain.popupStore.repository.ReservationSlotRepository;
import com.back.popspot.domain.user.entity.User;
import com.back.popspot.domain.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 개발(dev) 환경 초기 데이터. 애플리케이션 기동 시 1회 생성한다.
 * @Transactional 이 프록시로 적용되도록 self 를 주입받아 self 를 통해 호출한다.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
@Profile("dev")
public class BaseInitData {

	@Autowired
	@Lazy
	private BaseInitData self;

	private final UserRepository userRepository;
	private final PopupStoreRepository popupStoreRepository;
	private final ReservationSlotRepository reservationSlotRepository;
	private final CouponRepository couponRepository;

	@Bean
	public ApplicationRunner initData() {
		return args -> {
			self.initUsers();
			self.initPopupStores();
			self.initReservationSlots();
			self.initCoupons();
		};
	}

	// ===== 유저 =====
	@Transactional
	public void initUsers() {
		if (userRepository.count() > 0) {
			return;
		}

		// host@gmail.com : 주최자 - 팝업 등록/수정/삭제 테스트용
		userRepository.save(User.create("host@gmail.com", "주최자"));
		// user@gmail.com : 방문자 - 예약/굿즈 주문 테스트용 (host 팝업 수정 시도 시 403 확인용)
		userRepository.save(User.create("user@gmail.com", "방문자"));

		log.info("[initData] 유저 2명 생성 완료 (host, user)");
	}

	// ===== 팝업스토어 =====
	@Transactional
	public void initPopupStores() {
		if (popupStoreRepository.count() > 0) {
			return;
		}

		// 팝업은 host 계정 소유로 생성
		User host = userRepository.findByEmail("host@gmail.com")
				.orElseThrow(() -> new IllegalStateException("host 유저가 없습니다. initUsers 가 먼저 실행되어야 합니다."));

		LocalDateTime now = LocalDateTime.now();

		// UPCOMING : 예약 시작 전 (now < reservationStartAt)
		popupStoreRepository.save(PopupStore.of(host, new PopupStoreCreateRequest(
				"오픈예정 팝업", "서울 강남구", PopupFeeType.FREE, null,
				now.plusDays(3), now.plusDays(10),   // 예약 시작 / 종료
				now.plusDays(5), now.plusDays(15),   // 운영 시작 / 종료
				"image/upcoming.jpg", "곧 오픈하는 팝업입니다.")));

		// OPEN : 예약 진행 중 (reservationStartAt <= now < reservationEndAt)
		popupStoreRepository.save(PopupStore.of(host, new PopupStoreCreateRequest(
				"진행중 팝업", "서울 성수동", PopupFeeType.PAID, 10000,
				now.minusDays(2), now.plusDays(5),
				now.minusDays(1), now.plusDays(10),
				"image/open.jpg", "지금 예약 가능한 팝업입니다.")));

		// CLOSED : 예약 마감 (now >= reservationEndAt)
		popupStoreRepository.save(PopupStore.of(host, new PopupStoreCreateRequest(
				"마감 팝업", "서울 홍대", PopupFeeType.FREE, null,
				now.minusDays(20), now.minusDays(10),
				now.minusDays(18), now.minusDays(8),
				"image/closed.jpg", "예약이 마감된 팝업입니다.")));

		log.info("[initData] 팝업스토어 3개 생성 완료 (UPCOMING / OPEN / CLOSED)");
	}

	// ===== 예약 슬롯 =====
	@Transactional
	public void initReservationSlots() {
		if (reservationSlotRepository.count() > 0) {
			return;
		}

		LocalDateTime now = LocalDateTime.now();

		// 슬롯은 OPEN(진행중) 팝업에만 생성. findOpen 으로 진행 중 팝업 1건 조회
		PopupStore openPopup = popupStoreRepository.findOpen(now, PageRequest.of(0, 1))
				.getContent().stream().findFirst()
				.orElseThrow(() -> new IllegalStateException("OPEN 상태 팝업이 없습니다. initPopupStores 가 먼저 실행되어야 합니다."));

		// 운영 기간 내(now + 1일) 같은 날짜에 시간대만 다르게 3개 (오전 / 오후 / 저녁)
		LocalDate slotDate = now.plusDays(1).toLocalDate();
		reservationSlotRepository.save(ReservationSlot.of(openPopup,
				new ReservationSlotCreateRequest(slotDate, LocalTime.of(10, 0), 20)));  // 오전
		reservationSlotRepository.save(ReservationSlot.of(openPopup,
				new ReservationSlotCreateRequest(slotDate, LocalTime.of(14, 0), 20)));  // 오후
		reservationSlotRepository.save(ReservationSlot.of(openPopup,
				new ReservationSlotCreateRequest(slotDate, LocalTime.of(19, 0), 20)));  // 저녁

		log.info("[initData] OPEN 팝업에 예약 슬롯 3개 생성 완료 (오전 / 오후 / 저녁)");
	}

	// ===== 쿠폰 =====
	@Transactional
	public void initCoupons() {
		if (couponRepository.count() > 0) {
			return;
		}

		LocalDateTime now = LocalDateTime.now();

		PopupStore openPopup = popupStoreRepository.findOpen(now, PageRequest.of(0, 1))
				.getContent().stream().findFirst()
				.orElseThrow(() -> new IllegalStateException("OPEN 상태 팝업이 없습니다. initPopupStores 가 먼저 실행되어야 합니다."));

		couponRepository.save(Coupon.create(openPopup, new CouponCreateRequest(
				"굿즈 1,000원 할인 쿠폰",
				CouponDiscountType.AMOUNT,
				1000,
				null,
				5000,
				100,
				now,
				now.plusDays(7))));

		couponRepository.save(Coupon.create(openPopup, new CouponCreateRequest(
				"굿즈 10% 할인 쿠폰",
				CouponDiscountType.PERCENT,
				10,
				3000,
				10000,
				50,
				now,
				now.plusDays(3))));

		couponRepository.save(Coupon.create(openPopup, new CouponCreateRequest(
				"굿즈 2,000원 할인 쿠폰",
				CouponDiscountType.AMOUNT,
				2000,
				null,
				10000,
				30,
				now,
				now.plusDays(10))));

		log.info("[initData] 굿즈 쿠폰 3개 생성 완료");
	}
}

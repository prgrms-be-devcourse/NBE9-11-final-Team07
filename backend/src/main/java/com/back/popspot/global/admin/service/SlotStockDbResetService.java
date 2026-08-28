package com.back.popspot.global.admin.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.back.popspot.global.admin.dto.SlotStockResetResponse.DeletedRows;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

/**
 * 슬롯 재고 리셋의 DB 쓰기 전용 빈.
 *
 * <p>Redis 쓰기를 담당하는 {@link SlotStockResetService} 와 분리해, 코드베이스의 기존 관례대로
 * DB 커밋이 끝난 뒤에 Redis 를 만지도록 트랜잭션 경계를 이 빈의 메서드 단위로 둔다.
 *
 * <p>부하 테스트 반복 실행 전용이므로 운영(prod) 프로파일에서는 빈 자체가 등록되지 않는다.
 */
@Service
@Profile("!prod")
@RequiredArgsConstructor
public class SlotStockDbResetService {

	private final EntityManager entityManager;

	/**
	 * 슬롯에 달린 예약 원장을 지운다. Payment 가 Reservation 을 FK 로 참조하므로 결제 → 예약 순서로 삭제한다.
	 */
	@Transactional
	public DeletedRows purgeSlotReservations(Long slotId) {
		int payments = entityManager.createQuery("""
				delete from Payment payment
				where payment.reservation.id in (
					select reservation.id from Reservation reservation where reservation.slot.id = :slotId
				)
				""")
			.setParameter("slotId", slotId)
			.executeUpdate();

		int reservations = entityManager.createQuery(
				"delete from Reservation reservation where reservation.slot.id = :slotId")
			.setParameter("slotId", slotId)
			.executeUpdate();

		int waitlists = entityManager.createQuery(
				"delete from ReservationWaitlist waitlist where waitlist.slot.id = :slotId")
			.setParameter("slotId", slotId)
			.executeUpdate();

		int cancelPools = entityManager.createQuery(
				"delete from ReservationCancelPool pool where pool.slot.id = :slotId")
			.setParameter("slotId", slotId)
			.executeUpdate();

		return new DeletedRows(payments, reservations, waitlists, cancelPools);
	}

	/**
	 * 슬롯의 정원/예약 수를 지정한 값으로 되돌린다.
	 *
	 * <p>DB 에는 "남은 재고" 컬럼이 없고 {@code capacity - reserved_count} 가 그 역할을 하므로,
	 * Redis 에 넣을 remaining 과 어긋나지 않도록 {@code reserved_count = capacity - remaining} 으로 맞춘다.
	 */
	@Transactional
	public void resetSlotCounters(Long slotId, int capacity, int reservedCount) {
		entityManager.createQuery("""
				update ReservationSlot slot
				set slot.capacity = :capacity, slot.reservedCount = :reservedCount
				where slot.id = :slotId
				""")
			.setParameter("capacity", capacity)
			.setParameter("reservedCount", reservedCount)
			.setParameter("slotId", slotId)
			.executeUpdate();
	}
}

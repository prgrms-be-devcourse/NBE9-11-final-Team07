package com.back.popspot.domain.popupStore.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.back.popspot.domain.popupStore.entity.PopupStore;

public interface PopupStoreRepository extends JpaRepository<PopupStore, Long> {

	List<PopupStore> findByUserIdOrderByCreatedAtDesc(Long userId);

	// UPCOMING: 예약 시작 전
	@Query("SELECT p FROM PopupStore p WHERE p.reservationStartAt > :now")
	Page<PopupStore> findUpcoming(@Param("now") LocalDateTime now, Pageable pageable);

	// OPEN: 예약 진행 중
	@Query("SELECT p FROM PopupStore p WHERE p.reservationStartAt <= :now AND p.reservationEndAt > :now")
	Page<PopupStore> findOpen(@Param("now") LocalDateTime now, Pageable pageable);

	// CLOSED: 예약 종료
	@Query("SELECT p FROM PopupStore p WHERE p.reservationEndAt <= :now")
	Page<PopupStore> findClosed(@Param("now") LocalDateTime now, Pageable pageable);

	// 자정 배치 전용 — reservationEndAt <= now 인 팝업 ID 목록
	@Query("SELECT p.id FROM PopupStore p WHERE p.reservationEndAt <= :now")
	List<Long> findExpiredPopupIds(@Param("now") LocalDateTime now);
}

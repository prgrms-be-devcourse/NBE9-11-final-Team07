package com.back.popspot.domain.reservation.scheduler;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

import com.back.popspot.domain.reservation.service.ReservationExpirationService;

@ExtendWith(MockitoExtension.class)
class ReservationExpirationSchedulerTest {

	@Mock
	private ReservationExpirationService reservationExpirationService;

	@Test
	@DisplayName("스케줄러는 만료 예약 처리를 실행한다")
	void expireReservations_success() throws NoSuchMethodException {
		// given
		ReservationExpirationScheduler scheduler = new ReservationExpirationScheduler(reservationExpirationService);
		Method method = ReservationExpirationScheduler.class.getMethod("expireReservations");
		Scheduled scheduled = method.getAnnotation(Scheduled.class);

		// when
		scheduler.expireReservations();

		// then
		assertNotNull(scheduled);
		verify(reservationExpirationService).expireExpiredReservations();
	}
}

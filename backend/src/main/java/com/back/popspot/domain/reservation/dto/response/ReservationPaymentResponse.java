package com.back.popspot.domain.reservation.dto.response;

import java.time.LocalDate;
import java.time.LocalTime;

import com.back.popspot.domain.payment.entity.Payment;
import com.back.popspot.domain.popupStore.entity.PopupStore;
import com.back.popspot.domain.popupStore.entity.ReservationSlot;
import com.back.popspot.domain.reservation.entity.Reservation;
import com.back.popspot.domain.reservation.entity.ReservationStatus;

public record ReservationPaymentResponse(
	Long reservationId,
	ReservationStatus status,
	String popupName,
	String location,
	LocalDate reservationDate,
	LocalTime reservationTime,
	String orderId,
	String orderName,
	Long amount
) {

	public static ReservationPaymentResponse free(Reservation reservation) {
		ReservationSlot slot = reservation.getSlot();
		PopupStore popupStore = slot.getPopupStore();

		return new ReservationPaymentResponse(
			reservation.getId(),
			reservation.getStatus(),
			popupStore.getTitle(),
			popupStore.getLocation(),
			slot.getSlotDate(),
			slot.getStartTime(),
			null,
			null,
			null
		);
	}

	public static ReservationPaymentResponse paid(Payment payment) {
		return new ReservationPaymentResponse(
			null,
			null,
			null,
			null,
			null,
			null,
			payment.getOrderId(),
			payment.getOrderName(),
			payment.getAmount()
		);
	}
}

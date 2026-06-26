package com.back.popspot.domain.payment.service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import com.back.popspot.domain.payment.client.TossPaymentsClient;
import com.back.popspot.domain.payment.dto.PaymentConfirmRequest;
import com.back.popspot.domain.payment.dto.PaymentConfirmResponse;
import com.back.popspot.domain.payment.dto.PaymentCancelCommand;
import com.back.popspot.domain.payment.dto.PaymentCancelPreparation;
import com.back.popspot.domain.payment.dto.PaymentCancelRequest;
import com.back.popspot.domain.payment.dto.PaymentCancelResponse;
import com.back.popspot.global.exception.BusinessException;
import com.back.popspot.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {
	private static final String CONFIRM_COMPENSATION_REASON = "결제 승인 후 주문 확정 실패";

	private final PaymentTransactionService paymentTransactionService;
	private final TossPaymentsClient tossPaymentsClient;

	public PaymentConfirmResponse confirm(PaymentConfirmRequest request) {
		Optional<PaymentConfirmResponse> existingPayment = paymentTransactionService.prepare(request);
		if (existingPayment.isPresent()) {
			return existingPayment.get();
		}

		JsonNode tossResponse = tossPaymentsClient.confirm(request);
		validateTossConfirmResponse(tossResponse, request);

		try {
			return paymentTransactionService.complete(request, extractApprovedAt(tossResponse));
		} catch (BusinessException exception) {
			if (requiresCompensation(exception)) {
				compensateConfirmedPayment(request);
			}
			throw exception;
		}
	}

	private boolean requiresCompensation(BusinessException exception) {
		return exception.getErrorCode() == ErrorCode.RESERVATION_PAYMENT_EXPIRED
			|| exception.getErrorCode() == ErrorCode.RESERVATION_PAYMENT_NOT_ALLOWED_STATUS;
	}

	private void compensateConfirmedPayment(PaymentConfirmRequest request) {
		PaymentCancelCommand command;
		try {
			command = paymentTransactionService.prepareCompensation(
				request,
				CONFIRM_COMPENSATION_REASON
			);
		} catch (RuntimeException preparationException) {
			log.error("Payment compensation preparation failed. orderId={}",
				request.orderId(), preparationException);
			return;
		}

		executeCompensation(command);
	}

	public void retryFailedCompensations() {
		List<PaymentCancelCommand> commands = paymentTransactionService.prepareFailedCompensations();
		commands.forEach(this::executeCompensation);
	}

	public void retryFailedCancels() {
		List<PaymentCancelCommand> commands = paymentTransactionService.prepareFailedCancels();
		commands.forEach(command -> {
			try {
				executeCancel(command);
			} catch (RuntimeException exception) {
				log.error("Payment cancel retry failed. paymentId={}, orderId={}",
					command.paymentId(), command.orderId(), exception);
			}
		});
	}

	private void executeCompensation(PaymentCancelCommand command) {
		try {
			JsonNode tossResponse = tossPaymentsClient.cancel(command);
			validateTossCancelResponse(tossResponse, command);
			JsonNode cancel = tossResponse.path("cancels").get(tossResponse.path("cancels").size() - 1);
			paymentTransactionService.completeCompensation(
				command,
				cancel.path("transactionKey").asString(),
				extractCanceledAt(cancel)
			);
		} catch (RuntimeException compensationException) {
			try {
				paymentTransactionService.failCompensation(command);
			} catch (RuntimeException recordingException) {
				compensationException.addSuppressed(recordingException);
			}
			log.error("Payment compensation failed. paymentId={}, orderId={}",
				command.paymentId(), command.orderId(), compensationException);
		}
	}

	public PaymentCancelResponse cancel(Long paymentId, Long userId, PaymentCancelRequest request) {
		PaymentCancelPreparation preparation = paymentTransactionService.prepareCancel(paymentId, userId, request);
		if (preparation.isCompleted()) {
			return preparation.existingResponse();
		}

		PaymentCancelCommand command = preparation.command();
		JsonNode tossResponse;
		try {
			tossResponse = tossPaymentsClient.cancel(command);
		} catch (RestClientException exception) {
			paymentTransactionService.failCancel(command.idempotencyKey());
			throw new BusinessException(ErrorCode.PAYMENT_CANCEL_FAILED);
		}

		try {
			validateTossCancelResponse(tossResponse, command);
		} catch (BusinessException exception) {
			paymentTransactionService.failCancel(command.idempotencyKey());
			throw exception;
		}

		try {
			return completeCancel(command, tossResponse);
		} catch (RuntimeException exception) {
			recordFailedCancel(command, exception);
			throw exception;
		}
	}

	private PaymentCancelResponse executeCancel(PaymentCancelCommand command) {
		try {
			JsonNode tossResponse = tossPaymentsClient.cancel(command);
			validateTossCancelResponse(tossResponse, command);
			return completeCancel(command, tossResponse);
		} catch (RuntimeException exception) {
			recordFailedCancel(command, exception);
			throw exception;
		}
	}

	private void recordFailedCancel(PaymentCancelCommand command, RuntimeException exception) {
		try {
			paymentTransactionService.failCancel(command.idempotencyKey());
		} catch (RuntimeException recordingException) {
			exception.addSuppressed(recordingException);
		}
	}

	private PaymentCancelResponse completeCancel(PaymentCancelCommand command, JsonNode tossResponse) {
		JsonNode cancel = tossResponse.path("cancels").get(tossResponse.path("cancels").size() - 1);
		return paymentTransactionService.completeCancel(
			command,
			cancel.path("transactionKey").asString(),
			extractCanceledAt(cancel)
		);
	}

	private void validateTossConfirmResponse(JsonNode tossResponse, PaymentConfirmRequest request) {
		if (!"DONE".equals(tossResponse.path("status").asString(null))
			|| !request.paymentKey().equals(tossResponse.path("paymentKey").asString(null))
			|| !request.orderId().equals(tossResponse.path("orderId").asString(null))
			|| !String.valueOf(request.amount()).equals(tossResponse.path("totalAmount").asString(null))
			|| tossResponse.path("approvedAt").asString(null) == null
			|| tossResponse.path("approvedAt").asString(null).isBlank()) {
			throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_RESPONSE_MISMATCH);
		}
	}

	private LocalDateTime extractApprovedAt(JsonNode tossResponse) {
		String approvedAt = tossResponse.path("approvedAt").asString(null);
		try {
			return OffsetDateTime.parse(approvedAt).toLocalDateTime();
		} catch (DateTimeParseException e) {
			throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_RESPONSE_MISMATCH);
		}
	}

	private void validateTossCancelResponse(JsonNode tossResponse, PaymentCancelCommand command) {
		JsonNode cancels = tossResponse.path("cancels");
		if (!"CANCELED".equals(tossResponse.path("status").asString(null))
			|| !command.paymentKey().equals(tossResponse.path("paymentKey").asString(null))
			|| !command.orderId().equals(tossResponse.path("orderId").asString(null))
			|| tossResponse.path("balanceAmount").asLong(-1L) != 0L
			|| !cancels.isArray()
			|| cancels.isEmpty()) {
			throw new BusinessException(ErrorCode.PAYMENT_CANCEL_RESPONSE_MISMATCH);
		}

		JsonNode cancel = cancels.get(cancels.size() - 1);
		if (!"DONE".equals(cancel.path("cancelStatus").asString(null))
			|| cancel.path("cancelAmount").asLong(-1L) != command.amount()
			|| cancel.path("transactionKey").asString("").isBlank()
			|| cancel.path("canceledAt").asString("").isBlank()) {
			throw new BusinessException(ErrorCode.PAYMENT_CANCEL_RESPONSE_MISMATCH);
		}
	}

	private LocalDateTime extractCanceledAt(JsonNode cancel) {
		try {
			return OffsetDateTime.parse(cancel.path("canceledAt").asString()).toLocalDateTime();
		} catch (DateTimeParseException exception) {
			throw new BusinessException(ErrorCode.PAYMENT_CANCEL_RESPONSE_MISMATCH);
		}
	}
}

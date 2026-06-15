package com.back.popspot.domain.payment.service;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.back.popspot.domain.payment.dto.DevPaymentCreateRequest;
import com.back.popspot.domain.payment.dto.DevPaymentCreateResponse;
import com.back.popspot.domain.payment.entity.Payment;
import com.back.popspot.domain.payment.entity.PaymentType;
import com.back.popspot.domain.payment.repository.PaymentRepository;
import com.back.popspot.domain.user.entity.User;
import com.back.popspot.domain.user.repository.UserRepository;
import com.back.popspot.global.exception.BusinessException;
import com.back.popspot.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@Profile("dev")
@RequiredArgsConstructor
public class DevPaymentService {
	private final PaymentRepository paymentRepository;
	private final UserRepository userRepository;

	@Transactional
	public DevPaymentCreateResponse create(Long userId, DevPaymentCreateRequest request) {
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

		Payment payment = Payment.createReady(
			user,
			PaymentType.GOODS,
			UUID.randomUUID().toString(),
			request.orderName(),
			request.amount(),
			UUID.randomUUID().toString()
		);

		return DevPaymentCreateResponse.from(paymentRepository.save(payment));
	}
}

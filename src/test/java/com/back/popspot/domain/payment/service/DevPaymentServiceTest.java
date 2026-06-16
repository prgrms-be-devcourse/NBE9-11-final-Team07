package com.back.popspot.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.back.popspot.domain.payment.dto.DevPaymentCreateRequest;
import com.back.popspot.domain.payment.dto.DevPaymentCreateResponse;
import com.back.popspot.domain.payment.entity.Payment;
import com.back.popspot.domain.payment.entity.PaymentStatus;
import com.back.popspot.domain.payment.entity.PaymentType;
import com.back.popspot.domain.payment.repository.PaymentRepository;
import com.back.popspot.domain.user.entity.User;
import com.back.popspot.domain.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class DevPaymentServiceTest {
	@Mock
	private PaymentRepository paymentRepository;

	@Mock
	private UserRepository userRepository;

	@InjectMocks
	private DevPaymentService devPaymentService;

	@Test
	@DisplayName("개발용 결제 주문을 READY 상태로 생성한다")
	void createDevPayment() {
		Long userId = 1L;
		User user = User.create("dev@example.com", "개발자");
		DevPaymentCreateRequest request = new DevPaymentCreateRequest("테스트 상품", 1000L);

		given(userRepository.findById(userId)).willReturn(Optional.of(user));
		given(paymentRepository.save(org.mockito.ArgumentMatchers.any(Payment.class)))
			.willAnswer(invocation -> {
				Payment payment = invocation.getArgument(0);
				ReflectionTestUtils.setField(payment, "id", 10L);
				return payment;
			});

		DevPaymentCreateResponse response = devPaymentService.create(userId, request);

		assertThat(response.paymentId()).isEqualTo(10L);
		assertThat(response.orderId()).isNotBlank();
		assertThat(response.orderName()).isEqualTo("테스트 상품");
		assertThat(response.amount()).isEqualTo(1000L);
		assertThat(response.status()).isEqualTo(PaymentStatus.READY);
		verify(paymentRepository).save(org.mockito.ArgumentMatchers.argThat(payment ->
			payment.getMember() == user
				&& payment.getPaymentType() == PaymentType.GOODS
				&& payment.getIdempotencyKey() != null
		));
	}
}

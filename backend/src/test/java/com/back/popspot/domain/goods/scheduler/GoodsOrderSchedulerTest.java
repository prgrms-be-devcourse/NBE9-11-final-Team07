package com.back.popspot.domain.goods.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.back.popspot.domain.goods.entity.GoodsOrder;
import com.back.popspot.domain.goods.entity.GoodsOrderStatus;
import com.back.popspot.domain.goods.repository.GoodsOrderRepository;
import com.back.popspot.domain.goods.service.GoodsOrderService;
import com.back.popspot.domain.user.entity.User;

@ExtendWith(MockitoExtension.class)
class GoodsOrderSchedulerTest {

    @Mock private GoodsOrderRepository goodsOrderRepository;
    @Mock private GoodsOrderService goodsOrderService;

    @InjectMocks
    private GoodsOrderScheduler goodsOrderScheduler;

    private User user;
    private GoodsOrder expiredOrder;

    @BeforeEach
    void setUp() {
        user = User.create("test@test.com", "테스터");
        ReflectionTestUtils.setField(user, "id", 1L);

        expiredOrder = new GoodsOrder(user, 10000, 0, 10000, GoodsOrderStatus.PENDING,
                "홍길동", "010-1234-5678", "12345", "서울시", null);
        ReflectionTestUtils.setField(expiredOrder, "id", 100L);
        expiredOrder.setExpiresAt(LocalDateTime.now().minusMinutes(1));
    }

    @Test
    void expireOrders_만료된_주문_서비스_호출() {
        given(goodsOrderRepository.findExpiredPendingOrders(any())).willReturn(List.of(expiredOrder));

        goodsOrderScheduler.expireOrders();

        then(goodsOrderService).should().expireOrder(eq(expiredOrder), any());
    }

    @Test
    void expireOrders_만료_대상_없으면_서비스_미호출() {
        given(goodsOrderRepository.findExpiredPendingOrders(any())).willReturn(List.of());

        goodsOrderScheduler.expireOrders();

        then(goodsOrderService).should(never()).expireOrder(any(), any());
    }

    @Test
    void expireOrders_처리_중_예외_발생해도_다음_주문_계속_처리() {
        GoodsOrder anotherOrder = new GoodsOrder(user, 5000, 0, 5000, GoodsOrderStatus.PENDING,
                "홍길동", "010-1234-5678", "12345", "서울시", null);
        ReflectionTestUtils.setField(anotherOrder, "id", 200L);
        anotherOrder.setExpiresAt(LocalDateTime.now().minusMinutes(1));

        given(goodsOrderRepository.findExpiredPendingOrders(any()))
                .willReturn(List.of(expiredOrder, anotherOrder));
        // 첫 번째 주문 처리 시 예외 발생
        org.mockito.BDDMockito.willThrow(new RuntimeException("처리 실패"))
                .given(goodsOrderService).expireOrder(eq(expiredOrder), any());

        goodsOrderScheduler.expireOrders();

        // 예외가 발생해도 두 번째 주문은 처리돼야 함
        then(goodsOrderService).should().expireOrder(eq(anotherOrder), any());
    }
}
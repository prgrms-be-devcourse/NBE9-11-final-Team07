package com.back.popspot.domain.goods.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
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

import com.back.popspot.domain.goods.entity.Goods;
import com.back.popspot.domain.goods.entity.GoodsOrder;
import com.back.popspot.domain.goods.entity.GoodsOrderItem;
import com.back.popspot.domain.goods.entity.GoodsOrderStatus;
import com.back.popspot.domain.goods.repository.GoodsOrderItemRepository;
import com.back.popspot.domain.goods.repository.GoodsOrderRepository;
import com.back.popspot.domain.goods.repository.GoodsRepository;
import com.back.popspot.domain.user.entity.User;

@ExtendWith(MockitoExtension.class)
class GoodsOrderSchedulerTest {

    @Mock private GoodsOrderRepository goodsOrderRepository;
    @Mock private GoodsOrderItemRepository goodsOrderItemRepository;
    @Mock private GoodsRepository goodsRepository;

    @InjectMocks
    private GoodsOrderScheduler goodsOrderScheduler;

    private User user;
    private Goods goods;
    private GoodsOrder expiredOrder;
    private GoodsOrderItem orderItem;

    @BeforeEach
    void setUp() {
        user = User.create("test@test.com", "테스터");
        ReflectionTestUtils.setField(user, "id", 1L);

        goods = new Goods();
        ReflectionTestUtils.setField(goods, "id", 10L);

        expiredOrder = new GoodsOrder(user, 10000, 0, 10000, GoodsOrderStatus.PENDING,
                "홍길동", "010-1234-5678", "12345", "서울시", null);
        ReflectionTestUtils.setField(expiredOrder, "id", 100L);
        expiredOrder.setExpiresAt(LocalDateTime.now().minusMinutes(1)); // 이미 만료

        orderItem = new GoodsOrderItem(expiredOrder, goods, 2, 5000, 10000);
        ReflectionTestUtils.setField(orderItem, "id", 1L);
    }

    @Test
    void expireOrders_만료된_PENDING_주문_재고_복구_및_EXPIRED_처리() {
        given(goodsOrderRepository.findExpiredPendingOrders(any())).willReturn(List.of(expiredOrder));
        given(goodsOrderItemRepository.findByGoodsOrder_Id(100L)).willReturn(List.of(orderItem));

        goodsOrderScheduler.expireOrders();

        then(goodsRepository).should().increaseStock(10L, 2);
        assertThat(expiredOrder.getStatus()).isEqualTo(GoodsOrderStatus.EXPIRED);
    }

    @Test
    void expireOrders_만료_대상_없으면_아무것도_하지_않음() {
        given(goodsOrderRepository.findExpiredPendingOrders(any())).willReturn(List.of());

        goodsOrderScheduler.expireOrders();

        then(goodsRepository).should(never()).increaseStock(anyLong(), anyInt());
    }

    @Test
    void expireOrders_경합_가드_만료_아닌_주문은_스킵() {
        GoodsOrder notExpiredOrder = new GoodsOrder(user, 10000, 0, 10000, GoodsOrderStatus.PENDING,
                "홍길동", "010-1234-5678", "12345", "서울시", null);
        ReflectionTestUtils.setField(notExpiredOrder, "id", 200L);
        notExpiredOrder.setExpiresAt(LocalDateTime.now().plusMinutes(10)); // 아직 유효

        given(goodsOrderRepository.findExpiredPendingOrders(any())).willReturn(List.of(notExpiredOrder));

        goodsOrderScheduler.expireOrders();

        // 경합 가드에서 스킵 — 재고 복구 미호출
        then(goodsRepository).should(never()).increaseStock(anyLong(), anyInt());
    }
}
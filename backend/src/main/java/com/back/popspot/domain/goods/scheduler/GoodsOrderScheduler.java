package com.back.popspot.domain.goods.scheduler;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.back.popspot.domain.goods.entity.GoodsOrder;
import com.back.popspot.domain.goods.repository.GoodsOrderRepository;
import com.back.popspot.domain.goods.service.GoodsOrderService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class GoodsOrderScheduler {

    private final GoodsOrderRepository goodsOrderRepository;
    private final GoodsOrderService goodsOrderService;

    // 1분마다 실행
    @Scheduled(fixedDelay = 60_000)
    public void expireOrders() {
        LocalDateTime now = LocalDateTime.now();
        List<GoodsOrder> expiredOrders = goodsOrderRepository.findExpiredPendingOrders(now);

        if (expiredOrders.isEmpty()) {
            return;
        }

        log.info("[GoodsOrderScheduler] 만료 처리 대상 주문 수: {}", expiredOrders.size());

        // 주문별로 독립 트랜잭션 처리 — 하나 실패해도 나머지 정상 처리
        for (GoodsOrder order : expiredOrders) {
            try {
                goodsOrderService.expireOrder(order, now);
            } catch (Exception e) {
                log.error("[GoodsOrderScheduler] 주문 만료 처리 실패 — goodsOrderId: {}, error: {}",
                        order.getId(), e.getMessage());
            }
        }
    }
}
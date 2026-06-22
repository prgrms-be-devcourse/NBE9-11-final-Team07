package com.back.popspot.domain.goods.scheduler;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.back.popspot.domain.goods.entity.GoodsOrder;
import com.back.popspot.domain.goods.entity.GoodsOrderItem;
import com.back.popspot.domain.goods.repository.GoodsOrderItemRepository;
import com.back.popspot.domain.goods.repository.GoodsOrderRepository;
import com.back.popspot.domain.goods.repository.GoodsRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class GoodsOrderScheduler {

    private final GoodsOrderRepository goodsOrderRepository;
    private final GoodsOrderItemRepository goodsOrderItemRepository;
    private final GoodsRepository goodsRepository;

    // 1분마다 실행
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void expireOrders() {
        LocalDateTime now = LocalDateTime.now();
        List<GoodsOrder> expiredOrders = goodsOrderRepository.findExpiredPendingOrders(now);

        if (expiredOrders.isEmpty()) {
            return;
        }

        log.info("[GoodsOrderScheduler] 만료 처리 대상 주문 수: {}", expiredOrders.size());

        for (GoodsOrder order : expiredOrders) {
            // 경합 가드 — 조회 이후 상태가 바뀌었을 경우 스킵
            if (!order.isExpired(now)) {
                continue;
            }
            List<GoodsOrderItem> items = goodsOrderItemRepository.findByGoodsOrder_Id(order.getId());
            for (GoodsOrderItem item : items) {
                goodsRepository.increaseStock(item.getGoods().getId(), item.getQuantity());
            }
            order.expire();
            log.info("[GoodsOrderScheduler] 주문 만료 처리 완료 — goodsOrderId: {}", order.getId());
        }
    }
}
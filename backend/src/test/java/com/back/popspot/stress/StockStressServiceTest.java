package com.back.popspot.stress;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class StockStressServiceTest {

    private static final Long SLOT_ID = 999L;

    @Autowired
    private StockStressService service;

    @BeforeEach
    void setUp() {
        service.initStock(SLOT_ID, 50);
    }

    @Test
    void 단일_차감이_정상_동작한다() {
        long remaining = service.decrement(SLOT_ID);
        assertThat(remaining).isEqualTo(49L);
    }

    @Test
    void 재고가_0이면_차감에_실패한다() {
        service.initStock(SLOT_ID, 0);
        long result = service.decrement(SLOT_ID);
        assertThat(result).isEqualTo(-1L);
    }

    @Test
    void 동시_100명이_50석에_몰려도_정확히_50명만_성공한다() throws InterruptedException {
        int threads = 100;
        ExecutorService pool = Executors.newFixedThreadPool(32);
        AtomicInteger successCount = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                if (service.decrement(SLOT_ID) >= 0) {
                    successCount.incrementAndGet();
                }
            });
        }
        pool.shutdown();
        pool.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);

        // 핵심 검증: 과예약 0건 → 정확히 50명만 성공
        assertThat(successCount.get()).isEqualTo(50);
        assertThat(service.getStock(SLOT_ID)).isEqualTo(0L);
    }
}
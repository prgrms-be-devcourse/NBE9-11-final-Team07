package com.back.popspot.domain.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.back.popspot.domain.popupStore.dto.PopupStoreCreateRequest;
import com.back.popspot.domain.popupStore.dto.ReservationSlotCreateRequest;
import com.back.popspot.domain.popupStore.entity.PopupFeeType;
import com.back.popspot.domain.popupStore.entity.PopupStore;
import com.back.popspot.domain.popupStore.entity.ReservationSlot;
import com.back.popspot.domain.popupStore.repository.PopupStoreRepository;
import com.back.popspot.domain.popupStore.repository.ReservationSlotRepository;
import com.back.popspot.domain.user.entity.User;
import com.back.popspot.domain.user.repository.UserRepository;
import com.back.popspot.global.redis.RedisKeys;
import com.back.popspot.support.IntegrationTestSupport;

@DisplayName("예약 Lua 스크립트 동시성 테스트")
@Transactional(propagation = Propagation.NOT_SUPPORTED) // [추가] 멀티스레드라 부모 @Transactional 비활성화
class ReservationLuaConcurrencyTest extends IntegrationTestSupport {

    private static final int CAPACITY = 50;
    private static final int CONCURRENT_REQUESTS = 100;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PopupStoreRepository popupStoreRepository;

    @Autowired
    private ReservationSlotRepository reservationSlotRepository;

    private DefaultRedisScript<Long> reserveScript;
    private ReservationSlot slot;
    private User owner;
    private PopupStore popupStore;

    @BeforeEach
    void setUp() {
        // Lua 스크립트 로드
        reserveScript = new DefaultRedisScript<>();
        reserveScript.setLocation(new ClassPathResource("lua/reserve_slot.lua"));
        reserveScript.setResultType(Long.class);

        // DB 데이터 세팅 (@Transactional 없으니 직접 커밋됨)
        owner = userRepository.save(User.create(
                "owner-" + System.currentTimeMillis() + "@concurrency-test.com", "오너"));
        popupStore = popupStoreRepository.save(createPopupStore(owner));
        slot = reservationSlotRepository.save(createSlot(popupStore, CAPACITY));

        // Redis 카운터 0으로 초기화
        stringRedisTemplate.opsForValue()
                .set(RedisKeys.reservationSlotReserved(slot.getId()), "0");
    }

    @AfterEach
    void tearDown() {
        // Redis 키 정리
        stringRedisTemplate.delete(RedisKeys.reservationSlotReserved(slot.getId()));
        // DB 정리 (@Transactional 롤백이 없으니 직접 삭제)
        reservationSlotRepository.deleteById(slot.getId());
        popupStoreRepository.deleteById(popupStore.getId());
        userRepository.deleteById(owner.getId());
    }

    @Test
    @DisplayName("동시 100요청이 정원 50에 몰려도 Lua 스크립트는 정확히 50건만 성공한다 (과예약 0건)")
    void lua_스크립트_동시요청_과예약_없음() throws InterruptedException {
        AtomicInteger success = new AtomicInteger();
        AtomicInteger soldOut = new AtomicInteger();
        String key = RedisKeys.reservationSlotReserved(slot.getId());

        // CountDownLatch로 100개 스레드를 동시에 출발시킴
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(CONCURRENT_REQUESTS);
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);

        for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
            pool.submit(() -> {
                try {
                    startLatch.await(); // 모든 스레드가 준비될 때까지 대기
                    Long result = stringRedisTemplate.execute(
                            reserveScript,
                            List.of(key),
                            String.valueOf(CAPACITY)
                    );
                    if (result != null && result >= 0) {
                        success.incrementAndGet();
                    } else {
                        soldOut.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // 동시 출발
        doneLatch.await(15, TimeUnit.SECONDS); // 전부 끝날 때까지 대기
        pool.shutdown();

        // 핵심 검증: 과예약 0건
        assertThat(success.get())
                .as("정확히 capacity(50)만큼만 성공해야 한다")
                .isEqualTo(CAPACITY);

        assertThat(soldOut.get())
                .as("나머지 50건은 정원 초과로 실패해야 한다")
                .isEqualTo(CONCURRENT_REQUESTS - CAPACITY);

        // Redis 카운터도 정확히 capacity
        String finalCount = stringRedisTemplate.opsForValue().get(key);
        assertThat(Long.parseLong(finalCount))
                .as("Redis 카운터가 정확히 capacity와 같아야 한다")
                .isEqualTo(CAPACITY);
    }

    private PopupStore createPopupStore(User user) {
        LocalDateTime now = LocalDateTime.now();
        PopupStoreCreateRequest request = new PopupStoreCreateRequest(
                "동시성 테스트 팝업",
                "서울",
                PopupFeeType.FREE,
                null,
                now.minusDays(1),
                now.plusDays(10),
                now.plusDays(1),
                now.plusDays(20),
                null,
                null
        );
        return PopupStore.of(user, request);
    }

    private ReservationSlot createSlot(PopupStore popupStore, int capacity) {
        ReservationSlotCreateRequest request = new ReservationSlotCreateRequest(
                LocalDate.now().plusDays(3),
                LocalTime.of(10, 0),
                capacity
        );
        ReservationSlot slot = ReservationSlot.of(popupStore, request);
        ReflectionTestUtils.setField(slot, "reservedCount", 0);
        return slot;
    }
}
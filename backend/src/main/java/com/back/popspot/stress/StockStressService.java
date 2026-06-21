package com.back.popspot.stress;

import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

@Service
public class StockStressService {

    private static final String STOCK_KEY_PREFIX = "stock:slot:";

    private final StringRedisTemplate redisTemplate;
    private DefaultRedisScript<Long> decrementScript;

    public StockStressService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    void initScript() {
        this.decrementScript = new DefaultRedisScript<>();
        this.decrementScript.setLocation(new ClassPathResource("lua/decrement_stock.lua"));
        this.decrementScript.setResultType(Long.class);
    }

    // 재고를 지정한 수량으로 초기화 (테스트 시작 전 호출)
    public void initStock(Long slotId, int count) {
        redisTemplate.opsForValue().set(STOCK_KEY_PREFIX + slotId, String.valueOf(count));
    }

    // 현재 재고 조회
    public Long getStock(Long slotId) {
        String value = redisTemplate.opsForValue().get(STOCK_KEY_PREFIX + slotId);
        return value == null ? null : Long.parseLong(value);
    }

    // Lua 스크립트로 원자적 차감 -> @return 차감 후 남은 재고(0 이상)면 성공, -1이면 재고 부족(차감 실패)
    public long decrement(Long slotId) {
        Long result = redisTemplate.execute(
                decrementScript,
                List.of(STOCK_KEY_PREFIX + slotId));
        return result == null ? -1 : result;
    }
}
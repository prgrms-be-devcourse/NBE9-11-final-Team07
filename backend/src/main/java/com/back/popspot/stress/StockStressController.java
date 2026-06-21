package com.back.popspot.stress;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/stress")
public class StockStressController {

    private final StockStressService stockStressService;

    public StockStressController(StockStressService stockStressService) {
        this.stockStressService = stockStressService;
    }

    // 재고 초기화: POST /stress/init?slotId=1&count=50
    @PostMapping("/init")
    public ResponseEntity<String> init(
            @RequestParam Long slotId,
            @RequestParam int count
    ) {
        stockStressService.initStock(slotId, count);
        return ResponseEntity.ok("init: slot=" + slotId + ", stock=" + count);
    }

    // Lua 차감(예약 시도): POST /stress/reserve?slotId=1
    @PostMapping("/reserve")
    public ResponseEntity<String> reserve(@RequestParam Long slotId) {
        long remaining = stockStressService.decrement(slotId);
        if (remaining < 0) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("SOLD_OUT");
        }
        return ResponseEntity.ok("OK, remaining=" + remaining);
    }
}
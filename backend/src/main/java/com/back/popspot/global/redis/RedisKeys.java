package com.back.popspot.global.redis;

public final class RedisKeys {
    private static final String PREFIX = "popspot";

    private RedisKeys() {
    }

    public static String reservationSlotReserved(Long slotId) {
        return PREFIX + ":reservation:slot:" + slotId + ":reserved";
    }
}
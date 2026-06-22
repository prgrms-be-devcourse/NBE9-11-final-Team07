package com.back.popspot.global.redis;

public final class RedisKeys {
	private static final String PREFIX = "popspot";

	private RedisKeys() {}

	// 내가 추가할 것 (이중카운터)
	public static String reservationSlotReqCount(Long slotId) {
		return PREFIX + ":reservation:slot:" + slotId + ":req";
	}

	public static String reservationSlotRemaining(Long slotId) {
		return PREFIX + ":reservation:slot:" + slotId + ":remaining";
	}
}

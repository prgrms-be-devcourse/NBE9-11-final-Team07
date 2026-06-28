package com.back.popspot.global.redis;

public final class RedisKeys {
	private static final String PREFIX = "popspot";

	private RedisKeys() {}

	public static String reservationSlotRemaining(Long slotId) {
		return PREFIX + ":reservation:slot:" + slotId + ":remaining";
	}

	public static String popupQueueSeq(Long popupId) {
		return "seq:popup:" + popupId;
	}

	public static String popupWaitingQueue(Long popupId) {
		return "waiting:popup:" + popupId;
	}

	public static String popupWaitingQueuePattern() {
		return "waiting:popup:*";
	}

	public static String popupProceedFlag(Long popupId, String userId) {
		return "proceed:popup:" + popupId + ":" + userId;
	}

	public static String popupProceedFlagPattern(Long popupId) {
		return "proceed:popup:" + popupId + ":*";
	}

}

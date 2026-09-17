package com.back.popspot.global.redis;

import com.back.popspot.global.queue.config.SchedulerLockConfig;

public final class RedisKeys {
	private static final String PREFIX = "popspot";

	private RedisKeys() {
	}

	public static String reservationSlotRemaining(Long slotId) {
		return PREFIX + ":reservation:slot:" + slotId + ":remaining";
	}

	public static String popupQueueSeq(Long popupId) {
		return "seq:popup:" + popupId;
	}

	private static final String POPUP_WAITING_QUEUE_PREFIX = "waiting:popup:";

	public static String popupWaitingQueue(Long popupId) {
		return POPUP_WAITING_QUEUE_PREFIX + popupId;
	}

	public static String popupWaitingQueuePrefix() {
		return POPUP_WAITING_QUEUE_PREFIX;
	}

	public static String popupWaitingQueuePattern() {
		return POPUP_WAITING_QUEUE_PREFIX + "*";
	}

	public static String popupProceedFlag(Long popupId, String userId) {
		return "proceed:popup:" + popupId + ":" + userId;
	}

	public static String popupProceedFlagPattern(Long popupId) {
		return "proceed:popup:" + popupId + ":*";
	}

	// ShedLock key format: {DEFAULT_KEY_PREFIX}:{SHEDLOCK_ENV}:{lockName}
	// DEFAULT_KEY_PREFIX="job-lock" is ShedLock's built-in default (RedisLockProvider)
	public static String admissionSchedulerLockKey() {
		return "job-lock:" + SchedulerLockConfig.SHEDLOCK_ENV + ":popup-admission-scheduler";
	}

	public static String activeWaitingPopups() {
		return "active:waiting:popups";
	}

}

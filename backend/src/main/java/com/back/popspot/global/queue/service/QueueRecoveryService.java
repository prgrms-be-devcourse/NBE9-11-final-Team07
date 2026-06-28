package com.back.popspot.global.queue.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.back.popspot.domain.popupStore.repository.PopupStoreRepository;
import com.back.popspot.domain.queue.entity.PopupQueueEntry;
import com.back.popspot.domain.queue.entity.QueueEntryStatus;
import com.back.popspot.domain.queue.repository.PopupQueueEntryRepository;
import com.back.popspot.global.queue.config.WaitingQueueProperties;
import com.back.popspot.global.redis.RedisKeys;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueueRecoveryService {

	private final PopupQueueEntryRepository entryRepository;
	private final StringRedisTemplate redisTemplate;
	private final PopupStoreRepository popupStoreRepository;
	private final WaitingQueueProperties properties;

	public void recoverAll() {
		LocalDateTime now = LocalDateTime.now();
		popupStoreRepository.findOpen(now, Pageable.unpaged())
			.getContent()
			.forEach(popup -> recover(popup.getId(), popup.getReservationEndAt()));
	}

	public void recover(long popupId, LocalDateTime reservationEndAt) {
		Optional<Long> maxSeq = entryRepository.findMaxSeqByPopupId(popupId);
		if (maxSeq.isEmpty()) {
			log.info("[QueueRecovery] popupId={} — DB에 항목 없음, 스킵", popupId);
			return;
		}

		List<PopupQueueEntry> waitingEntries =
			entryRepository.findByPopupIdAndStatusOrderBySeqAsc(popupId, QueueEntryStatus.WAITING);

		String zsetKey = RedisKeys.popupWaitingQueue(popupId);
		String seqKey = RedisKeys.popupQueueSeq(popupId);
		Instant expireAt = properties.computeExpireAt(reservationEndAt);

		redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
			byte[] zsetKeyBytes = zsetKey.getBytes(StandardCharsets.UTF_8);
			byte[] seqKeyBytes = seqKey.getBytes(StandardCharsets.UTF_8);

			connection.keyCommands().del(zsetKeyBytes, seqKeyBytes);

			for (PopupQueueEntry entry : waitingEntries) {
				connection.zSetCommands().zAdd(
					zsetKeyBytes,
					entry.getSeq().doubleValue(),
					entry.getUserId().toString().getBytes(StandardCharsets.UTF_8)
				);
			}

			connection.stringCommands().set(
				seqKeyBytes,
				maxSeq.get().toString().getBytes(StandardCharsets.UTF_8)
			);

			// enqueue()와 동일한 공식: reservationEndAt + queueTtlBufferSeconds
			// WAITING=0 이면 zsetKey가 없으므로 zsetKey expireAt은 no-op이지만, seqKey는 반드시 적용
			connection.keyCommands().expireAt(zsetKeyBytes, expireAt);
			connection.keyCommands().expireAt(seqKeyBytes, expireAt);

			return null;
		});

		log.info("[QueueRecovery] popupId={} 복구 완료 — WAITING={}건, MAX seq={}, TTL={}",
			popupId, waitingEntries.size(), maxSeq.get(), expireAt);
	}
}

-- ============================================================
-- V2: popup_queue_entry에 WAITING 중복 방지용 dedup_key 추가
-- 실행 전제: 애플리케이션이 내려가 있는 상태에서 수동 실행
-- ============================================================

-- Step 1: (popupId, userId) 별 최소 seq 1개만 남기고 중복 WAITING 제거
-- ALTER 전에 실행하지 않으면 UNIQUE 제약 추가 시 충돌 발생
DELETE FROM popup_queue_entry
WHERE status = 'WAITING'
  AND id NOT IN (
    SELECT min_id
    FROM (
        SELECT MIN(id) AS min_id
        FROM popup_queue_entry
        WHERE status = 'WAITING'
        GROUP BY popup_id, user_id
    ) t
  );

-- Step 2: 컬럼 추가 + UNIQUE 제약
ALTER TABLE popup_queue_entry
    ADD COLUMN dedup_key VARCHAR(40) NULL,
    ADD UNIQUE KEY uq_pqe_dedup_key (dedup_key);

-- Step 3: 기존 WAITING 행에 dedup_key 채우기
UPDATE popup_queue_entry
SET dedup_key = CONCAT(popup_id, ':', user_id)
WHERE status = 'WAITING';

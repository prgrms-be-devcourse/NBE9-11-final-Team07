-- H4 부하테스트용 DB 시딩 스크립트
--
-- 실행 결과 (2026-07-01 기준, dev profile 로컬 DB):
--   테스트 유저: users.id 3 ~ 2002 (2000명)
--     - gated 그룹:    3 ~ 1002 (1000명)
--     - baseline 그룹: 1003 ~ 2002 (1000명)
--   개최자: organizer@test.com
--   팝업:  popup_store.id = 4
--   슬롯:  reservation_slot.id = 4 (capacity 2500)
--
-- 주의:
--   - id 시작값은 실행하는 DB 상태에 따라 달라짐 (기존 데이터 유무).
--     실행 후 반드시 MIN/MAX/COUNT로 실제 범위를 확인하고,
--     seed/generate-tokens.js의 GATED_RANGE / BASELINE_RANGE를 그 값에 맞게 수정할 것.
--   - 2번 블록(개최자/팝업/슬롯)은 SET @변수를 쓰므로 반드시 한 세션에서
--     전체를 이어서 실행해야 함 (한 줄씩 끊어서 실행하면 변수가 끊길 수 있음).
--   - 재실행 시 중복 삽입 방지를 위해, 먼저 기존 테스트 데이터 존재 여부를
--     확인하는 것을 권장 (하단 "정리용 쿼리" 참고).

-- ===== 1. 테스트 유저 2000명 =====
INSERT INTO users (email, name, created_at, modified_at)
SELECT CONCAT('user', n, '@test.com'), CONCAT('user', n), NOW(), NOW()
FROM (SELECT @row := @row + 1 AS n FROM information_schema.columns, (SELECT @row := 0) r LIMIT 2000) t;

-- 검증
SELECT COUNT(*) FROM users WHERE email LIKE 'user%@test.com';
SELECT MIN(id), MAX(id), COUNT(*) FROM users WHERE email LIKE 'user%@test.com';

-- ===== 2. 개최자 + 팝업스토어 + 예약슬롯 =====
-- 반드시 아래 블록 전체를 한 번에 이어서 실행할 것 (세션 변수 유지 필요)

INSERT INTO users (email, name, created_at, modified_at)
VALUES ('organizer@test.com', 'organizer', NOW(), NOW());
SET @organizer_id = LAST_INSERT_ID();

INSERT INTO popup_store (
    created_at, modified_at, user_id, title, location,
    fee_type, price, reservation_start_at, reservation_end_at
)
VALUES (
           NOW(), NOW(), @organizer_id, 'k6 부하테스트용 팝업', '테스트 위치',
           'FREE', NULL, NOW(), DATE_ADD(NOW(), INTERVAL 1 DAY)
       );
SET @popup_id = LAST_INSERT_ID();

INSERT INTO reservation_slot (
    created_at, modified_at, popup_store_id,
    slot_date, start_time, capacity, reserved_count
)
VALUES (
           NOW(), NOW(), @popup_id,
           DATE_ADD(CURDATE(), INTERVAL 1 DAY), '18:00:00', 2500, 0
       );
SET @slot_id = LAST_INSERT_ID();

SELECT @popup_id AS popup_id, @slot_id AS slot_id;


-- ===== 정리용 쿼리 (재실행 전 초기화가 필요할 때만 사용) =====
-- 자식 -> 부모 순서로 삭제해야 FK 제약에 안 걸림
-- DELETE FROM reservation WHERE slot_id IN (SELECT id FROM reservation_slot WHERE popup_store_id IN (SELECT id FROM popup_store WHERE title = 'k6 부하테스트용 팝업'));
-- DELETE FROM reservation_slot WHERE popup_store_id IN (SELECT id FROM popup_store WHERE title = 'k6 부하테스트용 팝업');
-- DELETE FROM popup_store WHERE title = 'k6 부하테스트용 팝업';
-- DELETE FROM users WHERE email LIKE 'user%@test.com' OR email = 'organizer@test.com';
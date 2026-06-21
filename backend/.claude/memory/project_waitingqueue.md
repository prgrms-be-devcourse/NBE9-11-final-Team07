---
name: project-waitingqueue
description: 대기열 엔진 구현 현황 — 진입 게이트·스케줄러·입장 허가까지 완료
metadata:
  type: project
---

대기열 엔진 1단계(진입 게이트 + 입장 스케줄러 + 입장 허가 발급) 구현 완료.

**Why:** 팝업 상세 조회 트래픽 집중 대비, FIFO Sorted Set 기반 큐 엔진.

**How to apply:** 이후 추가 기능(순번 조회, 폴링/SSE, 이탈 스위퍼 등)은 별도 범위로 구현.

## 구현된 파일
- `global/queue/WaitingQueueProperties.java` — @ConfigurationProperties record
- `global/queue/WaitingQueueRedisService.java` — enqueue / hasProceedPermission / admitBatch / getActivePopupIds
- `global/queue/WaitingQueueInterceptor.java` — HandlerInterceptor, /popups/* 등록
- `global/queue/WaitingQueueScheduler.java` — fixedRateString 기반 스케줄러
- `global/config/WebMvcConfig.java` — 인터셉터 등록

## Redis 키 구조
- `seq:popup:{id}` — INCR 순번
- `waiting:popup:{id}` — Sorted Set 대기열 (score=순번)
- `proceed:popup:{id}:{token}` — TTL 입장 허가 키

## 설정 (application.yml 기본값)
- `waiting-queue.batch-size: 10`
- `waiting-queue.scheduler-fixed-rate-ms: 500`
- `waiting-queue.proceed-ttl-seconds: 300`
- test 프로파일: batch-size=3, scheduler-rate=3600000, ttl=1s

## 쿠키
- 이름: `queue_token_{popupId}` (팝업별 독립)
- HttpOnly, Path=/, SameSite=Lax

## 통합 테스트
`WaitingQueueIntegrationTest` — 검증 1~5 전부 통과 (Redis localhost:6379 필요)

# PopSpot 🎪

> 팝업스토어 예약 플랫폼 — 기다림은 줄이고, 방문 경험은 더 확실하게

현장 선착순으로 운영되던 팝업스토어를 **사전 예약 + 실시간 정원 관리** 방식으로 전환한 예약 플랫폼입니다. 예약이 순간적으로 몰려도 정원을 초과하지 않고, 대기열로 공정한 입장 순서를 보장합니다.

- 프론트엔드 배포: https://nbe-9-11-final-team07.vercel.app
- GitHub: prgrms-be-devcourse/NBE9-11-final-Team07

---

## 기술 스택

### Frontend
![Next.js](https://img.shields.io/badge/Next.js_16-000000?style=for-the-badge&logo=nextdotjs&logoColor=white)
![React](https://img.shields.io/badge/React_19-61DAFB?style=for-the-badge&logo=react&logoColor=black)
![TypeScript](https://img.shields.io/badge/TypeScript-3178C6?style=for-the-badge&logo=typescript&logoColor=white)
![Tailwind CSS](https://img.shields.io/badge/Tailwind_CSS-06B6D4?style=for-the-badge&logo=tailwindcss&logoColor=white)

### Backend
![Java](https://img.shields.io/badge/Java_25-007396?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot_4.1-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![Spring Data JPA](https://img.shields.io/badge/Spring_Data_JPA-6DB33F?style=for-the-badge&logo=spring&logoColor=white)
![Spring Security](https://img.shields.io/badge/Spring_Security-6DB33F?style=for-the-badge&logo=springsecurity&logoColor=white)
![JWT](https://img.shields.io/badge/JWT-000000?style=for-the-badge&logo=jsonwebtokens&logoColor=white)

### Database / Cache
![MySQL](https://img.shields.io/badge/MySQL-4479A1?style=for-the-badge&logo=mysql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-DC382D?style=for-the-badge&logo=redis&logoColor=white)

### Infra / DevOps
![AWS](https://img.shields.io/badge/AWS-232F3E?style=for-the-badge&logo=amazonwebservices&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white)
![Terraform](https://img.shields.io/badge/Terraform-7B42BC?style=for-the-badge&logo=terraform&logoColor=white)
![GitHub Actions](https://img.shields.io/badge/GitHub_Actions-2088FF?style=for-the-badge&logo=githubactions&logoColor=white)
![Prometheus](https://img.shields.io/badge/Prometheus-E6522C?style=for-the-badge&logo=prometheus&logoColor=white)
![Grafana](https://img.shields.io/badge/Grafana-F46800?style=for-the-badge&logo=grafana&logoColor=white)

### Test / External
![JUnit5](https://img.shields.io/badge/JUnit5-25A162?style=for-the-badge&logo=junit5&logoColor=white)
![k6](https://img.shields.io/badge/k6-7D64FF?style=for-the-badge&logo=k6&logoColor=white)
![Toss Payments](https://img.shields.io/badge/Toss_Payments-0064FF?style=for-the-badge&logo=tosser&logoColor=white)

---

## 시스템 아키텍처

<img width="955" height="646" alt="Image" src="https://github.com/user-attachments/assets/72642fa3-c7ab-4fa2-9760-dcc5c29a560b" />

<!-- TODO: 발표자료의 '프로젝트 시스템 구조' 다이어그램을 docs/architecture.png 로 저장해 커밋 -->

User → Frontend(Next.js) → Backend(Spring Boot) 구조이며, 데이터 계층은 MySQL·Redis·S3, 외부 연동은 Google OAuth·Toss Payments를 사용합니다. 인프라는 Terraform으로 AWS(EC2/RDS/S3)를 프로비저닝하고, GitHub Actions → GHCR → EC2 Blue-Green으로 배포합니다.

---

## 팀 구성

| 이름 | 담당 |
|------|------|
| 김지오 | <!-- TODO: 역할 기입 --> |
| 강경서 | OAuth 인증, 팝업 주최자 API, Redis 단일 카운터·CircuitBreaker |
| 정종욱 | 굿즈 주문·환불 API, 재고 동시성, EC2 Blue/Green·GitHub Actions 배포 |
| 김동호 | 예약 API·동시성, 취소표 재오픈 정책, Redis 정원 차감, 대기열 설계·최적화 |
| 정준용 | 결제 API 연동·승인/취소, 결제-주문 정합성, 쿠폰 발급·검증 |

---

## 주요 기능

- **회차별 사전 예약** — 방문 가능한 시간과 잔여 정원을 확인하고 원하는 회차를 예약
- **대기열·정원 관리** — 예약이 집중되면 대기 순번 부여, 잔여 정원을 원자적으로 차감
- **예약·결제 통합** — 예약·결제·취소·팝업/굿즈/쿠폰 관리를 하나의 서비스에서 제공
- **개최자 기능** — 팝업 등록/수정/삭제(유료·무료), 예약 슬롯·굿즈·쿠폰 관리

---

## 핵심 기술 포인트

### Redis 단일 카운터로 원자적 정원 차감
잔여 정원을 `DECR` 한 번으로 원자적 차감해, 동시 예약에도 정원을 초과하지 않습니다. Lua Script 대비 임계 구역이 짧아 고부하에서 응답 시간이 유리합니다. (1,000 req/s p95: 단일 카운터 36ms vs Lua 187ms)

### Redis + DB 정합성 전략
Redis는 실시간 게이트, DB는 최종 원장으로 역할을 나눕니다.

- **예약** — Redis로 잔여 정원을 원자적 차감(실시간 판단), DB에 예약 상태(HELD/CONFIRMED)를 기록. **실패 시 오버셀 대신 과소판매 방향으로 깨지도록** 설계해, 장애가 나도 정원을 초과 판매하지 않고 Redis 복구로 자리를 다시 엽니다.
- **대기열** — Redis로 진입 순번(seq/score)을 빠르게 관리, DB에 대기 상태(WAITING/ADMITTED)를 기록. 진입 시 WAITING 저장 → Polling으로 순번 조회 → 스케줄러가 선착순 입장 처리(ADMITTED).

### 대기열 기반 입장 제어
Redis Sorted Set으로 진입 순서(score)를 관리하고, 허용된 사용자만 예약 API로 전달해 DB 커넥션 풀을 보호합니다. 대기열 적용 시 예약 API p95가 크게 개선됩니다. (Baseline 2.63s → Gated 250ms)

### 장애 대응
CircuitBreaker로 Redis 장애 확산 방지. 대기열은 서버 재기동 시 자동 rebuild(ShedLock 단일 인스턴스 + 완료 전 enqueue 503 차단), 정원은 DB 확정 예약 기준 수동 복구.

### 결제-예약 정합성
`READY → CONFIRMING → PAID` 상태 전이와 보상 취소, 멱등성 키 검증으로 중복 승인 및 결제-예약 불일치를 방지합니다.

---

## 테스트 & 배포

- **테스트** — JUnit + Testcontainers 단위 테스트, k6 부하·정합성 테스트 (`k6/`)
- **CI/CD** — GitHub Actions: 자동 테스트 → Docker 빌드 → GHCR → EC2 Blue-Green 배포
- **모니터링** — Spring Actuator → Prometheus → Grafana

---

## 협업 방식

- 작업 관리: Notion·WBS / 코드 통합: PR + 리뷰 후 `develop` 병합
- 브랜치: `feat/`·`fix/`·`refactor/` 접두어 / 컨벤션: 네이버 Java 코딩 컨벤션

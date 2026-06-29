# 인프라 운영 가이드

## 담당자
- 인프라/배포/Vercel: @jonguk-jeong
- AWS 콘솔/terraform apply: 담당자만 (team07 계정)
- 개인 계정에서 apply 절대 금지

---

## ⚠️ 운영 주의사항

### RDS
- **운영 중 RDS 절대 중지 금지**
- 중지 시 Spring Boot DB 연결 실패 → HAProxy 503 발생
- 중지됐을 때 복구 순서:
    1. AWS 콘솔 → RDS → 시작
    2. Available 될 때까지 대기 (5~10분)
    3. EC2에서 `docker restart popspot-green` (또는 blue)
    4. `curl http://localhost:8081/api/popups` → 401 나오면 정상

### EC2
- Nginx/HAProxy는 시스템 데몬 → `systemctl status nginx` / `systemctl status haproxy`
- Spring Boot는 수동 docker run (compose 안 씀)
- Redis: `docker ps | grep redis`
- 인스턴스 재시작 후 Spring 컨테이너는 자동으로 안 떠 → 수동으로 다시 띄워야 함

### Terraform
- apply는 team07 계정에서만
- state 파일 로컬 관리 중 — 분실 주의 (백업 권장)
- 순서: `terraform plan` 확인 → `terraform apply`

---

## 아키텍처

```
클라이언트
    ↓
Nginx (443/HTTPS)
    ↓
HAProxy (8090)
    ↓
Spring Boot (8080 Blue / 8081 Green)
    ↓
RDS MySQL (private subnet) + Redis (EC2 내 컨테이너) + S3 (이미지)
```

## Blue/Green 배포
- Blue: 8080 포트, Green: 8081 포트
- HAProxy가 health check로 살아있는 쪽으로 트래픽 전환
- CD 워크플로: main 브랜치 push 시 자동 실행 (`.github/workflows/cd.yml`)

---

## 트러블슈팅

### 503 Service Unavailable
1. HAProxy 상태 확인: `sudo systemctl status haproxy`
2. Spring 컨테이너 상태: `docker ps`
3. Spring 로그: `docker logs --tail 50 popspot-green`
4. RDS 연결 확인: `nc -zv -w 5 <RDS 엔드포인트> 3306`
    - timed out → RDS 중지 상태 확인 (AWS 콘솔)
    - succeeded → Spring 기동 실패 (로그 확인)

### Spring 기동 실패
- `Unable to determine Dialect` → DB 연결 실패 (RDS 중지 또는 SG 문제)
- `PlaceholderResolutionException` → 환경변수 누락 (`--env-file` 확인)
- `terraform plan` no changes → SG/인프라 코드는 정상. 코드 밖 상태(RDS on/off 등) 확인

### CORS 에러 (프론트)
- 백엔드 재배포 없이는 CORS 설정 변경 안 됨
- SecurityConfig의 `setAllowedOriginPatterns` 확인
- 운영 반영은 main 머지 → CD 자동 배포 필요
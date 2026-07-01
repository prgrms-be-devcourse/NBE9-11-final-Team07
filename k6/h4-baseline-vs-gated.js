/**
 * H4 검증 스크립트 — 큐 ON(gated) vs OFF(baseline) 대조 실험
 *
 * 가설: 같은 규모(1000 VU)의 트래픽을,
 *   (a) gated    - 정상적으로 대기열을 거쳐 예약 (큐가 admission을 걸러줌)
 *   (b) baseline - 큐를 우회하고(proceed 플래그 사전 SET) /reservations 직타
 * 로 각각 흘려보냈을 때, (a)는 HikariCP/Redis 지표가 안정적이고
 * (b)는 커넥션 풀 pending/timeout이 발생한다.
 *
 * 실행 전 필수 준비:
 *   1. seed/generate-tokens.js 로 tokens-gated.json / tokens-baseline.json 생성
 *   2. Redis: SET reservation:slot:4:remaining 2500 (매 실행 전 리셋)
 *   3. baseline 시나리오만: seed/seed-proceed-keys.js 실행 (TTL 내에 바로 실행)
 *
 * 실행 예:
 *   k6 run -e SCENARIO=gated    -e BASE_URL=http://localhost:8080 h4-baseline-vs-gated.js
 *   k6 run -e SCENARIO=baseline -e BASE_URL=http://localhost:8080 h4-baseline-vs-gated.js
 *
 * 두 시나리오는 반드시 "따로" 실행한다 (같은 슬롯 재고를 공유하므로 동시 실행 X).
 * 각 실행 전에 remaining 키와 (baseline인 경우) proceed 키를 리셋할 것.
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';
import { SharedArray } from 'k6/data';

// ===== 설정 =====
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const POPUP_ID = 4;
const SLOT_ID = 4;
const SCENARIO = __ENV.SCENARIO || 'gated'; // 'gated' | 'baseline'
const POLL_INTERVAL = 3;
const MAX_POLL_COUNT = 60; // 3초 * 60 = 최대 3분 대기

// ===== 토큰 로드 (시나리오별로 다른 유저 그룹) =====
const tokens = new SharedArray('tokens', () => {
  const file = SCENARIO === 'baseline' ? './tokens/tokens-baseline.json' : './tokens/tokens-gated.json';
  return JSON.parse(open(file));
});

// ===== 메트릭: 시나리오별로 분리, 요청 종류별로도 분리 =====
const enqueueDuration = new Trend('enqueue_duration', true);
const pollDuration = new Trend('poll_duration', true);
const reservationDuration = new Trend('reservation_duration', true);
const totalFlowDuration = new Trend('total_flow_duration', true);

const reservationSuccess = new Rate('reservation_success');
const capacityExceeded = new Rate('capacity_exceeded');
const admissionRequiredError = new Counter('admission_required_error'); // baseline에서 proceed flag 실패 시
const errorRate = new Rate('error_rate');

export const options = {
  scenarios: {
    load: {
      executor: 'per-vu-iterations',
      vus: tokens.length,
      iterations: 1,
      maxDuration: '10m',
    },
  },
  thresholds: {
    // 절대적 응답시간 기준이 아니라, 두 시나리오를 나중에 비교하는 게 목적이므로
    // 여기서는 하드 실패 기준을 느슨하게 두고 Grafana/사후 비교로 판단한다.
    error_rate: ['rate<0.5'],
  },
};

export default function () {
  const entry = tokens[__VU - 1];
  if (!entry) {
    return; // VU 수가 토큰 수를 초과하는 경우 방지
  }

  const headers = {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${entry.token}`,
  };

  const flowStart = Date.now();

  if (SCENARIO === 'baseline') {
    // (b) 큐 우회 - proceed 플래그가 이미 SET되어 있다고 가정하고 바로 예약 시도
    doReservation(headers, flowStart);
    return;
  }

  // (a) 큐 정상 통과 흐름
  const enqueueStart = Date.now();
  const enqueueRes = http.get(`${BASE_URL}/popups/${POPUP_ID}`, { headers });
  enqueueDuration.add(Date.now() - enqueueStart);

  if (enqueueRes.status === 200) {
    // 이미 진입 허가 상태(비정상 케이스) - 바로 예약 시도
    doReservation(headers, flowStart);
    return;
  }

  if (enqueueRes.status !== 202) {
    errorRate.add(1);
    reservationSuccess.add(0);
    return;
  }
  check(enqueueRes, { '대기열 진입 성공(202)': (r) => r.status === 202 });

  let admitted = false;
  for (let i = 0; i < MAX_POLL_COUNT; i++) {
    sleep(POLL_INTERVAL);

    const pollStart = Date.now();
    const pollRes = http.get(`${BASE_URL}/popups/${POPUP_ID}/waiting-status`, { headers });
    pollDuration.add(Date.now() - pollStart);

    if (pollRes.status === 200) {
      admitted = true;
      check(pollRes, { '입장 완료(200)': (r) => r.status === 200 });
      break;
    }
    if (pollRes.status === 202) {
      check(pollRes, { '대기 중(202)': (r) => r.status === 202 });
      continue;
    }
    errorRate.add(1);
    break;
  }

  if (!admitted) {
    errorRate.add(1);
    reservationSuccess.add(0);
    return;
  }

  doReservation(headers, flowStart);
}

function doReservation(headers, flowStart) {
  const payload = JSON.stringify({ slotId: SLOT_ID });

  const reserveStart = Date.now();
  const reserveRes = http.post(`${BASE_URL}/reservations`, payload, { headers });
  reservationDuration.add(Date.now() - reserveStart);
  totalFlowDuration.add(Date.now() - flowStart);

  if (reserveRes.status === 200 || reserveRes.status === 201) {
    reservationSuccess.add(1);
    capacityExceeded.add(0);
    errorRate.add(0);
    check(reserveRes, { '예약 성공(200/201)': (r) => r.status === 200 || r.status === 201 });
  } else if (reserveRes.status === 409) {
    reservationSuccess.add(0);
    capacityExceeded.add(1);
    errorRate.add(0);
    check(reserveRes, { '정원 초과(409) 정상 처리': (r) => r.status === 409 });
  } else if (reserveRes.status === 403 || reserveRes.status === 401) {
    // baseline에서 proceed 플래그가 없거나 만료된 경우
    reservationSuccess.add(0);
    capacityExceeded.add(0);
    errorRate.add(0);
    admissionRequiredError.add(1);
  } else if (reserveRes.status === 503) {
    reservationSuccess.add(0);
    capacityExceeded.add(0);
    errorRate.add(1);
  } else {
    reservationSuccess.add(0);
    capacityExceeded.add(0);
    errorRate.add(1);
  }
}

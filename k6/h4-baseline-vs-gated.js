/**
 * H4 검증 스크립트 — 큐 ON(gated) vs OFF(baseline) 대조 실험
 *
 * 가설: 같은 규모(500 VU)의 트래픽을, 순간 폭발이 아니라 "점진적으로 유입"시켰을 때
 *   (a) gated    - 정상적으로 대기열을 거쳐 예약 (큐가 admission을 걸러줌)
 *   (b) baseline - 큐를 우회하고(proceed 플래그 사전 SET) /reservations 직타
 * 로 각각 흘려보냈을 때, (a)는 HikariCP/Redis 지표가 안정적이고
 * (b)는 커넥션 풀 pending/timeout 또는 순간 RPS 폭증이 발생한다.
 *
 * ※ 기존 per-vu-iterations(순간 0→500 동시 스폰) 방식에서
 *   ramping-arrival-rate(0→목표 RPS로 점진 증가) 방식으로 변경.
 *   이유: 순간 스폰은 큐 테스트가 아니라 웹서버(Tomcat) 연결 수락 능력 테스트가 되어버림.
 *   H4가 보려는 건 "지속적으로 몰리는 트래픽에서 큐가 완충하는가"이므로 ramp가 더 적합.
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
 *
 * 실행 후 반드시 확인: 콘솔 요약의 dropped_iterations.
 *   0이 아니면 preAllocatedVUs가 부족해서 ramp 모양이 계획대로 안 된 것이므로 결과를 신뢰하지 말 것.
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';
import { SharedArray } from 'k6/data';
import exec from 'k6/execution';

// ===== 설정 =====
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const POPUP_ID = 4;
const SLOT_ID = 4;
const SCENARIO = __ENV.SCENARIO || 'gated'; // 'gated' | 'baseline'
const POLL_INTERVAL = 3;
const MAX_POLL_COUNT = 60; // 3초 * 60 = 최대 3분 대기

// ===== 토큰 로드 (시나리오별로 다른 유저 그룹) =====
const allTokens = new SharedArray('tokens', () => {
  const file = SCENARIO === 'baseline' ? './tokens/tokens-baseline.json' : './tokens/tokens-gated.json';
  return JSON.parse(open(file));
});

// VU_COUNT로 실제 사용할 유저 수를 제한할 수 있음 (로컬 환경 한계 테스트용)
// 예: k6 run -e VU_COUNT=200 ... 이면 1000개 토큰 중 앞 200개만 사용
const VU_COUNT = __ENV.VU_COUNT ? Number(__ENV.VU_COUNT) : allTokens.length;
const tokens = [];
for (let i = 0; i < VU_COUNT && i < allTokens.length; i++) {
  tokens.push(allTokens[i]);
}

// ===== 메트릭: 시나리오별로 분리, 요청 종류별로도 분리 =====
const enqueueDuration = new Trend('enqueue_duration', true);
const pollDuration = new Trend('poll_duration', true);
const reservationDuration = new Trend('reservation_duration', true);
const totalFlowDuration = new Trend('total_flow_duration', true);

const reservationSuccess = new Rate('reservation_success');
const capacityExceeded = new Rate('capacity_exceeded');
const admissionRequiredError = new Counter('admission_required_error'); // baseline에서 proceed flag 실패 시
const errorRate = new Rate('error_rate');

// ===== 램프 설정 =====
// 30초에 걸쳐 0 -> 25/s로 점진 증가 후, 10초간 유지하며 잔여 인원 소진.
// 25/s * 30s(삼각형 근사 절반) + 25/s * 10s ≈ 375 + 250 = 625 >= tokens.length(500) 커버.
// 필요시 stages 값은 실험 목적에 맞게 조정 가능 (핵심은 "순간 0->500"이 아니라 "점진 증가"라는 형태).
const RAMP_STAGES = [
  { target: 600, duration: '1s' },
  { target: 600, duration: '2s' },
];

export const options = {
  scenarios: {
    load: {
      executor: 'ramping-arrival-rate',
      startRate: 0,
      timeUnit: '1s',
      stages: RAMP_STAGES,
      // gated는 폴링 때문에 VU가 admit될 때까지(최대 3분) 오래 붙잡혀 있을 수 있어 넉넉히 확보.
      // 부족하면 dropped_iterations가 발생하며 램프 모양이 왜곡되니 실행 후 반드시 확인할 것.
      preAllocatedVUs: tokens.length,
      maxVUs: tokens.length + 50,
    },
  },
  thresholds: {
    // 절대적 응답시간 기준이 아니라, 두 시나리오를 나중에 비교하는 게 목적이므로
    // 여기서는 하드 실패 기준을 느슨하게 두고 Grafana/사후 비교로 판단한다.
    error_rate: ['rate<0.5'],
  },
};

export default function () {
  // ramping-arrival-rate는 VU를 재사용하며 이터레이션을 돌리므로
  // __VU로 토큰을 인덱싱하면 겹칠 수 있음. 이 실행 전체에서 유일한 순번을 써야 함.
  const idx = exec.scenario.iterationInTest;
  if (idx >= tokens.length) {
    return; // 목표 인원(tokens.length) 초과분은 요청 없이 즉시 종료
  }
  const entry = tokens[idx];
  if (!entry) {
    return;
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

    if (pollRes.status !== 200) {
      // waiting-status는 정상 케이스에서 항상 HTTP 200을 반환한다
      // (ADMITTED/WAITING/NOT_IN_QUEUE 전부 200). 200이 아니면 진짜 에러.
      errorRate.add(1);
      break;
    }

    let body;
    try {
      body = JSON.parse(pollRes.body);
    } catch (e) {
      errorRate.add(1);
      break;
    }

    const queueStatus = body && body.data ? body.data.status : undefined;

    if (queueStatus === 'ADMITTED') {
      admitted = true;
      check(pollRes, { '입장 완료(ADMITTED)': () => true });
      break;
    }
    if (queueStatus === 'WAITING') {
      check(pollRes, { '대기 중(WAITING)': () => true });
      continue;
    }
    if (queueStatus === 'NOT_IN_QUEUE') {
      // 큐에 없는 상태로 판정됨 - 정상 흐름이 아니므로 실패로 집계
      errorRate.add(1);
      reservationSuccess.add(0);
      return;
    }

    // status 필드를 못 찾은 경우 (응답 구조가 예상과 다름)
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
import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';
import encoding from 'k6/encoding';
import exec from 'k6/execution';

// ─────────────────────────────────────────────────────────────────────────────
// POST /reservations 재고 경합 부하 테스트.
//
// 정원(97)보다 요청(5,000)이 훨씬 많은 상황을 만들어, 재고 차감 로직이
// 오버셀 없이 정확히 정원만큼만 통과시키는지와 그때의 응답시간을 측정한다.
//
// 실행 전 setup() 이 관리용 리셋 API 를 호출해 재고와 proceed flag 를 되돌리므로,
// 별도 준비 없이 같은 명령을 반복 실행할 수 있다.
//
//   k6 run reservation-load-test.js
//   k6 run -e VUS=1000 -e ITERATIONS=10000 reservation-load-test.js
// ─────────────────────────────────────────────────────────────────────────────

const TOKENS_FILE = __ENV.TOKENS_FILE || './tokens-5000.json';

const tokens = new SharedArray('tokens', function () {
  return JSON.parse(open(TOKENS_FILE));
});

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const SLOT_ID = Number(__ENV.SLOT_ID || 900000);

const VUS = Number(__ENV.VUS || 500);
const ITERATIONS = Number(__ENV.ITERATIONS || 5000);

// 슬롯 정원. 이 값으로 재고를 리셋하고, 오버셀 임계값의 기준으로도 쓴다.
const CAPACITY = Number(__ENV.CAPACITY || 97);
const REMAINING = Number(__ENV.REMAINING || CAPACITY);

// 예약에 성공하면 proceed flag 가 즉시 소각된다. 측정 구간 내내 유효하도록 넉넉히 잡는다.
const PROCEED_TTL_SECONDS = Number(__ENV.PROCEED_TTL_SECONDS || 600);

// 성공/실패를 나눠 센다. sold_out 은 재고 소진(409)이라는 정상 결과이고,
// rejected 는 입장 허가 누락(403) 같은 예상 밖 실패라 측정 오염 신호다.
const successCount = new Counter('reservation_success');
const soldOutCount = new Counter('reservation_sold_out');
const rejectedCount = new Counter('reservation_rejected');

// 성공과 실패는 통과하는 경로가 다르다(성공은 DB 저장까지, 실패는 Redis 차감에서 반환).
// 한 덩어리로 보면 서로를 가리므로 응답시간을 따로 잡는다.
const successLatency = new Trend('reservation_success_latency', true);
const failLatency = new Trend('reservation_fail_latency', true);

// 409(정원 초과)는 이 시나리오의 정상 결과다. http_req_failed 로 세지 않는다.
http.setResponseCallback(http.expectedStatuses(200, 201, 409));

export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    reservation: {
      executor: 'shared-iterations',
      vus: VUS,
      iterations: ITERATIONS,
      maxDuration: '10m',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    // 오버셀 가드: 성공이 정원보다 많으면 차감 로직이 깨진 것이다.
    reservation_success: [`count<=${CAPACITY}`],
    // 예상 밖 실패가 하나라도 있으면 측정 결과를 믿을 수 없다.
    reservation_rejected: ['count==0'],
  },
};

// JWT payload 의 sub 가 유저 id 다. 토큰 파일에 실제로 담긴 범위를 그대로 쓴다.
function userIdOf(token) {
  const payload = token.split('.')[1];
  return Number(JSON.parse(encoding.b64decode(payload, 'rawurl', 's')).sub);
}

function resolveUserIdRange() {
  if (__ENV.PROCEED_USER_ID_FROM && __ENV.PROCEED_USER_ID_TO) {
    return {
      from: Number(__ENV.PROCEED_USER_ID_FROM),
      to: Number(__ENV.PROCEED_USER_ID_TO),
    };
  }

  // 생성 순서를 가정하지 않고 토큰 전체를 훑어 최소/최대를 구한다.
  let from = Infinity;
  let to = -Infinity;
  for (let i = 0; i < tokens.length; i++) {
    const userId = userIdOf(tokens[i]);
    if (userId < from) {
      from = userId;
    }
    if (userId > to) {
      to = userId;
    }
  }

  return { from, to };
}

export function setup() {
  const range = resolveUserIdRange();

  // 부하를 주기 전에 재고와 proceed flag 를 되돌린다.
  // 이걸 안 하면 직전 회차의 예약 행이 남아 중복 예약에 걸리고,
  // 소각된 proceed flag 때문에 모든 요청이 403 으로 떨어진다.
  const res = http.post(
    `${BASE_URL}/admin/load-test/slots/${SLOT_ID}/stock/reset`,
    JSON.stringify({
      capacity: CAPACITY,
      remaining: REMAINING,
      purgeReservations: true,
      proceedUserIdFrom: range.from,
      proceedUserIdTo: range.to,
      proceedTtlSeconds: PROCEED_TTL_SECONDS,
    }),
    {
      headers: {
        'Content-Type': 'application/json',
        // 리셋 API 도 인증이 필요하다. 부하에 쓰는 토큰을 그대로 재사용한다.
        'Authorization': `Bearer ${tokens[0]}`,
      },
      // 유저 범위가 넓으면 flag 발급에 시간이 걸린다.
      timeout: '120s',
      responseCallback: http.expectedStatuses(200),
    }
  );

  if (res.status !== 200) {
    exec.test.abort(`재고 리셋 실패 (HTTP ${res.status}): ${res.body}`);
  }

  const data = res.json('data');
  console.log(
    `[setup] slotId=${SLOT_ID} capacity=${data.capacity} remaining=${data.resetRemaining} ` +
    `(리셋 전 ${data.previousRedisRemaining}) 삭제된예약=${data.deleted.reservations} ` +
    `proceed=${data.proceed.userIdFrom}~${data.proceed.userIdTo} ${data.proceed.granted}건 TTL=${data.proceed.ttlSeconds}s`
  );

  return { capacity: data.capacity, remaining: data.resetRemaining };
}

export default function () {
  // 유저마다 슬롯당 1건만 예약할 수 있으므로 토큰을 순회하며 매번 다른 유저로 요청한다.
  const token = tokens[exec.scenario.iterationInTest % tokens.length];

  const res = http.post(
    `${BASE_URL}/reservations`,
    JSON.stringify({ slotId: SLOT_ID }),
    {
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`,
      },
    }
  );

  if (res.status === 200 || res.status === 201) {
    successCount.add(1);
    successLatency.add(res.timings.duration);
  } else if (res.status === 409) {
    soldOutCount.add(1);
    failLatency.add(res.timings.duration);
  } else {
    rejectedCount.add(1);
    failLatency.add(res.timings.duration);
  }

  check(res, {
    '201 예약 성공 또는 409 재고 소진': (r) => r.status === 200 || r.status === 201 || r.status === 409,
  });
}

// ─── 요약 출력 ────────────────────────────────────────────────────────────────

function count(data, name) {
  const metric = data.metrics[name];
  return metric ? metric.values.count : 0;
}

function p95(data, name) {
  const metric = data.metrics[name];
  const value = metric ? metric.values['p(95)'] : undefined;
  return value === undefined ? null : value;
}

function ms(value) {
  return value === null ? '-' : `${value.toFixed(2)}ms`;
}

export function handleSummary(data) {
  const success = count(data, 'reservation_success');
  const soldOut = count(data, 'reservation_sold_out');
  const rejected = count(data, 'reservation_rejected');
  // http_reqs 에는 setup 의 리셋 호출도 포함되므로, 예약 요청만 따로 센다.
  const total = success + soldOut + rejected;

  // setup 소요분이 포함된 전체 실행 시간이라 TPS 는 실제보다 약간 낮게 잡힌다.
  const durationMs = data.state.testRunDurationMs;
  const tps = durationMs > 0 ? (total / durationMs) * 1000 : 0;

  const capacity = Number(__ENV.CAPACITY || 97);
  const oversold = success > capacity;

  const lines = [
    '',
    '════════════════════════════════════════════════════════',
    '  POST /reservations 재고 경합 부하 테스트 결과',
    '════════════════════════════════════════════════════════',
    `  VU / 반복        : ${Number(__ENV.VUS || 500)} VU / ${Number(__ENV.ITERATIONS || 5000)} iterations`,
    `  실행 시간        : ${(durationMs / 1000).toFixed(2)}s`,
    `  TPS              : ${tps.toFixed(2)} req/s`,
    '  ──────────────────────────────────────────────────────',
    `  전체 요청        : ${total}`,
    `  성공 (200/201)   : ${success}`,
    `  재고 소진 (409)  : ${soldOut}`,
    `  기타 실패        : ${rejected}`,
    '  ──────────────────────────────────────────────────────',
    `  전체    p95      : ${ms(p95(data, 'http_req_duration'))}`,
    `  success p95      : ${ms(p95(data, 'reservation_success_latency'))}`,
    `  fail    p95      : ${ms(p95(data, 'reservation_fail_latency'))}`,
    '  ──────────────────────────────────────────────────────',
    `  정원             : ${capacity}`,
    `  오버셀           : ${oversold ? `발생 (성공 ${success} > 정원 ${capacity})` : '없음'}`,
    '════════════════════════════════════════════════════════',
    '',
  ];

  const out = { stdout: lines.join('\n') };

  // A/B 비교처럼 여러 회차를 모아 볼 때를 위해 요약을 파일로도 남긴다.
  if (__ENV.SUMMARY_OUT) {
    out[__ENV.SUMMARY_OUT] = JSON.stringify({
      label: __ENV.LABEL || '',
      vus: Number(__ENV.VUS || 500),
      iterations: Number(__ENV.ITERATIONS || 5000),
      durationMs,
      tps,
      total,
      success,
      soldOut,
      rejected,
      capacity,
      oversold,
      p95All: p95(data, 'http_req_duration'),
      p95Success: p95(data, 'reservation_success_latency'),
      p95Fail: p95(data, 'reservation_fail_latency'),
      avgAll: data.metrics.http_req_duration ? data.metrics.http_req_duration.values.avg : null,
      medAll: data.metrics.http_req_duration ? data.metrics.http_req_duration.values.med : null,
      p99All: data.metrics.http_req_duration ? data.metrics.http_req_duration.values['p(99)'] : null,
    }, null, 2);
  }

  return out;
}

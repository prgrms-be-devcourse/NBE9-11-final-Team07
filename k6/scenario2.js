import http from 'k6/http';
import { check } from 'k6';
import { SharedArray } from 'k6/data';
import exec from 'k6/execution';

const tokens = new SharedArray('tokens', function () {
  return JSON.parse(open('./tokens.json'));
});

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const SLOT_ID = Number(__ENV.SLOT_ID || 900000);
const RATE = Number(__ENV.RATE || 100);

export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    benchmark: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: '60s',
      preAllocatedVUs: Math.ceil(RATE * 2),
      maxVUs: Math.ceil(RATE * 4),
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
  },
};

export default function () {
  const idx = exec.scenario.iterationInTest;
  const token = tokens[idx % tokens.length];

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

  check(res, {
    'status is 201': (r) => r.status === 201,
  });
}

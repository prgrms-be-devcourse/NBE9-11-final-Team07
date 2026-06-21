import http from 'k6/http';
import { Counter } from 'k6/metrics';

const success = new Counter('reservation_success'); // 200 (예약 성공)
const soldOut = new Counter('reservation_soldout'); // 409 (매진)

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const SLOT_ID = __ENV.SLOT_ID || '1';

export const options = {
    scenarios: {
        burst: {
            executor: 'per-vu-iterations',
            vus: 100,        // 동시 100명 (1000으로 올려도 됨)
            iterations: 1,   // 1명당 1회 = 정확히 100요청
            maxDuration: '15s',
        },
    },
};

export default function () {
    const res = http.post(`${BASE}/stress/reserve?slotId=${SLOT_ID}`);
    if (res.status === 200) {
        success.add(1);
    } else if (res.status === 409) {
        soldOut.add(1);
    }
}
/**
 * H4 baseline(직타) 그룹용 proceed 플래그 사전 심기 스크립트
 *
 * ReservationService.createReservation()이 hasProceedPermission()을 체크하므로,
 * 큐를 거치지 않고 /reservations를 직접 때리는 baseline 그룹(1003~2002)은
 * 이 플래그가 미리 없으면 전원 RESERVATION_ADMISSION_REQUIRED 에러로 실패한다.
 *
 * 이 스크립트는 "큐가 없었다면 이 트래픽이 예약 로직/DB를 얼마나 직접
 * 두들겼을까"를 재현하기 위해, 정상적인 큐 admission 없이 permission만
 * 강제로 부여하는 것 -- 실제 서비스 흐름을 우회하는 테스트 전용 조작이다.
 *
 * 키 포맷 (RedisKeys.popupProceedFlag 기준): proceed:popup:{popupId}:{userId}
 * 값은 무엇이든 상관없음 (EXISTS만 체크). TTL은 dev/prod 기준 180초.
 *
 * 사용법:
 *   node seed-proceed-keys.js
 *
 * 주의: TTL이 180초이므로, 이 스크립트 실행 후 180초 이내에 baseline
 *   k6 시나리오를 시작해야 한다. VU 수가 많아 ramp-up이 길면 TTL을
 *   여유있게 늘려서(예: 600) 다시 실행할 것.
 */

const Redis = require('ioredis');

const REDIS_HOST = process.env.REDIS_HOST || 'localhost';
const REDIS_PORT = process.env.REDIS_PORT || 6379;
const POPUP_ID = 4;
const BASELINE_RANGE = { start: 1003, end: 2002 }; // (b) 직타 baseline 그룹
const TTL_SECONDS = 600; // 180초 기본값보다 여유있게. 필요시 조정.

const redis = new Redis({ host: REDIS_HOST, port: REDIS_PORT });

async function main() {
  const pipeline = redis.pipeline();
  for (let userId = BASELINE_RANGE.start; userId <= BASELINE_RANGE.end; userId++) {
    const key = `proceed:popup:${POPUP_ID}:${userId}`;
    pipeline.set(key, '1', 'EX', TTL_SECONDS);
  }
  const results = await pipeline.exec();

  const failed = results.filter(([err]) => err);
  console.log(`총 ${results.length}개 키 SET 시도, 실패 ${failed.length}개`);
  if (failed.length > 0) {
    console.error('실패 샘플:', failed.slice(0, 5));
  }

  // 검증용 샘플 확인
  const sampleKey = `proceed:popup:${POPUP_ID}:${BASELINE_RANGE.start}`;
  const sampleVal = await redis.get(sampleKey);
  const sampleTtl = await redis.ttl(sampleKey);
  console.log(`샘플 확인 [${sampleKey}] = ${sampleVal}, TTL = ${sampleTtl}s`);

  await redis.quit();
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});

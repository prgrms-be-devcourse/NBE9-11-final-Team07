/**
 * H4 부하테스트용 JWT 토큰 일괄 생성 스크립트
 *
 * - (a) 큐 통과 그룹: userId 3~1002 (1000명) -> tokens-gated.json
 * - (b) 직타 baseline 그룹: userId 1003~2002 (1000명) -> tokens-baseline.json
 *
 * 서버가 검증할 때 서명 + 만료시간만 확인하고 email/name 클레임 값 자체는
 * 검증하지 않으므로, 여기서는 실제 DB row와 email이 정확히 일치할 필요가 없다.
 * (userId만 실제 DB의 users.id와 일치하면 됨 - FK/예약 로직에서 필요)
 *
 * 사용법 (JWT_SECRET을 커맨드라인에 직접 입력할 필요 없음):
 *   node queue-generate-tokens.js
 * -> backend/.env 에서 JWT_SECRET을 자동으로 읽어옴
 */

const path = require('path');

// ===== backend/.env 위치 =====
// 이 파일이 popspot/k6/ 밑에 있고, popspot/backend/.env 를 가리키는 경우
const ENV_PATH = path.join(__dirname, '..', 'backend', '.env');

require('dotenv').config({ path: ENV_PATH });

const jwt = require('jsonwebtoken');
const fs = require('fs');

const SECRET = process.env.JWT_SECRET;
if (!SECRET) {
  console.error(`JWT_SECRET을 찾을 수 없습니다. ${ENV_PATH} 파일 경로와 내용을 확인하세요.`);
  console.error('경로가 다르면 스크립트 상단의 ENV_PATH를 실제 backend/.env 위치로 수정하세요.');
  process.exit(1);
}

// ===== 실제 DB에서 확인된 값으로 맞춰야 함 =====
const GATED_RANGE = { start: 3, end: 1002 };       // (a) 큐 통과 그룹, 1000명
const BASELINE_RANGE = { start: 1003, end: 2002 };  // (b) 직타 baseline 그룹, 1000명
const ACCESS_TOKEN_VALIDITY_SECONDS = 3600; // application.yml 기본값과 동일하게

function createToken(userId) {
  const nowSeconds = Math.floor(Date.now() / 1000);
  const payload = {
    sub: String(userId),
    email: `loadtest${userId}@test.com`,
    name: `loadtest${userId}`,
    iat: nowSeconds,
  };
  return jwt.sign(payload, SECRET, {
    algorithm: 'HS512',
    expiresIn: ACCESS_TOKEN_VALIDITY_SECONDS,
  });
}

function generateGroup(range) {
  const result = [];
  for (let userId = range.start; userId <= range.end; userId++) {
    result.push({ userId, token: createToken(userId) });
  }
  return result;
}

const gatedTokens = generateGroup(GATED_RANGE);
const baselineTokens = generateGroup(BASELINE_RANGE);

const outDir = path.join(__dirname, 'tokens');
fs.mkdirSync(outDir, { recursive: true });

fs.writeFileSync(
    path.join(outDir, 'tokens-gated.json'),
    JSON.stringify(gatedTokens, null, 2)
);
fs.writeFileSync(
    path.join(outDir, 'tokens-baseline.json'),
    JSON.stringify(baselineTokens, null, 2)
);

console.log(`gated 그룹: ${gatedTokens.length}개 -> ${outDir}/tokens-gated.json`);
console.log(`baseline 그룹: ${baselineTokens.length}개 -> ${outDir}/tokens-baseline.json`);
console.log(`토큰 만료: 발급 후 ${ACCESS_TOKEN_VALIDITY_SECONDS}초 (${ACCESS_TOKEN_VALIDITY_SECONDS / 60}분) - 이 시간 안에 k6 테스트를 끝내야 함`);
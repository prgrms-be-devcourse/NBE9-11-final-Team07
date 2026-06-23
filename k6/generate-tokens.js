const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const secret = process.env.JWT_SECRET || '68f28b12fc570e4d40dfb17f6784e5f49ae9c1090b96cddcef19e6e72a01fee1';
const firstUserId = Number(process.env.FIRST_USER_ID || 900001);
const count = Number(process.env.USER_COUNT || 171000);
const output = process.env.OUTPUT || path.join(__dirname, 'tokens.json');
const expiresInSeconds = Number(process.env.EXPIRES_IN_SECONDS || 60 * 60);
function base64Url(value) {
  return Buffer.from(value).toString('base64').replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
}
function sign(input) {
  return crypto.createHmac('sha256', secret).update(input).digest('base64').replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
}
function tokenFor(userId) {
  const nowSeconds = Math.floor(Date.now() / 1000);
  const header = { alg: 'HS256', typ: 'JWT' };
  const payload = {
    sub: String(userId),
    email: `load-user-${userId}@test.com`,
    name: `load-user-${userId}`,
    iat: nowSeconds,
    exp: nowSeconds + expiresInSeconds,
  };
  const signingInput = `${base64Url(JSON.stringify(header))}.${base64Url(JSON.stringify(payload))}`;
  return `${signingInput}.${sign(signingInput)}`;
}
const tokens = Array.from({ length: count }, (_, index) => tokenFor(firstUserId + index));
fs.writeFileSync(output, `${JSON.stringify(tokens, null, 2)}\n`);
console.log(`Generated ${tokens.length} tokens: ${output}`);

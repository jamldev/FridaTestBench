import test from 'node:test';
import assert from 'node:assert/strict';
import { DEFAULT_PACKAGE_NAME, LABELS, extractVerdicts } from './verdicts.mjs';
import { createIntegrityServer } from './server.mjs';

const requestHash = 'a'.repeat(43);
const now = 1_800_000_000_000;
function payload(labels = LABELS) {
  return {
    tokenPayloadExternal: {
      requestDetails: { requestPackageName: DEFAULT_PACKAGE_NAME, requestHash, timestampMillis: String(now) },
      deviceIntegrity: { deviceRecognitionVerdict: labels },
    },
  };
}

test('maps each returned label independently, including all true and all false', () => {
  for (const labels of [LABELS, [LABELS[1]], [LABELS[2]], [], ['MEETS_VIRTUAL_INTEGRITY']]) {
    const result = extractVerdicts(payload(labels), requestHash, DEFAULT_PACKAGE_NAME, now);
    for (const label of LABELS) assert.equal(result[label], labels.includes(label));
  }
});

test('an omitted deviceRecognitionVerdict is a valid all-false result', () => {
  const decoded = payload();
  delete decoded.tokenPayloadExternal.deviceIntegrity.deviceRecognitionVerdict;
  const result = extractVerdicts(decoded, requestHash, DEFAULT_PACKAGE_NAME, now);
  for (const label of LABELS) assert.equal(result[label], false);
});

test('rejects mismatched package, hash, stale and future timestamps', () => {
  for (const [key, value] of [
    ['requestPackageName', 'other.app'], ['requestHash', 'other-hash'],
    ['timestampMillis', now - 120_001], ['timestampMillis', now + 30_001],
    ['timestampMillis', 'invalid'], ['timestampMillis', null],
  ]) {
    const decoded = payload();
    decoded.tokenPayloadExternal.requestDetails[key] = value;
    assert.throws(() => extractVerdicts(decoded, requestHash, DEFAULT_PACKAGE_NAME, now));
  }
});

test('missing or malformed Google payload is an error instead of all false', () => {
  for (const device of [undefined, null, [], { deviceRecognitionVerdict: null },
    { deviceRecognitionVerdict: 'MEETS_DEVICE_INTEGRITY' }, { deviceRecognitionVerdict: [true] }]) {
    const decoded = payload();
    decoded.tokenPayloadExternal.deviceIntegrity = device;
    assert.throws(() => extractVerdicts(decoded, requestHash, DEFAULT_PACKAGE_NAME, now));
  }
});

test('HTTP endpoint verifies tokens and preserves errors without fabricating verdicts', async t => {
  let calls = 0;
  const server = createIntegrityServer({ decodeToken: async token => {
    calls++;
    if (token === 'fail') throw new Error('secret credentials');
    const decoded = payload([LABELS[1]]);
    decoded.tokenPayloadExternal.requestDetails.timestampMillis = String(Date.now());
    return decoded;
  } });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  t.after(() => new Promise(resolve => server.close(resolve)));
  const url = `http://127.0.0.1:${server.address().port}/integrity/verify`;
  const post = body => fetch(url, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body),
  });
  const success = await post({ integrityToken: 'token', requestHash });
  assert.equal(success.status, 200);
  assert.deepEqual(await success.json(), {
    requestHash, MEETS_STRONG_INTEGRITY: false, MEETS_DEVICE_INTEGRITY: true, MEETS_BASIC_INTEGRITY: false,
  });
  const invalid = await post({ integrityToken: 'token' });
  assert.equal(invalid.status, 400);
  assert.equal(calls, 1);
  const failure = await post({ integrityToken: 'fail', requestHash });
  assert.equal(failure.status, 502);
  assert.equal((await failure.text()).includes('secret credentials'), false);
  const invalidJson = await fetch(url, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{',
  });
  assert.equal(invalidJson.status, 400);
  assert.equal((await fetch(url)).status, 404);
});

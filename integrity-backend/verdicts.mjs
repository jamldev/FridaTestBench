export const DEFAULT_PACKAGE_NAME = 'com.example.fridatestbench';
export const LABELS = [
  'MEETS_STRONG_INTEGRITY',
  'MEETS_DEVICE_INTEGRITY',
  'MEETS_BASIC_INTEGRITY',
];

export class RequestError extends Error {
  constructor(message, statusCode = 400) {
    super(message);
    this.statusCode = statusCode;
  }
}

export function validateRequest(body) {
  if (!body || typeof body.integrityToken !== 'string' ||
      body.integrityToken.length === 0 || body.integrityToken.length > 60_000 ||
      typeof body.requestHash !== 'string' || !/^[A-Za-z0-9_-]{43}$/.test(body.requestHash)) {
    throw new RequestError('Expected integrityToken and a base64url SHA-256 requestHash.');
  }
}

export function extractVerdicts(decoded, requestHash, packageName, now = Date.now()) {
  const payload = decoded?.tokenPayloadExternal;
  const details = payload?.requestDetails;
  const timestamp = Number(details?.timestampMillis);
  if (details?.requestPackageName !== packageName || details?.requestHash !== requestHash ||
      !Number.isSafeInteger(timestamp) || timestamp <= 0 ||
      now - timestamp > 120_000 || timestamp - now > 30_000) {
    throw new RequestError('Token package, request hash, or timestamp did not match.', 422);
  }
  const device = payload?.deviceIntegrity;
  if (!device || typeof device !== 'object' || Array.isArray(device)) {
    throw new RequestError('Google response is missing device integrity.', 502);
  }
  // Google omits this array when no device labels are met; that is a valid all-false verdict.
  const labels = device.deviceRecognitionVerdict === undefined ? [] : device.deviceRecognitionVerdict;
  if (!Array.isArray(labels) || labels.some(label => typeof label !== 'string')) {
    throw new RequestError('Google response has malformed device labels.', 502);
  }
  return {
    requestHash,
    ...Object.fromEntries(LABELS.map(label => [label, labels.includes(label)])),
  };
}

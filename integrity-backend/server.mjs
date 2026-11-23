import { createServer } from 'node:http';
import { pathToFileURL } from 'node:url';
import { DEFAULT_PACKAGE_NAME, RequestError, extractVerdicts, validateRequest } from './verdicts.mjs';

// Credentials stay on the server. GoogleAuth supports an attached service account or
// GOOGLE_APPLICATION_CREDENTIALS; the Android APK only contains project number and URL.
async function createGoogleDecoder(packageName) {
  const { GoogleAuth } = await import('google-auth-library');
  const auth = new GoogleAuth({ scopes: ['https://www.googleapis.com/auth/playintegrity'] });
  const client = await auth.getClient();
  return async integrityToken => {
    const response = await client.request({
      url: `https://playintegrity.googleapis.com/v1/${encodeURIComponent(packageName)}:decodeIntegrityToken`,
      method: 'POST',
      data: { integrity_token: integrityToken },
      timeout: 20_000,
      // Re-decoding standard tokens can clear the verdicts through replay protection.
      retry: false,
    });
    return response.data;
  };
}

function sendJson(response, status, body) {
  response.writeHead(status, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' });
  response.end(JSON.stringify(body));
}

export function createIntegrityServer({ decodeToken, packageName = DEFAULT_PACKAGE_NAME }) {
  const server = createServer(async (request, response) => {
    if (request.method !== 'POST' || request.url !== '/integrity/verify') {
      sendJson(response, 404, { error: 'Use POST /integrity/verify.' });
      request.resume();
      return;
    }
    if (request.headers['content-type']?.split(';')[0].trim() !== 'application/json') {
      sendJson(response, 415, { error: 'Use application/json.' });
      request.resume();
      return;
    }
    try {
      let size = 0;
      const chunks = [];
      for await (const chunk of request) {
        size += chunk.length;
        if (size > 64 * 1024) throw new RequestError('Request body is too large.', 413);
        chunks.push(chunk);
      }
      let body;
      try {
        body = JSON.parse(Buffer.concat(chunks).toString('utf8'));
      } catch {
        throw new RequestError('Invalid JSON.');
      }
      validateRequest(body);
      let decoded;
      try {
        decoded = await decodeToken(body.integrityToken);
      } catch {
        // Do not return Google's errors: they can contain credentials or token data.
        throw new RequestError('Google token decoding failed. Check server credentials and API setup.', 502);
      }
      const result = extractVerdicts(decoded, body.requestHash, packageName);
      sendJson(response, 200, result);
    } catch (error) {
      if (!response.destroyed) {
        sendJson(response, error instanceof RequestError ? error.statusCode : 500, {
          error: error instanceof RequestError ? error.message : 'Integrity verification failed.',
        });
      }
    }
  });
  server.requestTimeout = 30_000;
  server.headersTimeout = 10_000;
  return server;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const packageName = process.env.PLAY_INTEGRITY_PACKAGE_NAME || DEFAULT_PACKAGE_NAME;
  const decodeToken = await createGoogleDecoder(packageName);
  const port = Number(process.env.PORT || 8080);
  const host = process.env.HOST || '127.0.0.1';
  createIntegrityServer({ decodeToken, packageName }).listen(port, host, () => {
    console.log(`Play Integrity backend listening on ${host}:${port}. Put HTTPS in front of this server.`);
  });
}

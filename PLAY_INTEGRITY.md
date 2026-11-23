# Google Play Integrity panel

The app runs a Standard Play Integrity request on launch and supports manual reruns
in a separate **Google Play Integrity** card. It shows the exact returned labels as
`true`/`false`: `MEETS_STRONG_INTEGRITY`, `MEETS_DEVICE_INTEGRITY`, and
`MEETS_BASIC_INTEGRITY`. Each label is evaluated independently.

Without configuration the card shows **Not configured**. Network/API/verification
errors show **No result**, not `false`. A verified response with no device labels
shows all three as `false`. These are diagnostic results, not an authorization policy.

## Google setup (required for real results)

1. Create a Google Cloud project, enable **Play Integrity API**, and record its
   numeric **project number** (not its project ID).
2. Link this Cloud project to `com.example.fridatestbench` in the Google Play
   Console under **Protected with Play → Play Integrity API**. Register/distribute
   the app using your chosen Play testing track and follow Google's testing setup.
3. Under **Change responses**, enable the additional device labels for BASIC and
   STRONG integrity. Without this opt-in, absent optional labels cannot establish
   whether the device meets those levels; the panel reports whether Google returned
   the labels. The panel reports each label independently.
4. Create/use a service account in the linked Cloud project for token decoding.
   Keep credentials on the backend. Do not copy a service-account key into the app.

Google's instructions:
- [Setup and optional labels](https://developer.android.com/google/play/integrity/setup)
- [Standard requests and server decoding](https://developer.android.com/google/play/integrity/standard)
- [Verdict meanings](https://developer.android.com/google/play/integrity/verdicts)

## Included backend

`integrity-backend` is a small Node.js 20+ service that uses Google's auth library
and Application Default Credentials to call `decodeIntegrityToken`. It checks the
decoded package name, request hash, and timestamp before returning booleans.
It never logs tokens or returns credentials/Google response bodies to clients.

From PowerShell, with your service-account credentials stored outside the repo:

```powershell
cd D:\cybersec\testbench\integrity-backend
npm ci
$env:GOOGLE_APPLICATION_CREDENTIALS = 'C:\secure\play-integrity-service-account.json'
npm start
```

On a hosted service, an attached service account can supply Application Default
Credentials instead of a key file. `PORT` defaults to `8080`; `HOST` defaults to
`127.0.0.1`. Set `HOST=0.0.0.0` when the hosting platform requires it. Put HTTPS in
front of the service and use the full `/integrity/verify` endpoint URL in the app.
Restrict access/rate-limit it at your hosting layer before exposing this diagnostic
service publicly, because each call consumes Google decoding quota. No deployment
or Google account resources are provisioned by this repository change.

## App configuration

Add these to your user Gradle properties or supply them as `-P` arguments:

```properties
playIntegrityCloudProjectNumber=123456789012
playIntegrityBackendUrl=https://your-server.example/integrity/verify
```

Rebuild the app after setting them; the defaults are `0` and an empty URL. Only
the public project number and backend URL are compiled into `BuildConfig`.
The app requires an HTTPS endpoint and does not follow token-upload redirects.

### Backend contract

`POST /integrity/verify`, `Content-Type: application/json`:

```json
{ "integrityToken": "encrypted-google-token", "requestHash": "base64url-sha256-hash" }
```

The backend decodes once, checks `requestDetails`, and returns:

```json
{
  "requestHash": "base64url-sha256-hash",
  "MEETS_STRONG_INTEGRITY": false,
  "MEETS_DEVICE_INTEGRITY": true,
  "MEETS_BASIC_INTEGRITY": true
}
```

The hash must match the request and all three values must be JSON booleans.
An HTTP error is displayed as a failed check, not three failed integrity levels.

## Verification

```powershell
cd D:\cybersec\testbench\integrity-backend
npm test
cd ..
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Backend tests cover independent/empty verdicts, omitted labels, malformed payloads,
request binding/freshness, and the HTTP contract without Google credentials.
Real device verdicts require the Google setup above and a configured backend;
sideloaded/debug installations and Play Console test overrides can affect results.

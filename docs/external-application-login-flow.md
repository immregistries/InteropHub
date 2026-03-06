# InteropHub External Application Login Flow

This guide describes how an external application initiates login in InteropHub and receives user identity data back.

## Summary

1. External app redirects the browser to InteropHub `/home` with required query parameters.
2. User authenticates in InteropHub (or is already authenticated).
3. InteropHub redirects the browser back to the external app `return_to` URL with a one-time `code` (and original `state`).
4. External app server calls InteropHub `POST /api/auth/exchange` with `app_code` and `code`.
5. InteropHub returns user profile data as JSON.

## Base URLs

Typical local base URL is:

- `http://localhost:8080/hub`

Key endpoints:

- Login entry: `GET /home`
- Code exchange: `POST /api/auth/exchange`

## 1. Redirect User To InteropHub

Your app starts authentication by redirecting the browser to:

- `GET {hubBaseUrl}/home`

Required query parameters:

- `app_code`: Registered app code in InteropHub app registry.
- `return_to`: Your app callback URL. Must match an allowlisted base URL for this app.
- `state`: Opaque CSRF/correlation value (8 to 255 chars).
- `requested_url`: Original URL user wanted in your app (absolute HTTP/HTTPS URL).

Example:

```text
http://localhost:8080/hub/home?app_code=my-app&return_to=https%3A%2F%2Fapp.example.org%2Fauth%2Fcallback&state=7ecf5f8a9b43d1aa&requested_url=https%3A%2F%2Fapp.example.org%2Fworkspace%2F42
```

Notes:

- All values should be URL-encoded.
- `return_to` must be absolute `http` or `https`.
- `requested_url` must also be absolute `http` or `https`.
- If parameters are invalid, InteropHub will not issue a callback code.

## 2. User Authentication Behavior

InteropHub behavior depends on user session state:

- Already signed in to InteropHub:
  - InteropHub immediately creates a one-time login code and redirects to your callback URL.
- Not signed in:
  - InteropHub prompts for email, may collect profile for new users, issues a magic link, then continues after link click.
  - After successful magic-link sign-in, InteropHub creates a one-time login code and redirects to your callback URL.

## 3. Callback To External App

On success, browser is redirected to your `return_to` URL with query parameters:

- `code`: One-time code to exchange server-to-server.
- `state`: The same state value you originally sent.

Example callback URL:

```text
https://app.example.org/auth/callback?code=Z8Lh8L2tX5xYV...&state=7ecf5f8a9b43d1aa
```

What your app should do on callback:

1. Verify `state` matches the value stored in the user session.
2. Send `code` and `app_code` from your backend to InteropHub exchange API.

## 4. Exchange Code For User Identity

Call InteropHub exchange endpoint from your server:

- `POST {hubBaseUrl}/api/auth/exchange`
- `Content-Type: application/json`

Request body:

```json
{
  "app_code": "my-app",
  "code": "<code-from-callback>"
}
```

Example cURL:

```bash
curl -X POST "http://localhost:8080/hub/api/auth/exchange" \
  -H "Content-Type: application/json" \
  -d '{"app_code":"my-app","code":"<code-from-callback>"}'
```

Successful response (`200 OK`):

```json
{
  "hub_user_id": 123,
  "email": "user@example.org",
  "name": "Jane Doe",
  "organization": "AIRA",
  "title": "Developer",
  "requested_url": "https://app.example.org/workspace/42",
  "issued_at": "2026-03-05T20:34:12Z",
  "expires_in_seconds": 3600
}
```

Error response (`400` or `404`):

```json
{
  "error": "Invalid app_code or code."
}
```

## 5. Code Rules And Security Expectations

- Codes are one-time use.
- Codes expire quickly (currently 5 minutes in InteropHub).
- Exchange fails if:
  - `app_code` is unknown,
  - `code` is unknown,
  - `code` is expired,
  - `code` has already been consumed,
  - `code` was issued for a different `app_code`.
- Always validate `state` before exchange.
- Perform exchange server-to-server; do not expose exchange credentials/logic in browser code.

## 6. Recommended External App Implementation Pattern

1. Generate and store a random `state` in user session.
2. Build InteropHub `/home` URL with required parameters.
3. Redirect user to InteropHub.
4. Handle callback at `return_to` route.
5. Validate `state`.
6. Exchange `code` via backend `POST /api/auth/exchange`.
7. Create your own local session/token for the user.
8. Redirect user to `requested_url` if present and allowed by your app rules.

## 7. Operational Notes

- For local/dev, InteropHub email sending may be disabled by runtime property:
  - `-Dinterophub.email.send.enabled=true` to enable SMTP sending.
- In disabled mode, the magic link is still generated and shown in InteropHub UI, which is useful for testing.

## 8. Integration Checklist

- `app_code` exists and is enabled in InteropHub.
- `return_to` base URL is allowlisted for the app.
- `state` generation and verification are implemented.
- Callback handler reads `code` and `state`.
- Backend exchange call is implemented.
- Exchange errors are handled and shown to users.
- Local app session creation after successful exchange is implemented.

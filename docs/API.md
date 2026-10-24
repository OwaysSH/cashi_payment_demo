# Payments API

Contract of the Ktor backend in [`server/`](../server). The request/response models and error codes
are defined once in `shared` (`cashi.payment.oways.shared.api`) and used by both the server and the
app's API client.

- **Base URL (local):** `http://127.0.0.1:8080` (configurable, see the README "Backend" section).
  An override that includes a path needs a trailing slash (e.g. `http://host/api/`); otherwise the
  client resolves `payments` against the host root. For how the app's base URL is set and
  overridden, see the README section [Connection options](../README.md#connection-options).
- **Format:** JSON (`Content-Type: application/json`), UTF-8
- **Authentication:** none (demo)

## GET /health

Liveness check.

```http
GET /health
```
```http
HTTP/1.1 200 OK

{"status":"UP"}
```

## POST /payments

Validates and processes a payment and returns the processed payment.

### Request

| Header | Required | Description |
|---|---|---|
| `Content-Type` | yes | Must be `application/json`. |
| `Idempotency-Key` | no | Makes the request safe to retry; see [Idempotency](#idempotency). |

```json
{ "recipientEmail": "alice@example.com", "amount": 10.5, "currency": "USD" }
```

| Field | Type | Rules |
|---|---|---|
| `recipientEmail` | string | Surrounding whitespace is trimmed. At most 254 characters (local part at most 64), shaped `local@domain.tld` with a TLD of 2+ letters. |
| `currency` | string | `USD` or `EUR`, exact uppercase ISO 4217 code. |
| `amount` | number | A JSON number (a quoted `"10.5"` is rejected). Greater than 0, at most 2 decimals, at most `1000000.00`. |

- All three fields are required. Unknown extra fields are ignored.
- Validation runs in the order **email → currency → amount**, and only the first failure is
  reported.

### Response: 201 Created

```json
{
  "id": "58148a0c-1f2d-45ea-b233-ad188103a693",
  "recipientEmail": "alice@example.com",
  "amount": 10.5,
  "currency": "USD",
  "createdAt": "2026-10-22T21:58:45.069114Z"
}
```

| Field | Description |
|---|---|
| `id` | Server-generated payment ID (UUID). |
| `recipientEmail` | The trimmed recipient email. |
| `amount` | The amount in major units (stored internally as exact minor units). |
| `currency` | ISO 4217 code. |
| `createdAt` | Processing time, ISO-8601 UTC. |

A replayed response also carries the header `Idempotent-Replayed: true`.

The app saves the processed payment to Firestore after a 201. Transaction history shows Firestore's
server timestamp, not `createdAt`.

## Errors

Every error, from any endpoint, has the same body:

```json
{ "code": "INVALID_EMAIL", "message": "Invalid recipient email" }
```

Clients must branch on `code`. `message` is for developers and logs, not for end users. Clients
should treat an unknown `code` as a generic error, because new codes may be added.

| Status | `code` | When |
|---|---|---|
| 400 | `INVALID_EMAIL` | `recipientEmail` fails validation. |
| 400 | `UNSUPPORTED_CURRENCY` | `currency` is not `USD` or `EUR`. |
| 400 | `INVALID_AMOUNT` | `amount` is ≤ 0, has more than 2 decimals, or exceeds 1,000,000.00. |
| 400 | `MALFORMED_REQUEST` | Body is missing, not valid JSON, lacks a field, or has a wrong type (e.g. a quoted amount). |
| 400 | `INVALID_IDEMPOTENCY_KEY` | `Idempotency-Key` header is present but invalid. |
| 404 | `NOT_FOUND` | No such endpoint. |
| 405 | `METHOD_NOT_ALLOWED` | Endpoint exists, method doesn't (e.g. `GET /payments`). |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | `Content-Type` is not `application/json`. |
| 422 | `IDEMPOTENCY_KEY_MISMATCH` | `Idempotency-Key` was already used with a different payment. |
| 500 | `INTERNAL_ERROR` | Unexpected server error (details are logged server-side only). |

For `POST /payments`, the checks run in this order: the `Idempotency-Key` header, then the body's
Content-Type and JSON shape, then validation, then idempotency matching.

## Idempotency

Send `Idempotency-Key: <unique string>` (the app uses a new UUID per form submission and reuses it
when retrying that submission).

- **Key format:** 1–255 printable ASCII characters, no surrounding whitespace. Any opaque string is
  allowed; a UUID is recommended.
- **First request with a key:** processed normally, 201. The result is remembered.
- **Same key, same payment:** the original 201 is returned again (same `id` and `createdAt`) with
  `Idempotent-Replayed: true`; no new payment is created. "Same payment" is compared after
  normalization, so `" alice@example.com "` vs `"alice@example.com"`, or `10.5` vs `10.50`, match.
- **Same key, different payment:** 422 `IDEMPOTENCY_KEY_MISMATCH`; nothing is created.
- **Validation failures are not remembered:** a corrected retry with the same key is processed.
- **Concurrent requests with one key** create exactly one payment.
- **Without the header,** every request creates a new payment.
- **Storage:** in server memory, kept for 24 hours, at most 10,000 keys (least recently used are
  evicted), lost on restart. Production should require the header and use a shared store (see the
  README "Production hardening").

## curl examples

The examples use bash. On Windows, run them from Git Bash if possible. In PowerShell, call
`curl.exe` (not PowerShell's `curl` alias, which is `Invoke-WebRequest`) and generate the key with
`$KEY = [guid]::NewGuid()` instead of `KEY=$(uuidgen)`.

```bash
BASE=http://127.0.0.1:8080
JSON="Content-Type: application/json"

# Health
curl -s $BASE/health

# Success (201)
curl -s -i -X POST $BASE/payments -H "$JSON" \
  -d '{"recipientEmail":"alice@example.com","amount":10.5,"currency":"USD"}'

# Validation error (400 INVALID_EMAIL)
curl -s -i -X POST $BASE/payments -H "$JSON" \
  -d '{"recipientEmail":"bad","amount":10.5,"currency":"USD"}'

# Malformed: missing field (400 MALFORMED_REQUEST)
curl -s -i -X POST $BASE/payments -H "$JSON" \
  -d '{"recipientEmail":"alice@example.com","amount":10.5}'

# Wrong content type (415 UNSUPPORTED_MEDIA_TYPE)
curl -s -i -X POST $BASE/payments -H "Content-Type: text/plain" -d 'hello'

# Idempotency: create (201), replay (201 + Idempotent-Replayed: true), mismatch (422)
KEY=$(uuidgen)
curl -s -i -X POST $BASE/payments -H "$JSON" -H "Idempotency-Key: $KEY" \
  -d '{"recipientEmail":"alice@example.com","amount":10.5,"currency":"USD"}'
curl -s -i -X POST $BASE/payments -H "$JSON" -H "Idempotency-Key: $KEY" \
  -d '{"recipientEmail":"alice@example.com","amount":10.5,"currency":"USD"}'
curl -s -i -X POST $BASE/payments -H "$JSON" -H "Idempotency-Key: $KEY" \
  -d '{"recipientEmail":"alice@example.com","amount":99,"currency":"USD"}'
```
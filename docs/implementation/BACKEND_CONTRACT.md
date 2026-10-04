# Intent gateway hardening contract

Date: 2026-10-04. Implemented; 35 local mocked tests pass. Deployment verification remains pending.

The gateway remains a loopback-only, standard-library local service. It classifies a bounded Hindi/English request into the existing six allowed actions and never executes a phone action. No transcript or bearer token is logged or stored.

Required controls:

- Immutable, validated startup configuration. HTTPS upstream only, no URL credentials, query or fragment. Redirects must not carry credentials to a new destination.
- Per-device bearer tokens supplied as `MAA_DEVICE_TOKENS` JSON (`{"device-id":"long-random-token"}`). Device IDs are bounded, tokens unique and at least 24 printable ASCII characters. Authentication compares every configured token in constant time. Restarting with a token removed revokes it for the new process.
- Legacy `MAA_ACCESS_TOKEN` is accepted only with explicit `MAA_ALLOW_LEGACY_TOKEN=1`, only without a device map, and only on loopback. It remains a shared local-pilot credential, not device-level revocation.
- A bounded number of accepted connection threads; admission rejects excess connections. Every request has an absolute inbound header/body deadline, including clients that drip bytes. Bodies are at most 8192 bytes, with one valid Content-Length, JSON content type and the exact request schema. Chunked bodies and duplicate length/auth headers are rejected.
- Global and per-device sliding-window request limits and an in-memory inference quota. Inference workers have a separate concurrency cap; timed-out work retains its slot until it actually finishes. Client response waits are bounded, so a blocked upstream cannot consume unbounded connection threads or create unbounded inference workers.
- Upstream data is bounded before parsing. Authentication, malformed requests, rate/capacity limits and upstream failures return generic structured JSON. Results are validated again before returning to the phone.

Acceptance uses local loopback sockets and mocked upstream inference only. Tests must exercise partial-body deadlines, slow-drip total deadlines, oversized input, strict schemas, bad/duplicate auth, rate limits, connection and inference capacity, token revocation by restart/config replacement, startup validation and oversized upstream output. Short test-only configuration avoids prolonged blocked requests.

This work does not install or expose a service. Production still needs a supported HTTPS reverse proxy/runtime, provider-side spend limits, deployment secrets/revocation procedures, process supervision, monitoring that excludes transcripts/tokens, and operational tests. In-memory limits reset on restart and are not a durable financial budget. Python threads cannot forcibly cancel DNS or arbitrary upstream library calls; bounded daemon workers prevent unbounded concurrency while the client deadline expires, and process restart is the recovery for permanently stuck workers.

## Configuration and compatibility

Required: `NEBIUS_API_KEY`, `NEBIUS_MODEL`, and `MAA_DEVICE_TOKENS`. The token map associates a short device ID with a unique randomly generated bearer token, for example the placeholder shape `{"poco":"REPLACE_WITH_A_UNIQUE_RANDOM_TOKEN"}`. Generate real values outside source control and configure the matching device token in Maa. Device IDs are not request parameters; a matching token identifies its device. Never use the displayed placeholder as a deployed token.

To revoke a device, remove its entry and restart the process. Editing environment variables does not mutate the running server's immutable configuration. Configuration representations omit both upstream and device secrets. Startup failures produce generic structured error messages without echoing values.

Existing single-token local setups now need `MAA_ALLOW_LEGACY_TOKEN=1` alongside `MAA_ACCESS_TOKEN`, with `MAA_DEVICE_TOKENS` unset. This is an explicit compatibility opt-in; it retains the limitations of a shared token. Per-device and legacy credentials cannot be configured simultaneously.

| Setting | Default | Meaning |
| --- | --- | --- |
| `NEBIUS_BASE_URL` | `https://api.tokenfactory.nebius.com/v1` | HTTPS base; redirects disabled |
| `PORT` | `8080` | Loopback listener port; address stays `127.0.0.1` |
| `MAA_REQUEST_TIMEOUT` | `5` seconds | Absolute inbound header/body deadline |
| `MAA_RESPONSE_TIMEOUT` | `7` seconds | Maximum wait for classification after body validation |
| `MAA_UPSTREAM_TIMEOUT` | `6` seconds | Upstream socket timeout; outer wait also bounds the client response |
| `MAA_MAX_CONNECTIONS` | `16` | Maximum active handler threads |
| `MAA_MAX_INFERENCES` | `2` | Maximum actual inference workers, including timed-out work |
| `MAA_RATE_WINDOW` | `60` seconds | Request-rate sliding window |
| `MAA_GLOBAL_REQUEST_LIMIT` | `240` | GET/POST attempts per window, including unauthorized requests |
| `MAA_DEVICE_REQUEST_LIMIT` | `30` | Authenticated requests per device per window |
| `MAA_INFERENCE_WINDOW` | `86400` seconds | Sliding window for the process-local inference quota |
| `MAA_INFERENCE_LIMIT` | `1000` | Inference attempts in that window, including failed work |

The default 7-second inference response wait fits inside the Android assistant's current 8-second HTTP read timeout with limited network margin. A deployed proxy must use aligned timeouts, and slow-network acceptance remains necessary. Increasing server timeouts requires reviewing the client deadline too.

Fixed bounds: 8192-byte request body; 1000-character nonblank transcript; 24576 total inbound bytes across headers/body; 32768-byte upstream JSON envelope; 2048-character model result. The exact client request schema is `{"text":"..."}`. Extra/duplicate properties, duplicate Authorization/Content-Length headers, missing length, chunked bodies and unsupported encodings are rejected. Response schema remains exactly `{"action":"allowed-action"}`.

## Verification

Command: `python -m unittest -v test_server`, run from `backend` on Python 3.14.7. Final run: **35 tests passed in 1.307 seconds**.

Tests cover valid Hindi requests; all six allowed actions; arbitrary action rejection; discarded extra model fields; invalid/duplicate/deep JSON; malformed tokens and configuration; constant-time comparison across configured tokens; immutable/revoked credentials; explicit legacy mode; partial header/body and continuous slow-drip deadlines; aggregate header/body bounds; oversized body/upstream responses; per-device/global rate limits and expiry; connection/inference concurrency; timeout behavior retaining a busy inference slot; process-local inference quota; unsupported upstream encoding; redirect refusal; secret-free failure responses and logs; structured startup errors.

All network traffic in these tests stayed on ephemeral loopback ports. Upstream inference and credentials were mocks. No installed service, real provider call, public listener, deployment or phone action was performed. Passing tests establish these bounded contracts, not production deployment readiness.

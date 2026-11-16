# Authentication rate limiting

## Routes and behavior

The backend applies a servlet filter only to `POST /api/auth/login` and `POST /api/auth/register`. Requests below a configured limit continue to the existing controller and validation flow. Excess requests receive HTTP `429 Too Many Requests`, a JSON error body with the same top-level fields used by the API error handler, and a `Retry-After` header in seconds. The filter does not read or log request bodies, credentials, JWTs, or authorization headers.

Login and registration use independent keys and counters. Malformed requests are counted because the filter runs before MVC request-body parsing; requests outside the two exact POST routes are not limited by this filter.

## Initial limits by profile

| Profile | Login | Registration | Enabled by default |
|---|---:|---:|---|
| `dev` | 300 requests / 300 seconds | 120 requests / 3,600 seconds | Yes |
| `test` | 1,000 requests / 60 seconds | 1,000 requests / 60 seconds | No |
| `prod` | 300 requests / 300 seconds | 120 requests / 3,600 seconds | Yes |

Override the numeric limits and windows with `AUTH_LOGIN_RATE_LIMIT`, `AUTH_LOGIN_RATE_WINDOW_SECONDS`, `AUTH_REGISTER_RATE_LIMIT`, and `AUTH_REGISTER_RATE_WINDOW_SECONDS`. Development and test also accept `AUTH_RATE_LIMIT_ENABLED`; production keeps the limiter enabled. Production Compose explicitly supplies the numeric values, with the listed defaults. Limits and window lengths must be positive.

The values are starting points, not measured capacity thresholds. They are deliberately generous for shared-address traffic. The limiter uses fixed windows, so a client may make up to the configured amount near a window boundary and then another window's amount after reset.

## Proxy identity and trust boundary

In development and test, the limiter keys requests by the direct socket peer (`getRemoteAddr`) and ignores `X-Forwarded-For`.

In production, Nginx overwrites `X-MedQueue-Ingress-Peer` with its socket peer address when proxying `/api/`. The backend reads only that private header and never falls back to `X-Forwarded-For` or an unresolved request address. A missing or invalid value goes into one fixed unresolved-peer bucket. This header is safe only while the backend remains private to the Compose application network and untrusted clients cannot connect directly to the backend. The current topology has Nginx as the only other service on that network; the AWS security group must also keep direct backend access blocked.

Because Nginx's immediate peer is the ALB, the production key is an ALB node address, not the end user's address. Users served by the same ALB node share a bucket. The selected thresholds reduce false blocks in shared-IP environments, but this coarse key is not per-user or global protection. If traffic or replica count grows, use an edge limiter with a validated client-IP chain or a shared rate-limit store; do not start trusting arbitrary forwarding headers.

## State and operational limitations

`InMemoryAuthRateLimiter` stores synchronized fixed-window counters in the backend process. Each backend instance has independent counters, and all counters reset on process restart. The map is bounded at 20,000 active keys; while full, unseen keys are denied until existing windows expire. This is a coarse abuse-control layer, not distributed protection or account lockout. The project does not add Redis or another rate-limit service.

The current frontend uses Axios for both auth requests and displays the API `message`, so a 429 response is shown through its existing error handling. Rate-limit responses are not logged by the filter.

## Verification checklist

- [ ] Confirm the backend is reachable only through the intended Nginx service.
- [ ] Confirm production Nginx overwrites `X-MedQueue-Ingress-Peer` and the backend ignores browser-supplied forwarding headers.
- [ ] Tune the profile values using actual shared-address traffic and observed login/registration rates.
- [ ] If using multiple backend instances, decide whether independent per-instance limits are acceptable or configure a shared edge/store limiter.

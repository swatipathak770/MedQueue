# MedQueue deployment: Vercel, Render, and TiDB Cloud

This document prepares the deployment configuration; no cloud service was created and no database was contacted. Keep the existing AWS deployment guide as historical/reference documentation. Production must use the `prod` Spring profile and must not use the `dev` profile.

## Frontend on Vercel

1. Import the GitHub repository into Vercel and set the project Root Directory to `medqueue-frontend`.
2. Use `npm run build` as the build command and `dist` as the output directory. Vercel detects the Vite framework; the checked-in `vercel.json` rewrites client-side routes to `index.html` for direct refreshes.
3. Set `VITE_API_BASE_URL` to the Render service origin, for example `https://<render-service>.onrender.com` (no `/api` suffix). Set `VITE_WS_URL` to `https://<render-service>.onrender.com/ws`.
4. Redeploy after changing either variable. Vite embeds `VITE_*` values at build time. These variables are public browser configuration: never put credentials, signing keys, or other secrets in them.

The app sends REST requests to `/api/...` under `VITE_API_BASE_URL`. SockJS/STOMP connects to `/ws` under `VITE_WS_URL`; the Spring endpoint permits the configured CORS origins and still requires the existing JWT STOMP authentication. If the Render URL changes, update both Vercel variables and Render's `CORS_ALLOWED_ORIGINS`.

## Backend on Render

Create a Render Web Service from the repository using the Docker runtime, Root Directory `medqueue-backend`, and the existing `Dockerfile`. Its build stage uses Maven and Java 17. Render supplies `PORT`; Spring now listens on `${PORT}` with an 8080 fallback for local/Compose use. Configure the Render health check path as `/actuator/health`.

Set these environment variables in Render's private environment configuration:

| Variable | Value/purpose |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DB_URL` | TiDB Cloud JDBC URL, e.g. `jdbc:mysql://<tidb-host>:4000/<database>?sslMode=VERIFY_IDENTITY&serverTimezone=UTC` |
| `DB_USERNAME` / `DB_PASSWORD` | TiDB database user and password |
| `JWT_SECRET` | Fresh Base64-encoded cryptographically random key decoding to at least 32 bytes; production validation also rejects weak/repeated patterns |
| `JWT_EXPIRATION_SECONDS` | Positive token lifetime in seconds; defaults to `3600` |
| `CORS_ALLOWED_ORIGINS` | Exact Vercel origin, e.g. `https://<project>.vercel.app`; comma-separate additional exact HTTPS custom domains |
| `BOOTSTRAP_ADMIN_EMAIL` / `BOOTSTRAP_ADMIN_PASSWORD` | Optional initial administrator; provide both, use a unique password of at least 20 characters, then remove both after successful creation |

The JDBC `sslMode=VERIFY_IDENTITY` setting requires TLS and certificate/hostname verification. TiDB Cloud connection details and IP allowlisting must match the selected TiDB cluster and Render egress arrangement. Do not copy AWS RDS truststore variables or disable TLS verification. Keep Swagger/OpenAPI disabled, as configured in production. Do not expose environment values in build logs.

Render health check path: `/actuator/health`. Only the health endpoint is exposed; details and components are hidden. Security, JWT, role checks, and rate limits remain enabled. The current STOMP broker is in-memory and supports one backend instance; scaling to multiple instances requires shared broker/session handling. Verify the selected Render plan supports persistent WebSocket connections and note any instance sleep/restart behavior before relying on live queue updates.

## TiDB Cloud database and compatibility review

Create a new TiDB Cloud database/cluster separately, create a least-privilege application account, configure the cluster's network access, and obtain its TLS JDBC connection details. Do not point the app at the original local database. No TiDB connection or data transfer is part of this preparation.

The project uses MySQL Connector/J, Flyway's MySQL module, InnoDB-style DDL, `BIGINT AUTO_INCREMENT`, `ENUM`, `BIT(1)`, `DATE`, `TIME(6)`, `DATETIME(6)`, `TIMESTAMP(6)`, unique and secondary indexes, and foreign keys. The repository's two migrations contain no stored procedures, triggers, or MySQL-specific native query; repository queries are JPQL. These constructs are broadly in TiDB's MySQL compatibility surface, but migration execution and Hibernate schema validation against the chosen TiDB Cloud tier/version have not been tested.

Compatibility items to verify against the actual cluster before migration:

- Both migration files explicitly declare `utf8mb4_0900_ai_ci`. TiDB collation behavior depends on TiDB version/configuration: current TiDB supports the collation, while versions before 7.4 may fall back to `utf8mb4_bin` for client connection collation. Collation changes case/accent and trailing-space comparison behavior, so confirm the target behavior before modifying schema or migrating data.
- TiDB foreign-key enforcement is generally available starting in v8.5; earlier releases have limitations/feature status differences. Confirm the selected TiDB Cloud cluster version supports enforced foreign keys as required by this schema.
- Review `AUTO_INCREMENT` allocation semantics if the application or migration process assumes gapless or globally monotonic IDs. IDs are unique, but TiDB may allocate them in batches.
- Confirm Flyway's version support with TiDB and run schema creation/validation only on a newly created empty target database after an explicit review. Hibernate is `validate`, not `update`; Flyway applies versioned SQL automatically at application startup unless separately controlled. Do not start the production app against any existing or unverified database.

No schema changes are recommended in this preparation. Resolve any version or collation mismatch through a reviewed target-schema plan before modifying migrations.

## Safe original database migration procedure

1. Keep the original local MedQueue database untouched. Do not point a cloud service, Flyway, Hibernate, or a migration utility at it.
2. Make and verify an independent backup using an approved local procedure; preserve the source database and its backup.
3. Create a separate TiDB target and inspect its version, collation support, and foreign-key behavior. Review the two checked-in Flyway migrations and compare a target schema created in a disposable database against the expected schema.
4. Test the transfer on a disposable target copy first. Validate table counts, key relationships, unique constraints, representative application workflows, and Hibernate `validate` before scheduling any final transfer.
5. For the eventual cutover, stop or freeze writes using an operator-approved plan, transfer a fresh source snapshot to the target, reconcile counts and constraints, and only then change Render's `DB_URL` and credentials to the verified TiDB database. Keep the source and backup intact until the cutover is accepted. Do not baseline or migrate an unknown schema.

This is a planning checklist only; it does not execute database commands.

## Deployment verification checklist

- [ ] Render builds with Java 17 and starts with `SPRING_PROFILES_ACTIVE=prod` and the injected `PORT`.
- [ ] Render `/actuator/health` returns HTTP 200 without exposing database or component details.
- [ ] TiDB TLS verifies the cluster certificate and hostname; application credentials have only required privileges.
- [ ] Render `CORS_ALLOWED_ORIGINS` exactly matches the Vercel production origin(s), with no wildcard or path.
- [ ] Login and role-protected patient, doctor, and admin workflows work; a valid JWT is required for protected REST and STOMP operations.
- [ ] Vercel production build contains no localhost backend URL; direct refresh of `/login`, `/patient/...`, `/doctor/...`, and `/admin/...` serves the SPA.
- [ ] REST calls and authenticated queue WebSocket updates work over HTTPS/WSS through Render.
- [ ] Appointment booking, queue transitions, and status history are verified against the target database before real traffic.
- [ ] Backups, restore procedure, alerting, and rollback plan are configured for the selected tiers.

## Free-tier limits and costs

Free offerings and quotas change. Vercel, Render, and TiDB Cloud can impose usage, sleeping/compute, storage, connection, networking, and bandwidth limits; free service availability does not guarantee production uptime or durable recovery. Confirm current plan limits and pricing in each provider console before launch. Budget for Render always-on compute if sleep is unacceptable, TiDB capacity/backups/network features required by production, and any Vercel usage beyond included quotas. This repository does not provision or estimate provider-specific charges.

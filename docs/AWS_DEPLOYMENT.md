# MedQueue AWS deployment architecture

This document describes the production architecture and operator procedure. No AWS deployment is performed by this repository task.

## Architecture and network boundaries

- Local development uses the unchanged root `docker-compose.yml`: it starts Docker MySQL and publishes the development database/backend ports for local use.
- Production uses the separate root `docker-compose.prod.yml`: it starts only the backend and frontend/Nginx. Amazon RDS for MySQL is the production database; production Compose creates no MySQL service or database volume.
- The ALB accepts browser HTTPS on 443 and terminates TLS. It forwards HTTP to Nginx on the EC2 private IPv4 address and configured web port (80 by default). Nginx serves React, proxies `/api/` and `/ws/` to the backend, and uses the SPA `index.html` fallback.
- Nginx and the backend share only the private `application` Docker bridge. Nginx also joins the `ingress` bridge and is the only service with a published host port. The application bridge has normal bridge routing so the backend can reach the private RDS endpoint through EC2/VPC networking. The backend port 8080 is not published to the host.
- Do not expose EC2 port 80 to the internet. The EC2 application security group must allow the web target port **only from the ALB security group**. RDS must be in private subnets with inbound TCP 3306 allowed **only from the EC2 application security group**. Do not add public RDS access or public inbound rules for 3306/8080.
- The backend has no public/host port mapping. Its health endpoint is available internally to its health check and is not routed by Nginx.

## Forwarded protocol trust

The ALB is the only permitted network peer to the Nginx listener. The ALB supplies `X-Forwarded-Proto`; Nginx passes that value to Spring rather than replacing it with its local HTTP scheme. Production sets `SERVER_FORWARD_HEADERS_STRATEGY=framework` so Spring Boot interprets the forwarded headers. `/ws/` keeps the HTTP/1.1 `Upgrade` and `Connection` headers for WebSocket handshakes.

This trust is valid only while network controls prevent direct public access to the EC2 listener and the backend. Configure the ALB as the only ingress source in the EC2 security group; do not expose an alternate direct route that lets public clients supply forwarded headers to Nginx. Keep the backend attached only to the Compose application network.

## RDS connectivity and TLS

Set `DB_URL` to the private RDS JDBC endpoint and intended database. Production Compose has no Docker-hostname default and requires `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` explicitly. `application-prod.properties` configures MySQL Connector/J `sslMode=VERIFY_IDENTITY`; it requires both encryption and certificate-chain/endpoint verification. It also requires a PKCS12 truststore URL and password. There is no production fallback that disables TLS verification.

Obtain the current AWS RDS CA bundle using AWS's documented process, select/import the active RDS CA root certificates into a PKCS12 truststore, and place it on the EC2 host at the path configured by `RDS_TRUSTSTORE_PATH`. Compose mounts it read-only at `/run/certs/rds-truststore.p12`. Keep the truststore password in the external secret source and set `RDS_TRUSTSTORE_PASSWORD`. Rotate the truststore when AWS announces CA changes. Never use `useSSL=false`, `sslMode=DISABLED`, or an unverified trust-all certificate configuration.

RDS security group rule: inbound **TCP 3306 from the EC2 application security group only**. The database should have no public accessibility. The application EC2 instance must have VPC routing to the private RDS subnets and RDS DNS resolution.

## External configuration and secrets

Copy `docs/production.env.example` to `.env.production` on the EC2 host, fill it with real values, and keep it outside source control. Compose consumes values supplied in that file; **Compose does not retrieve values from AWS Secrets Manager or SSM Parameter Store**. Recommended production practice is to grant the EC2 instance role narrowly scoped access to Secrets Manager or SSM SecureString, retrieve values through an operator-controlled startup/deploy process, and provide them to Compose without printing them. Protect the environment file as a secret if that delivery approach is used. Do not put credentials, JWT secrets, or bootstrap passwords in source control or image layers.

Required variables:

- `DB_URL`: private RDS endpoint JDBC URL, for example `jdbc:mysql://<private-rds-endpoint>:3306/medqueue?serverTimezone=UTC`.
- `DB_USERNAME`, `DB_PASSWORD`: least-privilege application database account credentials.
- `RDS_TRUSTSTORE_PATH`, `RDS_TRUSTSTORE_PASSWORD`: absolute EC2 host path to the PKCS12 RDS CA truststore and its password.
- `JWT_SECRET`: Base64 text decoding to at least 32 random bytes; `JWT_EXPIRATION_SECONDS` defaults to 3600.
- `CORS_ALLOWED_ORIGINS`: exact HTTPS browser origin, with no localhost default in production Compose.
- `MEDQUEUE_BACKEND_IMAGE`, `MEDQUEUE_FRONTEND_IMAGE`: repository references pinned to `@sha256:<64-hex-character-digest>`.
- `WEB_BIND_ADDRESS`: RFC1918 private IPv4 assigned to the EC2 interface and reachable by the ALB; `WEB_PORT` defaults to 80.
- `BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD` are optional for the initial administrator only. If either is set, both must be explicitly supplied; production rejects placeholder passwords and requires at least 20 characters. Remove both from the environment after bootstrap.

Authentication requests are limited per backend instance. The production starting limits and ALB/Nginx peer-key assumption are documented in [AUTH_RATE_LIMITING.md](AUTH_RATE_LIMITING.md); these are not distributed AWS-side limits.

The production stack explicitly activates Spring profile `prod`, disables Swagger/OpenAPI, exposes only Actuator health internally, and limits Actuator details. It does not use development CORS or database defaults.

## Build, publish, and pin release images

Build and push images in a controlled CI/release environment. The production frontend build must receive empty Vite URL values at **build time**; the Dockerfile fails a production build with non-empty values and scans the output bundle for localhost URLs. Runtime container environment variables cannot rewrite a Vite bundle.

Example command shape (run only as part of an authorized release; not executed here):

```sh
docker buildx build --platform linux/amd64 \
  --build-arg MEDQUEUE_PRODUCTION_BUILD=true \
  --build-arg VITE_API_URL= --build-arg VITE_WS_URL= \
  -t "$ECR_REGISTRY/medqueue-frontend:$RELEASE" --push medqueue-frontend
docker buildx build --platform linux/amd64 \
  -t "$ECR_REGISTRY/medqueue-backend:$RELEASE" --push medqueue-backend
```

After push, obtain each immutable manifest digest from the registry release output or your registry's image inspection command. Set Compose image variables to the full `repository@sha256:<64 hex>` value, not a mutable tag. Do not use `latest`. No registry or AWS command is run by the offline validation below.

## Pre-deployment validation and Compose commands

On the deployment host, fill `.env.production`, install the RDS truststore, then run the value-safe validation and Compose syntax checks:

```sh
python3 scripts/validate-production.py .env.production
docker compose --env-file .env.production -f docker-compose.prod.yml config --quiet
```

The validation script prints variable names and pass/fail results, never their values. It rejects missing/placeholder required values, non-digest image references, `latest`, non-RDS/default database hosts, and wildcard/public/loopback web bind addresses. `WEB_BIND_ADDRESS` should also be checked against the actual EC2 network interface and ALB target configuration; a local syntax check cannot prove AWS network state.

Only after a separately approved deployment, pull and start the production stack with:

```sh
docker compose --env-file .env.production -f docker-compose.prod.yml pull
docker compose --env-file .env.production -f docker-compose.prod.yml up -d
docker compose --env-file .env.production -f docker-compose.prod.yml ps
docker compose --env-file .env.production -f docker-compose.prod.yml logs --tail=200 backend frontend
```

Those operational commands are documentation only and are not executed during this task. Development remains `docker compose up --build` with the root `docker-compose.yml` and local Docker MySQL.

## Existing RDS schema

The backend uses Flyway migrations `V1__initial_schema.sql` and `V2__appointment_status_history.sql`; Hibernate is configured with `ddl-auto=validate`. Before connecting an existing RDS database, independently verify its schema and Flyway history and take a restorable snapshot. Do not baseline an unknown or mismatched schema. Any one-time baseline procedure must be reviewed against the exact target database and must never drop, recreate, truncate, or reset business tables/data. This document intentionally does not provide an executable baseline command.

## RDS backup and disaster recovery

The repository configures the application to use an externally managed RDS endpoint; it does **not** configure or prove that AWS automated backups, point-in-time recovery, KMS encryption, snapshot retention, deletion protection, or restore testing are enabled. These are AWS-side tasks. Before serving production traffic, review and apply the plan in [DISASTER_RECOVERY.md](DISASTER_RECOVERY.md), then record the applied settings and a successful restore-test result. Do not treat a Compose volume or an unverified RDS default as a tested backup.

## Operational notes

Production Compose retains restart policies, bounded JSON-file log rotation, health checks, and resource limits. Configure ALB target health checks against the Nginx web route. RDS backup controls and restore testing remain to be configured and verified in AWS; centralized log retention/alerts and a tested image rollback process are also separate operational work. Image rollback does not reverse database migrations.

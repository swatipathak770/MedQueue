# AWS EC2 deployment preparation

MedQueue has not been deployed to AWS. These steps prepare the current Docker setup for an EC2 deployment; verify the resulting URL and health check after deployment.

## Recommended layout

- Run the React/Nginx container behind an HTTPS Application Load Balancer (or a TLS reverse proxy).
- Keep the Spring Boot backend and MySQL private. The frontend Nginx routes `/api/*` and `/ws/*` to the backend over the Compose network.
- For production, use Amazon RDS for MySQL in private subnets instead of keeping the only database copy on the EC2 instance. Enable automated backups and test restores.
- Use ECR for backend and frontend images, and SSM Parameter Store or Secrets Manager for `DB_PASSWORD`, `JWT_SECRET`, and the initial administrator password.

## EC2 preparation

1. Create a VPC with a public load balancer and private application/database subnets. A single-instance proof of concept can put EC2 in a public subnet, but still restrict SSH to a trusted IP or use Systems Manager Session Manager.
2. Create a security group that permits inbound 443 (and 80 only for HTTPS redirect) from the internet. Do not allow public inbound MySQL 3306 or backend 8080. Allow the load balancer to reach the frontend target on port 80. If EC2 is directly exposed, permit only the chosen web ports.
3. Launch a current Amazon Linux or Ubuntu LTS EC2 instance with an IAM instance role that can pull the two ECR repositories and read the required secrets. Size storage for container images and logs.
4. Install Docker Engine and the Docker Compose plugin using the distribution's current official instructions. Configure Docker to start at boot and add the deploy user to the Docker group, or run the deployment under a controlled service account.
5. Create or select an RDS MySQL 8.0 database in private subnets. Its security group should allow 3306 only from the EC2 application security group. Create a least-privilege database account for MedQueue.

## Configuration and release

1. Build and push tagged backend and frontend images to ECR from CI. Do not use mutable `latest` tags for a production rollout.
2. Place `docker-compose.yml` and a production Compose override on EC2. Update image references to the ECR image tags. Remove public host port mappings for MySQL and backend in the production override; the frontend can reach both services through the internal Compose network.
3. Set `DB_URL` to the RDS endpoint, `DB_USERNAME`, `DB_PASSWORD`, a newly generated Base64-encoded `JWT_SECRET` of at least 32 decoded bytes, `JWT_EXPIRATION_SECONDS`, and `CORS_ALLOWED_ORIGINS` for the HTTPS application origin. Hibernate `ddl-auto` is fixed to `validate`; never enable `update` in deployment. The deployable/default Spring configuration requires the database URL, username, password, and JWT secret; do not set `SPRING_PROFILES_ACTIVE=dev` on EC2. Flyway applies pending versioned migrations at backend startup, then Hibernate validates the schema.
4. Disable the publicly permitted development API documentation on the deployed application with `SPRINGDOC_API_DOCS_ENABLED=false` and `SPRINGDOC_SWAGGER_UI_ENABLED=false`, or protect those routes at the deployment boundary.
5. Set `BOOTSTRAP_ADMIN_EMAIL` and a unique `BOOTSTRAP_ADMIN_PASSWORD` of at least 12 characters for the first launch. The app creates the administrator only if that email is unused. Remove both variables after the admin is created; a conflicting non-admin account causes startup to fail rather than being elevated.
6. Set the frontend Vite URL build args empty so browser traffic stays same-origin through Nginx. Rebuild the frontend image whenever those build-time values or frontend code change.
7. Pull the pinned image tags and run `docker compose up -d`. Review `docker compose ps` and `docker compose logs --tail=200 backend frontend mysql`.

## Health, logs, and restart behavior

- The backend health URL inside the application is `/actuator/health` (port 8080). The Compose backend does not publish this port to the internet in the recommended production layout.
- Configure the load balancer health check to use the frontend health route or add a private backend health route in the deployment override. Confirm the backend reports `UP` before sending patient traffic.
- Compose services use `restart: unless-stopped`. Send logs to CloudWatch through the Docker logging driver or the EC2 unified CloudWatch agent, and alert on unhealthy targets, repeated restarts, database storage, and 5xx rates.
- Keep a tested rollback image tag and a database backup before schema changes. A rollback of application images does not reverse database migrations.

## Database migrations and existing RDS databases

The repository migrations are `V1__initial_schema.sql` (core tables) and `V2__appointment_status_history.sql` (audit table). A fresh empty RDS database applies V1 then V2 automatically when the backend starts. Run only one application instance during initial migration; review migration logs and confirm Hibernate validation succeeds before scaling the service. The application must use a migration-capable database account at startup; scope/rotate credentials according to your deployment process.

For an existing RDS database, do not let Flyway infer or alter its history. Before rollout, take and verify a restorable snapshot, compare `SHOW CREATE TABLE` for all seven application tables with V1/V2 and inspect whether `flyway_schema_history` already exists. If it exactly matches both versions and has no history table, set the exact existing MedQueue database URL before enabling the one-time baseline. For a Windows PowerShell deployment/operator shell, use the RDS endpoint and the MedQueue database name explicitly (do not substitute `medqueue_test` or another database):

```powershell
$env:DB_URL = "jdbc:mysql://<existing-rds-endpoint>:3306/medqueue?useSSL=false&serverTimezone=Asia/Kolkata&allowPublicKeyRetrieval=true"
$env:SPRING_FLYWAY_BASELINE_ON_MIGRATE = "true"
$env:SPRING_FLYWAY_BASELINE_VERSION = "2"
```

Before starting the application, verify that `DB_URL` points to the intended existing `medqueue` database. Do not use this procedure against `medqueue_test` or an unrelated database. After the successful one-time baseline and startup validation, stop the application and immediately remove the temporary flags with `Remove-Item Env:SPRING_FLYWAY_BASELINE_ON_MIGRATE` and `Remove-Item Env:SPRING_FLYWAY_BASELINE_VERSION`. If the schema matches only V1, baseline at version 1 so the normal migration applies V2. Verify the history table records the expected baseline/migration versions and that Hibernate validation passes. Never baseline an unverified or partially matching schema; stop and prepare a reviewed migration to reconcile differences. This procedure only adds Flyway history metadata: it must never drop, recreate, truncate, reset, or modify business tables or their data.

Do not keep `SPRING_FLYWAY_BASELINE_ON_MIGRATE` enabled in production. Future schema changes must be added as reviewed Flyway migrations; Hibernate remains `validate` and must not perform DDL updates. The retained `medqueue-backend/docs/appointment-status-history.sql` is historical reference only; V2 is the official migration.

## Validation checklist after deployment

- Register a patient, sign in, and verify a protected request returns successfully.
- Provision the first admin and create a department, doctor, and slot.
- Book an appointment, call and complete it from the doctor account, and confirm the patient queue view changes over STOMP.
- Verify `https://<domain>/actuator/health` only if you intentionally publish that health route; otherwise check it from a private operator network.
- Confirm the browser uses HTTPS and the WebSocket upgrades to WSS through the load balancer.
- Verify RDS backups, CloudWatch logs, and the rollback procedure.

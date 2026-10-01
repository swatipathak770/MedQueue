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
3. Set `DB_URL` to the RDS endpoint, `DB_USERNAME`, `DB_PASSWORD`, a newly generated Base64-encoded `JWT_SECRET` of at least 32 decoded bytes, `JWT_EXPIRATION_SECONDS`, `CORS_ALLOWED_ORIGINS` for the HTTPS application origin, and `JPA_DDL_AUTO=update` for the first schema creation. After the schema is established, use `validate` and apply future changes with a migration tool before adopting that stricter setting.
4. Set `BOOTSTRAP_ADMIN_EMAIL` and a unique `BOOTSTRAP_ADMIN_PASSWORD` of at least 12 characters for the first launch. The app creates the administrator only if that email is unused. Remove both variables after the admin is created; a conflicting non-admin account causes startup to fail rather than being elevated.
5. Set the frontend Vite URL build args empty so browser traffic stays same-origin through Nginx. Rebuild the frontend image whenever those build-time values or frontend code change.
6. Pull the pinned image tags and run `docker compose up -d`. Review `docker compose ps` and `docker compose logs --tail=200 backend frontend mysql`.

## Health, logs, and restart behavior

- The backend health URL inside the application is `/actuator/health` (port 8080). The Compose backend does not publish this port to the internet in the recommended production layout.
- Configure the load balancer health check to use the frontend health route or add a private backend health route in the deployment override. Confirm the backend reports `UP` before sending patient traffic.
- Compose services use `restart: unless-stopped`. Send logs to CloudWatch through the Docker logging driver or the EC2 unified CloudWatch agent, and alert on unhealthy targets, repeated restarts, database storage, and 5xx rates.
- Keep a tested rollback image tag and a database backup before schema changes. A rollback of application images does not reverse database migrations.

## Validation checklist after deployment

- Register a patient, sign in, and verify a protected request returns successfully.
- Provision the first admin and create a department, doctor, and slot.
- Book an appointment, call and complete it from the doctor account, and confirm the patient queue view changes over STOMP.
- Verify `https://<domain>/actuator/health` only if you intentionally publish that health route; otherwise check it from a private operator network.
- Confirm the browser uses HTTPS and the WebSocket upgrades to WSS through the load balancer.
- Verify RDS backups, CloudWatch logs, and the rollback procedure.

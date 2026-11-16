# MedQueue monitoring and operations plan

## Scope and current state

This document describes the repository configuration and a proposed AWS operations plan. It does not create or verify AWS resources. CloudWatch dashboards, alarms, log groups, log exports, notification destinations, and CloudWatch Agent configuration are **not configured or active based on this repository**.

| Area | Implemented in the repository | Not currently configured here |
|---|---|---|
| Backend health | Spring Boot Actuator dependency; production exposes only `health`; details and components are hidden. Docker polls `/actuator/health` internally every 30 seconds. | No public Nginx route to the backend health endpoint; no CloudWatch health alarm or external synthetic check. |
| Frontend health | Docker polls Nginx `/` every 30 seconds. | No monitoring of the container health state outside Docker. |
| Container lifecycle | `unless-stopped`, resource limits and healthchecks in production Compose. | No Docker health/restart metric exporter or alarm. |
| Logs | Backend logs to the normal application/container output. Compose uses Docker `json-file` rotation, 10 MB per file and 5 files per service. Nginx uses its standard access/error log behavior. | No CloudWatch log driver, agent, log groups, retention policy, metric filters, or centralized search configured. |
| AWS infrastructure | Deployment documentation describes EC2, ALB and RDS architecture. | No evidence here that AWS metrics, alarms, dashboards, SNS, RDS log exports, or CloudWatch Agent are applied. |

Relevant files: `docker-compose.prod.yml`, `medqueue-backend/Dockerfile`, `medqueue-backend/src/main/resources/application-prod.properties`, `medqueue-backend/pom.xml`, `medqueue-frontend/nginx.conf`, and `docs/AWS_DEPLOYMENT.md`.

## Application health and exposure

Production properties and Compose explicitly set `management.endpoints.web.exposure.include=health`, `management.endpoint.health.show-details=never`, and `management.endpoint.health.show-components=never`. `SecurityConfig` permits `/actuator/health`; the production Compose topology does not publish backend port 8080 and Nginx has no Actuator proxy location. The backend healthcheck calls `http://127.0.0.1:8080/actuator/health`; the frontend healthcheck calls `/` on the local Nginx process. No additional Actuator endpoint should be exposed for monitoring.

Actuator health can include a datasource health contributor when the datasource is configured. The current health response hides component names and details. A 30-second local probe is modest for a single backend, but it is not proof of zero database activity: verify behavior and connection-pool impact under the actual production profile before scaling probe frequency or replica count. Keep liveness/readiness decisions distinct if they are introduced later; an external health check should not create a new public path to Actuator.

## Logging security and retention

`ApiExceptionHandler` returns generic text for unexpected failures and logs the request method and URI with the exception stack (`ApiExceptionHandler.java`). This avoids logging the request body or Authorization header in that handler. Review future log statements and exception messages before release; do not add tokens, passwords, cookies, patient names, contact details, appointment notes, or request/response bodies to logs. Treat query strings as sensitive because URLs can contain identifiers or tokens. Nginx currently has no custom `log_format` or explicit access/error log directives in `medqueue-frontend/nginx.conf`; inspect the effective image defaults before relying on exact fields, and ensure Authorization and Cookie headers are not added to its format.

Docker local log rotation is bounded to roughly 50 MB per service (5 files × 10 MB, plus overhead), but it is not durable central retention and can lose older evidence. If CloudWatch collection is enabled, define log groups explicitly, restrict read access, encrypt where required by the organization's policy, and set retention rather than leaving logs indefinitely. A practical MVP starting point is 30 days for operational application/proxy logs, then adjust based on incident-response needs, storage cost, and any applicable privacy or retention policy. Keep security/audit records separate if a longer approved retention period is required. Do not assume RDS logs are exported; configure only the specific MySQL logs needed and review their privacy/cost impact.

## Recommended CloudWatch collection

These are recommendations only. Select the deployment account, region, naming, permissions, and cost controls before applying them.

| Component | Recommended signals | Collection method / notes |
|---|---|---|
| EC2 | `CPUUtilization`, network in/out, status checks. Memory used, disk used/free, filesystem/inode utilization. | Basic CPU/network/status metrics are available as EC2 metrics. Install/configure the CloudWatch Agent for memory and filesystem metrics; the agent is not present in this project. Consider disk queue/IO only if the selected storage makes it useful. |
| RDS MySQL | `CPUUtilization`, `FreeStorageSpace`, `DatabaseConnections`, `FreeableMemory`, read/write latency and IOPS, `DiskQueueDepth`; RDS events and backup status. | RDS publishes standard CloudWatch metrics. Configure event notifications and any required MySQL log exports separately. Database connection count should be compared with the instance's actual connection limit and normal pool usage. |
| ALB | `HealthyHostCount`, `UnHealthyHostCount`, `HTTPCode_ELB_5XX_Count`, `HTTPCode_Target_5XX_Count`, `TargetResponseTime`, `RequestCount`, and `RejectedConnectionCount`. | Use `AWS/ApplicationELB`. Break down by load balancer/target group and account for low request volume. Enable ALB access logs separately if request-level diagnosis is needed; these are not configured by this repository. |
| Backend / Nginx containers | Docker health state, restart count, CPU/memory, stdout/stderr logs, Nginx 4xx/5xx and error messages. | Current Compose healthchecks and restart policies do not publish these as CloudWatch metrics. Use a reviewed CloudWatch Agent/container metrics setup or a small custom metric collector; tail Docker JSON log files or use an explicitly configured log driver. Neither option is configured here. Avoid collecting container environment values. |
| MedQueue behavior | Queue/appointment operation rates and failures, if operationally needed. | No application metrics endpoint or custom business metrics are configured. Add only privacy-safe aggregate metrics in a separately reviewed change; do not expose more Actuator endpoints publicly. |

## Suggested alarms

Treat the values below as initial tuning candidates, not production guarantees. Establish a normal baseline and adjust periods, evaluation counts, and thresholds for the actual instance class, traffic, pool size, and operating hours. Use missing-data behavior deliberately so a telemetry outage is not mistaken for healthy service.

| Alarm candidate | Initial trigger to tune | Investigation |
|---|---|---|
| EC2 sustained CPU | Average above roughly 80% for 10–15 minutes. | Check ALB request rate, container CPU/memory, recent release, and host saturation. Scale or roll back only after confirming cause and capacity. |
| EC2 disk low | Free space below roughly 15% (and alert separately on inode exhaustion). | Identify the filesystem and largest safe-to-inspect consumers. Check Docker log growth and image/cache growth. Do not delete data or volumes during triage without an approved recovery plan. Requires agent metrics. |
| RDS storage low | Free space below a chosen safety buffer (for example 15–20% of provisioned capacity) or a forecasted time-to-full threshold. | Review growth, retention, backup/log usage, and storage autoscaling state. Preserve data; do not delete records as an emergency response. |
| RDS connections high | Sustained use above a tuned share of `max_connections` (for example 80%), with evaluation periods. | Compare with normal and maximum Hikari pool sizes and active app replicas; inspect connection leaks, retries, and traffic. Do not expose connection strings in tickets/logs. |
| ALB target unhealthy | Any sustained unhealthy target, or healthy targets below the intended minimum. For a single target, one unhealthy target means no healthy backend path. | Check target group health path/port, EC2 security-group path from ALB, Nginx container health, and recent changes. |
| Elevated ALB 5xx | Target or load-balancer 5xx rate above a baseline (e.g. 5%) with a minimum request count over several periods. | Separate ELB-generated and target-generated 5xx; correlate timestamps with Nginx and backend logs and recent deployments. Low-volume periods can make percentage alarms noisy. |
| Response latency | Target response latency above a user-visible objective for multiple periods, using a suitable percentile and enough requests. | Check RDS latency/connections, backend resource pressure, and ALB request mix. Choose an objective from observed baseline and product expectations. |
| Backend failure/restarts | Sustained unhealthy state or repeated restarts (e.g. more than 3 within 10 minutes). | Requires a container state/restart metric source; Compose alone does not send this to CloudWatch. Correlate restart times with startup logs, OOM/resource events, DB availability, and releases. |
| Nginx errors | Elevated 5xx/error log count or repeated upstream connection failures. | Requires Nginx logs in CloudWatch and metric filters or log-based metrics. Check backend reachability and health, then inspect a short, privacy-safe time window. |

Route alarms to a monitored SNS email subscription or the team's approved incident channel. Separate actionable service-impact alarms from informational warnings; verify subscriptions and escalation coverage during a controlled test. Alarm notification plumbing is not present in this repository.

## Troubleshooting procedures

1. **Acknowledge and scope.** Record alarm name, UTC start time, affected components, user impact, and current release. Check for concurrent alarms before changing anything.
2. **Backend unhealthy or restarting.** Inspect container health and restart timestamps on the host; review a bounded window of backend stdout/stderr. Check recent release/config changes, memory/CPU pressure, and RDS reachability. Keep credentials and environment output out of terminal captures and incident notes.
3. **ALB unhealthy or 5xx.** Compare ELB-generated with target-generated errors, check target health reason and Nginx health, then correlate proxy and backend timestamps. Confirm security-group and listener routing from the approved AWS console/runbook.
4. **RDS pressure.** Check CPU, free storage, connections, memory, latency, and RDS events together. Compare active connections with configured application pool limits. Escalate capacity or query issues; do not make ad hoc destructive schema/data changes.
5. **Host resource pressure.** Use agent metrics to identify the affected filesystem/resource and inspect Docker/service consumption. Preserve logs and volumes; avoid broad cleanup commands during an incident.
6. **Suspected data loss/corruption.** Follow `docs/DISASTER_RECOVERY.md`; restore to a separate candidate database and validate it before an approved traffic switch. Do not restore over the original as the first action.
7. **Close out.** Capture timeline, user impact, recovery actions, evidence location, and follow-up owners. Redact patient identifiers and credentials from all shared material.

## Incident response checklist

- [ ] Assign an incident lead and record timestamps in UTC.
- [ ] Identify severity, affected user flows, and whether patient data confidentiality/integrity may be involved.
- [ ] Preserve relevant CloudWatch/host logs and alarm history; restrict evidence access.
- [ ] Avoid copying secrets, Authorization headers, JWTs, cookies, or unnecessary patient data into logs, screenshots, tickets, or chat.
- [ ] Apply only reversible, approved mitigations; record each action and its owner.
- [ ] For database recovery, use the separate-instance procedure in `docs/DISASTER_RECOVERY.md` and retain the original.
- [ ] Verify user-facing recovery, queue/appointment flows, and monitoring signal recovery.
- [ ] Document cause, timeline, data impact, and preventive actions; review log retention and access after the incident.

## Before calling monitoring active

- [ ] Configure CloudWatch Agent and verify memory/disk metrics without collecting secrets or environment variables.
- [ ] Configure narrowly scoped log groups, explicit retention, encryption/access policy, and log shipping for backend and Nginx.
- [ ] Enable only approved RDS log exports and ALB access logs; validate sensitive-field handling.
- [ ] Create dashboards and alarms for the selected EC2, RDS, ALB, container, and log metrics.
- [ ] Configure SNS/incident delivery and test notification receipt with the on-call owner.
- [ ] Exercise alarm and recovery procedures, record observed thresholds and time-to-detect, and tune noisy alarms.
- [ ] Record which controls are applied in AWS; this document alone is not evidence they are active.

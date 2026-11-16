# MedQueue RDS backup and disaster recovery plan

> **Status: planning document only.** No AWS or RDS settings have been applied or verified by this repository. The targets below are proposed initial objectives, not measured service guarantees.

## Repository facts and scope

- Production Compose starts the backend and Nginx containers; it has no MySQL service or database volume. Production data is expected to reside in Amazon RDS.
- The backend applies Flyway migrations at startup and uses Hibernate `ddl-auto=validate`. The checked-in migrations are `V1__initial_schema.sql` (users, departments, doctors, slots, appointments, and queue states) and `V2__appointment_status_history.sql` (appointment status audit history).
- Neither `docker-compose.prod.yml` nor the environment example configures RDS automated backups, PITR, KMS keys, snapshot retention, deletion protection, backup-vault access, or restore procedures. These must be configured and tested in AWS separately.
- RDS backups protect the MySQL database. They do not back up application images, `.env.production`, RDS CA truststores, KMS keys/policies, or the EC2/ALB configuration. Protect and recover those separately without storing secret values in this repository.

## Proposed recovery objectives

| Objective | Proposed initial target | Meaning and limits |
|---|---:|---|
| RPO | 15 minutes | Recovery Point Objective: at most 15 minutes of committed changes may be lost. This assumes automated backups and PITR are enabled and usable. It is a target, not a guarantee. |
| RTO | 8 hours | Recovery Time Objective: restore service within 8 hours for a small, single-region MVP using an operator-driven restore, data validation, and application cutover. It is not measured and depends on RDS restore duration, AWS capacity, the incident, and operator availability. |

Measure actual RPO/RTO during restore exercises. Revise these targets if the database grows, the application gains a support rota, or the service requires tighter availability.

## Recommended RDS backup controls

These are proposed settings; none are confirmed as applied.

1. **Automated backups and PITR:** enable automated backups with an initial **7-day retention** and point-in-time recovery. Confirm the backup window is outside the expected busiest operating period. Consider 14–35 days if incident discovery may take longer or the recovery policy requires it. Confirm the resulting recovery window in the RDS console/API after configuration.
2. **Encryption:** encrypt the RDS instance storage and backups with AWS KMS. Prefer a customer-managed KMS key when controlled access, auditing, or cross-account recovery is needed. Keep key administrators separate from routine application operators, enable key rotation where appropriate, and protect the key policy from accidental removal. Encrypted snapshots require continued access to their KMS key; loss or disabling of the key can make recovery impossible. If an existing instance is unencrypted, plan an encrypted snapshot-copy and restore migration; do not assume encryption can be enabled in place.
3. **Pre-change snapshots:** take and verify a manual snapshot immediately before major releases, Flyway schema changes, bulk data corrections, or other high-risk operations. Tag each snapshot with database identifier, creation time in UTC, release/migration identifier, purpose, and owner. Wait for snapshot status to become available before proceeding with the change.
4. **Manual snapshot retention:** retain release/change snapshots for an initial **30 days**. Keep a known-good pre-change snapshot until the migration and post-change checks are accepted. Review any longer retention requirement separately. Delete expired snapshots only through an approved cleanup process; manual snapshots can remain after an RDS instance is deleted and continue to incur storage cost.
5. **Deletion protection:** enable RDS deletion protection. Restrict `DeleteDBInstance`, snapshot deletion, KMS key disable/deletion, and backup-policy changes to a separate, tightly controlled administrator role with MFA and change approval. Deletion protection is not a backup and does not prevent data corruption or a privileged account from deleting snapshots.
6. **Backup access:** keep application credentials and the EC2 application role out of backup administration. Give the application role only the existing runtime needs (for example, image pulls and external secret retrieval if configured). Use a separate backup/operator role for describing backup state, creating/copying snapshots, and restoring to a new instance. Scope RDS actions to the intended resources and KMS actions to the backup key. Keep deletion and key-destruction authority separate from routine restore permissions. Record and alert on snapshot deletion, KMS policy/key changes, retention changes, and backup failures.
7. **Isolation:** for stronger protection against account compromise or regional failure, plan a separately administered backup account/vault and/or cross-region snapshot copies. These add KMS grants, operational complexity, and cost; they are not configured by this project. A second copy in the same account and region does not protect against every account- or region-level incident.
8. **Availability is separate:** Multi-AZ can reduce outage time for some infrastructure failures, but it is not a substitute for backups or protection from accidental writes/deletes. Choose it separately based on availability needs and cost.

Review current AWS pricing before enabling longer retention, cross-region copies, a second account, or a continuously provisioned recovery instance. Costs can include retained backup/snapshot storage, copied snapshots, KMS use, data transfer, and temporary restored database instances. Set ownership and budget alerts for those resources.

## Restore runbook

Restore into a **separate RDS instance first**. Do not restore over, delete, or repoint production to a candidate before it is validated.

1. **Declare and bound the incident.** Record the incident owner, UTC detection time, last known-good time, affected database, suspected cause, and whether unauthorized access is suspected. Stop or restrict new booking/queue mutations through the approved application/traffic controls to avoid writes diverging between databases. Preserve logs and the original RDS instance.
2. **Choose the recovery point.** Use PITR to a time immediately before corruption or an unwanted migration when available. Otherwise identify the most recent verified pre-change/manual snapshot. Record the selected recovery point and the expected data-loss window; obtain approval for that point before cutover.
3. **Restore a separate candidate.** Restore PITR or the snapshot as a new RDS instance in private subnets. Use the intended MySQL engine version, parameter/subnet groups, security-group boundaries, storage settings, and KMS key. Keep public accessibility disabled. Apply deletion protection after the instance is available. Use a recovery-specific identifier and retain the original production instance unchanged.
4. **Check backup and schema state before application startup.** Confirm the restored instance is available, encrypted as intended, and reachable only on the private application path. Verify `flyway_schema_history` records the expected successful migrations (`V1` and `V2` for the current repository) and compare the schema with the checked-in migration definitions. Confirm critical foreign-key relationships and table/index presence. Stop if history is absent, failed, unexpected, or inconsistent; do not baseline or let startup migrations conceal an unknown schema. Because the production backend applies Flyway on startup, any pending migration must be reviewed and approved before connecting the backend to this candidate.
5. **Validate recovered data on the isolated candidate.** Check aggregate counts and referential consistency for users/patients, doctors/departments, appointments, queue states, and appointment status history. Confirm records around the recovery point and representative queue/status relationships without copying patient-identifying data into logs or incident notes. Compare expected totals and audit history with application logs/metrics where available.
6. **Run restricted application checks.** Start a controlled validation backend using the same approved immutable application image and production profile, with its `DB_URL` directed only to the restored candidate and secrets supplied through the normal protected mechanism. Keep it inaccessible to normal patient traffic. Verify TLS certificate/hostname validation, health, authentication, authorized patient/doctor/admin access, appointment history, queue snapshots, and WebSocket updates. Use dedicated recovery test accounts and avoid mutations unless they are confined to the candidate and recorded for later cleanup.
7. **Approve cutover.** Document data checks, the accepted recovery point/RPO, application test results, and decision owner. Freeze writes briefly. Update the protected production database endpoint setting to the candidate, roll the backend in a controlled manner, and verify health and critical read/write flows before allowing patient traffic. Do not run old and new databases as simultaneous writers. If the original database may be compromised, rotate affected credentials/secrets through the incident process before restoring traffic.
8. **Keep the original intact.** Do not delete or overwrite the original database during recovery. Keep it isolated from application writes and retain it, with its snapshots/log evidence, until service and data recovery are confirmed and the incident owner approves cleanup under the retention policy. If rollback is needed, first assess writes made after cutover; blindly pointing back can discard valid changes or reintroduce corruption.
9. **Close the incident.** Record the actual recovery point, time to service restoration, data discrepancies, snapshot/KMS identifiers, approvals, and follow-up actions. Update the measured RPO/RTO and improve the runbook based on the exercise.

## Restore-test checklist

Run a restore exercise before first production use and at least quarterly thereafter, and after material changes to backup policy, KMS keys, network boundaries, or migration practice.

- [ ] Confirm the latest automated backup and PITR window, plus the latest required manual snapshot.
- [ ] Restore into a separate private, encrypted candidate instance; leave production untouched.
- [ ] Confirm deletion protection, KMS access, and restricted security-group connectivity.
- [ ] Verify successful Flyway history for the expected versions and schema consistency before starting the application.
- [ ] Validate patient/user, doctor/department, appointment, queue, and audit-history consistency using non-sensitive checks.
- [ ] Run controlled application health, authentication, API, and WebSocket checks against the candidate only.
- [ ] Measure elapsed restore and validation time, and estimate the achieved RPO from the chosen recovery point.
- [ ] Confirm the candidate can be removed only through the approved cleanup/retention process; keep the original production database.
- [ ] Record evidence and owners without storing credentials, tokens, or patient data in the report.

## AWS items to configure and verify manually

- RDS automated backups, retention duration, and PITR window.
- RDS storage encryption and KMS key policy/rotation/protection.
- Manual snapshot process, tags, retention, deletion approval, and any cross-account/region copy policy.
- RDS deletion protection and least-privilege backup/restore IAM roles.
- Private subnet placement, non-public accessibility, and application-security-group-only access.
- Restore exercise, measured recovery times, and the operational owner/on-call procedure.

Until these settings are applied and a restore exercise succeeds, MedQueue must be treated as having an unverified backup and disaster-recovery posture. This document itself creates no AWS resources and is not evidence that backups are enabled.

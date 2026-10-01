# MedQueue

MedQueue is a Smart OPD appointment and queue management system. Patients book a scheduled visit or join a walk-in queue, doctors run their daily queue, and administrators manage departments, doctors, and weekly slot templates.

## Project layout

```text
MedQueue/
├── medqueue-backend/       Spring Boot 3.5.16 / Java 17 API
├── medqueue-frontend/      React 19 / Vite / Redux Toolkit / Tailwind SPA
├── docs/AWS_DEPLOYMENT.md  EC2 and RDS deployment preparation
├── docker-compose.yml      MySQL + backend + frontend
└── .env.example            Compose environment template
```

## Architecture

The backend uses controller → service → repository layers, DTOs at API boundaries, Spring Security, BCrypt, signed JWT bearer tokens, Spring Data JPA, and MySQL. Patient registration always creates a `PATIENT`. Administrators can create doctor accounts with the `DOCTOR` role. An optional environment-based bootstrap creates the first administrator without a public admin-registration endpoint.

The React application has role-gated patient, doctor, and admin workspaces. Axios attaches the bearer token to API calls. Patient and doctor views connect to the STOMP endpoint at `/ws` and subscribe to `/topic/queue/{doctorId}`. Queue snapshots contain token numbers, statuses, positions, wait estimates, and availability state; they do not include patient names or email addresses. Updates publish after the database transaction commits.

The STOMP broker is Spring’s in-memory simple broker, suitable for one backend instance. A multi-instance deployment needs a broker relay or shared message broker and cross-instance session/message handling.

## Data model

- `users`: identity, unique email, BCrypt password hash, role, phone, and creation time.
- `departments`: unique department names.
- `doctors`: one-to-one user account, department, specialization, average consultation duration, and availability.
- `slots`: recurring doctor/day templates with start/end and capacity.
- `appointments`: patient, doctor, date, optional slot (null for walk-in), token, status, and timestamps.
- `appointment_status_history`: append-only initial/status transition entries, actor key, and timestamp; each row references its appointment.
- `queue_states`: per-doctor/per-day open state and latest called token.

Appointment tokens are unique per doctor and appointment date. Booking locks the doctor row while it checks capacity and assigns the next token. Booking and queue mutations use READ COMMITTED so the transaction sees changes committed while it waited for that row lock; an integration test starts two actual database transactions together and verifies only one call-next succeeds. Queue transitions allow only one CALLED/IN_PROGRESS appointment at a time and enforce doctor ownership. The STOMP channel requires JWT authentication, restricts doctors to their own queue, and blocks clients from publishing queue messages. The composite index `idx_appointments_doctor_date_status` supports queue queries. See [the EXPLAIN SQL](medqueue-backend/docs/appointment-index-explain.sql); the integration test verifies MySQL can use that index when selected. On the current small local database, MySQL’s unforced optimizer plan may choose the unique doctor/date/token index instead, so no response-time improvement is claimed.

Every booking records an initial `WAITING` audit entry; supported doctor queue transitions append their old/new statuses inside the same transaction. `changed_by` stores `USER:<users.id>` (or `SYSTEM` for future system initiated transitions) to avoid copying email addresses into audit rows. Authenticated patients can read their own appointment audit, doctors can read audit for their appointments, and admins can read any appointment audit through `GET /api/appointments/{id}/status-history`. For an existing MySQL schema, apply [the audit table SQL](medqueue-backend/docs/appointment-status-history.sql) once before running the updated backend; it creates the table, appointment foreign key, and chronological index.

## API summary

| Method | Endpoint | Access | Purpose |
|---|---|---|---|
| POST | `/api/auth/register` | Public | Register a patient |
| POST | `/api/auth/login` | Public | Authenticate and issue a JWT |
| GET | `/api/departments` | Public | Browse departments |
| GET | `/api/doctors?department=...` | Public | Browse/search doctors |
| GET | `/api/doctors/{id}/slots?date=YYYY-MM-DD` | Public | View weekday slots and remaining capacity |
| POST | `/api/appointments` | PATIENT | Book a slot or join walk-in queue |
| GET | `/api/appointments/me` | PATIENT | View own appointment history |
| GET | `/api/appointments/{id}/status-history` | Appointment patient, assigned doctor, or ADMIN | Read chronological appointment status audit |
| GET | `/api/doctor/queue` | DOCTOR | View own current-day queue |
| POST | `/api/doctor/queue/next` | DOCTOR | Call the next waiting patient |
| POST | `/api/doctor/queue/{id}/complete` | DOCTOR | Complete own called visit |
| POST | `/api/doctor/queue/{id}/skip` | DOCTOR | Skip own waiting/called visit |
| PUT | `/api/doctor/queue/availability` | DOCTOR | Update availability |
| POST | `/api/doctor/queue/close` | DOCTOR | Close today’s queue |
| CRUD | `/api/admin/departments` | ADMIN | Manage departments |
| CRUD | `/api/admin/doctors` | ADMIN | Manage doctor accounts/profiles |
| CRUD | `/api/admin/slots` | ADMIN | Manage weekly slot templates |
| GET | `/api/admin/queues` | ADMIN | View today’s queue summary for every doctor |
| GET | `/api/admin/analytics` | ADMIN | View today’s per-doctor metrics |

Protected endpoints accept `Authorization: Bearer <JWT>`. Role and ownership rules are checked by Spring Security and service logic. Validation and API errors are returned as JSON.

## Local development

### Backend using the existing MySQL installation

The deployable/default configuration requires explicit `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and `JWT_SECRET` environment variables. For local development only, activate the `dev` profile to use the existing MySQL installation defaults (`localhost:3306/medqueue`, `root`/`root`) and a fixed development-only JWT key. Never activate this profile in a deployed environment. The key is Base64 text that decodes to at least 32 bytes. From `medqueue-backend`, run:

```powershell
$env:SPRING_PROFILES_ACTIVE = "dev"
mvn clean test
mvn spring-boot:run
```

The backend listens on `http://localhost:8080`. In IntelliJ, set the active Spring profile to `dev` in the run configuration. Outside the dev profile, provide the four required environment variables explicitly. `JWT_SECRET` must be Base64 text decoding to at least 32 bytes. Swagger UI is at [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html), OpenAPI JSON at `/v3/api-docs`, and health at [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health). Swagger UI and OpenAPI are public in the current security configuration for development. For production, disable them with `SPRINGDOC_SWAGGER_UI_ENABLED=false` and `SPRINGDOC_API_DOCS_ENABLED=false`, or protect them at the deployment boundary. CORS origins are restricted by `CORS_ALLOWED_ORIGINS`; configure it to the deployed frontend origin. The first admin can be created by setting `BOOTSTRAP_ADMIN_EMAIL` and a unique `BOOTSTRAP_ADMIN_PASSWORD` (minimum 12 characters) before startup. Remove the bootstrap variables after the account is created.

### Frontend development server

From `medqueue-frontend`, run:

```powershell
npm install
npm run dev
npm test
npm run build
```

Vite runs on `http://localhost:5173` and talks to the backend on port 8080. No frontend environment file is required for local development; `VITE_API_URL` and `VITE_WS_URL` are optional overrides. The frontend test suite covers live queue lookup and active-visit selection.

## Docker Compose

From the repository root, copy `.env.example` to `.env` and replace every placeholder password/secret. These sample values are for local development only. Then run:

```powershell
docker compose up --build
```

Open the React app at `http://localhost`, the backend at `http://localhost:8080`, Swagger at `http://localhost:8080/swagger-ui.html`, and health at `http://localhost:8080/actuator/health`. The Compose MySQL port maps to host port 3307 to avoid colliding with a local MySQL server on 3306. MySQL data persists in the `medqueue-mysql` named volume. To stop the services, use `docker compose down`; this keeps the database volume. To erase local database data, explicitly remove the volume with `docker compose down -v`.

Compose environment variables are documented in [.env.example](.env.example). It contains placeholders only: replace every password and secret before starting Compose. `DB_USERNAME`, `DB_PASSWORD`, `MYSQL_ROOT_PASSWORD`, and `JWT_SECRET` are mandatory for Compose; the backend's default/deployable configuration also requires `DB_URL`. `JWT_SECRET` must be newly generated Base64 text that decodes to at least 32 bytes. Core optional values include `JWT_EXPIRATION_SECONDS`, `CORS_ALLOWED_ORIGINS`, `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD`, and `JPA_DDL_AUTO`. The browser-facing Vite values are build-time variables; leave them blank when using the included same-origin Nginx proxy.

## Testing and verification

Backend checks:

```powershell
cd medqueue-backend
mvn clean test
```

The suite covers application startup on MySQL, patient register/login/JWT/RBAC, password hashing and duplicate registration, appointment token allocation, serialized queue advancement, and the appointment composite index plan. Frontend checks run with `npm test` and `npm run build` from `medqueue-frontend`.

The test classpath activates the local-only `dev` profile so `mvn test` works with the existing local MySQL defaults. Security integration tests exercise valid, malformed, expired, and incorrectly signed JWTs; the PATIENT/DOCTOR/ADMIN REST role matrix; patient history isolation; doctor queue ownership; admin CRUD/analytics/live-queue access; and unauthenticated 401 responses.

No AWS deployment has been performed. See [AWS deployment preparation](docs/AWS_DEPLOYMENT.md) for EC2, RDS, network, secret, image, health-check, and rollback guidance.

## Current limitations and next improvements

- Appointment history currently returns all records for the patient without pagination.
- The simple in-memory STOMP broker supports one backend instance; multi-instance deployments need shared broker infrastructure.
- Hibernate schema update is retained for local convenience; use versioned migrations before production and switch `JPA_DDL_AUTO` to `validate` afterward.
- There is no AWS account or infrastructure access configured here, so deployment and a public health URL remain unverified.

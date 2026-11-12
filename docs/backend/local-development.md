# Backend local development

Prerequisites: JDK 21 and Docker Compose. The services run as separate processes and connect to real PostgreSQL and RabbitMQ dependencies. Set local-only credentials in the environment; do not commit `.env` files or real secrets.

## Start dependencies

```powershell
$env:POSTGRES_USER='aja_owner'
$env:POSTGRES_PASSWORD='<local-owner-password>'
$env:CORE_DATABASE_PASSWORD='<local-core-password>'
$env:QUESTIONS_DATABASE_PASSWORD='<local-questions-password>'
$env:EMAIL_DATABASE_PASSWORD='<local-email-password>'
$env:RABBITMQ_USER='aja'
$env:RABBITMQ_PASSWORD='<local-rabbitmq-password>'
docker compose up -d --wait
```

PostgreSQL creates the service roles and schemas when its persistent volume is first initialized. Removing the `postgres-data` volume destroys local database contents.

## Run services

Run each command in a separate terminal after setting that service's database password and the RabbitMQ credentials where needed. Override `SERVER_PORT` to change defaults Core `8081`, Questions `8082`, and Email `8083`.

```powershell
$env:CORE_DATABASE_PASSWORD='<local-core-password>'
$env:RABBITMQ_USER='aja'
$env:RABBITMQ_PASSWORD='<local-rabbitmq-password>'
$env:CORE_OUTBOX_ENCRYPTION_KEY='<base64-encoded-32-byte-key>'
$env:CORE_OUTBOX_OPERATOR_TOKEN='<private-outbox-operator-token-at-least-32-characters>'
$env:CORE_QUESTIONS_SERVICE_TOKEN='<shared-questions-core-token-at-least-32-characters>'
$env:EMAIL_DELIVERY_OPERATOR_TOKEN='<private-email-delivery-operator-token-at-least-32-characters>'
$env:API_ALLOWED_ORIGINS='http://127.0.0.1:3000'
$env:AJA_COOKIE_SECURE='false'
Set-Location services/core
.\gradlew.bat bootRun
```

Run Questions with the same service token, Core's private session URL, and public file API base:

```powershell
$env:QUESTIONS_DATABASE_PASSWORD='<local-questions-password>'
$env:CORE_QUESTIONS_SERVICE_TOKEN='<same-shared-questions-core-token>'
$env:CORE_INTERNAL_SESSION_URL='http://localhost:8081/internal/v1/session'
$env:CORE_PUBLIC_API_URL='http://localhost:8081/api/v1'
$env:API_ALLOWED_ORIGINS='http://127.0.0.1:3000'
Set-Location services/questions
.\gradlew.bat bootRun
```

Core requires both cryptographic secrets from the environment. Use the same outbox key for Core and the Email worker, and the same private service token for Core and the Questions caller. Set `AJA_COOKIE_SECURE=true` when TLS terminates before Core; direct HTTPS requests also set the cookie's Secure flag.

Questions needs `QUESTIONS_DATABASE_PASSWORD`, `CORE_QUESTIONS_SERVICE_TOKEN` (the same value configured in Core), `CORE_INTERNAL_SESSION_URL` (for example `http://localhost:8081/internal/v1/session`), and does not require RabbitMQ. Set `CORE_PUBLIC_API_URL` to Core's public API base (default `http://localhost:8081/api/v1`) so question image references point to Core-owned file content. Its public routes use `/api/v1`; the authenticated snapshot provider is private at `/internal/v1/snapshots`, outside the public OpenAPI. Email needs `EMAIL_DATABASE_PASSWORD`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD`, the same `CORE_OUTBOX_ENCRYPTION_KEY` as Core, and `EMAIL_DELIVERY_OPERATOR_TOKEN` for its private DLQ redrive operation. Configure `EMAIL_PROVIDER_URL`, `EMAIL_PROVIDER_TOKEN`, and `EMAIL_ACTION_BASE_URL` for the delivery adapter. Core and Questions health are `GET /api/v1/health`; Email health is operational at `GET /health`.

## Verify

Run in each service directory:

```powershell
.\gradlew.bat test
.\gradlew.bat build
```

Integration tests use Testcontainers and require Docker. To run only health unit tests, use `.\gradlew.bat test --tests '*HealthControllerTests'`; Core and Questions Origin checks use `--tests '*OriginFilterTests'`.

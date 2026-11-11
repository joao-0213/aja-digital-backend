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
Set-Location services/core
.\gradlew.bat bootRun
```

Questions needs `QUESTIONS_DATABASE_PASSWORD` and does not require RabbitMQ. Email needs `EMAIL_DATABASE_PASSWORD`, `RABBITMQ_USER`, and `RABBITMQ_PASSWORD`. Core and Questions public routes use `/api/v1`; Core health is `GET /api/v1/health`. Email health is operational at `GET /health`.

## Verify

Run in each service directory:

```powershell
.\gradlew.bat test
.\gradlew.bat build
```

Integration tests use Testcontainers and require Docker. To run only health unit tests, use `.\gradlew.bat test --tests '*HealthControllerTests'`; Core and Questions Origin checks use `--tests '*OriginFilterTests'`.

# Email

Worker assíncrono que consome eventos versionados publicados via RabbitMQ; mantém estado/deduplicação no schema `email` e envia por adaptador configurável. Não hospeda endpoint browser nem é fonte de identidade.

See [backend local development](../../docs/backend/local-development.md) for prerequisites, dependency startup, configuration, health, tests, and build commands.

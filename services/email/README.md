# Email — escopo previsto

Worker assíncrono que consome eventos versionados publicados via RabbitMQ; mantém estado/deduplicação no schema `email` e envia por adaptador configurável. Não hospeda endpoint browser nem é fonte de identidade.

Não há código Kotlin, Gradle, Docker, API, migração ou broker configurado neste diretório. Consulte B-01 e B-03 em `ISSUES-BACKEND.md`.

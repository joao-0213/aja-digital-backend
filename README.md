# AJA Digital Backend

Repositório dos serviços Kotlin/Spring Boot do AJA Digital, ainda não implementados. O trabalho está definido em [`ISSUES-BACKEND.md`](ISSUES-BACKEND.md); todas as issues permanecem abertas e nenhuma implementação backend acompanha esta preparação.

## Serviços previstos

- [`services/core/`](services/core/README.md): identidade, autorização acadêmica e domínios Core.
- [`services/questions/`](services/questions/README.md): acervo e provider interno de snapshots.
- [`services/email/`](services/email/README.md): worker assíncrono de email.

Comece por [`ARQUITETURA.md`](ARQUITETURA.md), [`AGENTS.md`](AGENTS.md), o cabeçalho B-01 do backlog e os contratos versionados em [`docs/http/`](docs/http/). Ainda não há comandos de build/teste backend: B-01 deve escolher e registrar ferramentas reproduzíveis. Comandos `npm` nos documentos HTTP descrevem somente o simulador frontend.

As [skills locais](docs/agents/skills.md) preservam a pipeline de execução e incluem as nove orientações técnicas aprovadas. O [Graphify](docs/graphify.md) indexa os contratos e as issues localmente; seus artefatos não são versionados. Veja as [verificações da preparação](docs/setup-validation.md).

Este repositório tem Git próprio, sem remoto configurado. A cópia dos contratos não modifica o frontend nem configura comunicação entre os repositórios. API Gateway, integração de telas e implementação das issues exigem trabalho posterior explicitamente solicitado.

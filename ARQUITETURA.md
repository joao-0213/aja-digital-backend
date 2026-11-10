# Arquitetura prevista do backend AJA Digital

Este documento fixa as fronteiras de domínio e dados do backlog; não descreve serviços já executáveis. Os três serviços previstos são Kotlin/Spring Boot, diretamente acessíveis nesta etapa, sem gateway público ou integração com frontend.

## Fronteiras

| Serviço | Responsabilidade | Persistência |
| --- | --- | --- |
| Core | Identidade, sessão, autorização acadêmica, domínios acadêmicos, arquivos e snapshots persistidos | Schema `core`, role própria |
| Questões | Acervo e provider privado/versionado de snapshots | Schema `questions`, role própria |
| Email | Consumo assíncrono e envio por adaptador | Schema `email`, role própria |

Os schemas ficam na mesma instância PostgreSQL, mas cada role acessa apenas o próprio schema. Migrations pertencem ao serviço. Não há SQL cross-schema, identidade compartilhada, snapshot global ou dual-write. Core grava outbox na transação de negócio e publica eventos via RabbitMQ; Email consome, deduplica estado interno e não é fonte de identidade.

## Contratos e protocolos

`docs/http/openapi.json` fixa a superfície pública OpenAPI 3.1 versão 1.0.8 e deve ser preservado. Cookie público: `aja_session` HttpOnly segundo os atributos do contrato. Não inventar CSRF header, JWT, mudança de IDs, paginação, status, schemas ou respostas; os exemplos genéricos das skills não substituem o OpenAPI. Interfaces internas Core↔Questões e Core→RabbitMQ→Email são autenticadas e não fazem parte de `/api/v1`. A matriz completa está em `docs/backend/operation-ownership.md`.

Não construir gateway, proxy, BFF, UI administrativa nem integração do frontend nesta fase. Os serviços são testados diretamente contra PostgreSQL e RabbitMQ reais de teste ou processos equivalentes; mocks HTTP não comprovam os contratos.

## Organização hexagonal

Cada serviço deve separar domínio, aplicação/casos de uso, portas e adaptadores de entrada/saída. Domínio não depende de Spring, JPA, RabbitMQ ou transporte. Adaptadores de banco, mensageria e HTTP chamam portas de aplicação; regras e autorização permanecem no serviço proprietário. B-01 definirá a estrutura concreta e as ferramentas sem antecipar convenções não decididas.

## Fontes e limites

- `ISSUES-BACKEND.md` é o backlog executável; todas as issues estão abertas.
- `docs/http/` contém contrato e regras observáveis copiados do simulador, com proveniência. Exemplos de JSON Server/storage local não são arquitetura obrigatória.
- `docs/reference/frontend-sources.md` identifica arquivos históricos externos. Suites frontend são referências, nunca evidência de backend.
- `services/*/README.md` registra apenas escopo previsto até B-01 criar os projetos.

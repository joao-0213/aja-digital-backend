# Fontes de referência do frontend

Fontes usadas para definir o contrato observável do backend, consultadas em `aja-digital-frontend@2257d2af4950983f067f7f20b4d2e6b021f365e8`. Não foram copiadas implementações, logs em massa, artefatos de evidência ou testes executáveis.

| Referência histórica | Uso | Tratamento neste repositório |
| --- | --- | --- |
| `ISSUES-FRONTEND-HTTP.md` | Contexto da migração e contratos de referência | Fonte externa; não é autorização para executar issues frontend. |
| `tests/http-*.test.*`, incluindo `tests/http-real-process-contract.test.mjs` | Cenários observáveis do simulador | Referência externa; suites não provam comportamento dos serviços Kotlin. |
| `src/`, `server/`, `package.json`, comandos `npm` | Implementação e operação do simulador | Contexto frontend apenas; não são estrutura, comandos nem runtime deste repositório. |
| `docs/http/evidence/**`, `docs/http/validation-*` | Evidências de validação frontend | Omitidas; não são evidência de aceite backend. |

Os contratos normativos relevantes foram copiados para `docs/http/` e `docs/backend/operation-ownership.md`. O arquivo `docs/http/openapi.json` é snapshot byte-idêntico da fonte. Para rastrear detalhes históricos não representados nesses contratos, consulte o commit frontend acima.

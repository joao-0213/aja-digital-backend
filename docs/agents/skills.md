# Skills locais do backend

As skills estão versionadas em `.agents/skills/`. Não dependem de links para o checkout do frontend ou para o cache pessoal de plugins. São instruções e referências: copiá-las não executa seus exemplos nem implementa qualquer issue.

## Pipeline reaproveitada

| Skill / guia | Origem | Uso |
| --- | --- | --- |
| `tdd-workflow`, `verification-loop`, `code-reviewer.md` | ECC 2.2.3 | RED/GREEN, validação e revisão independente |
| `ponytail`, `ponytail-review` | Ponytail 5.3.3 | Mudança mínima suficiente e segunda revisão independente |
| `graphify` | Graphify 0.9.95 | Navegação pelo grafo local e atualização após mudanças |

O guia ECC de revisão fica em [code-reviewer.md](code-reviewer.md). A skill Graphify foi copiada da instalação local existente. O executável Graphify permanece uma ferramenta instalada na máquina; não foi incorporado como dependência de execução dos serviços.

## Nove skills adicionais aprovadas

Todas vieram do ECC 2.2.3, revisão instalada `5af4395e5c52643b7f5ecc716079cd253504b354`. O usuário aprovou sua inclusão antes da cópia.

| Skill | Aplicar quando |
| --- | --- |
| `kotlin-patterns` | Escrever ou revisar Kotlin |
| `kotlin-testing` | Escrever testes Kotlin com as ferramentas escolhidas na B-01 |
| `springboot-patterns` | Implementar adaptadores Spring Boot |
| `springboot-security` | Implementar autenticação e autorização próprias dos serviços |
| `hexagonal-architecture` | Definir portas, adaptadores e dependências internas |
| `jpa-patterns` | Implementar persistência JPA e suas transações |
| `postgres-patterns` | Projetar consultas, índices e privilégios PostgreSQL |
| `database-migrations` | Criar e verificar migrações do schema do serviço |
| `docker-patterns` | Preparar o ambiente reproduzível quando a issue autorizar |

## Precedência e limites

`AGENTS.md`, a issue e o contrato público prevalecem sobre os exemplos. Exemplos Java, JWT, CSRF desabilitado, IDs numéricos, `Page`, Problem Details, `auth.uid()`, `ALTER SYSTEM`, Kafka, Redis ou Ktor não modificam as decisões do AJA. O serviço conserva cookie `aja_session`, proteção de Origin, IDs opacos, envelopes e códigos do OpenAPI, e acesso somente ao próprio schema. B-01 decide ferramentas de build, teste e cobertura; instalar a skill não escolhe Kotest, MockK, Kover, Detekt ou um percentual de cobertura.

Comandos de reset, remoção de volumes, instalação global, login, publicação e envio a serviços externos nos exemplos não estão autorizados por esta preparação. As referências opcionais a outros agentes e exemplos dos projetos de origem não são dependências ausentes deste backend. Nenhum hook de telemetria do Ponytail nem configuração pessoal de plugins foi copiado.

## Proveniência

[skills-manifest.json](skills-manifest.json) registra versões e hashes SHA-256 dos arquivos copiados. Origem ECC: [affaan-m/ECC](https://github.com/affaan-m/ECC). As cópias conservam os textos originais e suas referências. Licenças: [ECC MIT](licenses/ECC-LICENSE), [Ponytail MIT](licenses/PONYTAIL-LICENSE), [Graphify Apache-2.0](licenses/GRAPHIFY-LICENSE), [MIT histórico Graphify](licenses/GRAPHIFY-LICENSE-MIT) e [NOTICE Graphify](licenses/GRAPHIFY-NOTICE).

Skills locais são descobertas pelo ambiente ao abrir uma nova sessão no repositório; consulte a [documentação oficial](https://developers.openai.com/codex/skills/). Alterações de modelo/effort do coordenador dependem dos controles do host, não são realizadas por este documento.

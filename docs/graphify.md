# Graphify local

O grafo inicial indexa documentos, títulos, links locais, dependências B-01–B-16 e ownership das 98 operações do OpenAPI. É um mapa dos contratos e do trabalho planejado, não de serviços implementados. A extração é determinística, sem chamada a API de IA, e não tenta inferir relações não escritas. Skills de terceiros e suas licenças não fazem parte do grafo do produto.

## Consultar

Na raiz do backend, com Graphify 0.9.95 instalado:

```powershell
graphify query "snapshots questões"
graphify explain "B-15"
graphify hook status
```

## Reconstruir os contratos

O índice inicial foi criado durante esta preparação por extração determinística de títulos, links, operações e dependências documentadas. Não contém análise semântica completa dos textos nem código de serviços. Para gerar um índice mais detalhado ou atualizar documentos, invoque `/graphify --update` no agente seguindo a [skill Graphify](../.agents/skills/graphify/SKILL.md). Em um checkout sem grafo, invoque `/graphify .`.

Nesta máquina, Graphify 0.9.95 está instalado via `uv tool`, com Python próprio e independente do frontend. Os caminhos da máquina ficam somente em `graphify-out/`. A instalação local é necessária em outro computador; as skills versionadas não instalam o executável automaticamente. `graphify export html` gera a visualização local do índice atual.

## Hooks e atualização

Na configuração local deste repositório foram instalados hooks `post-commit`, `post-checkout`, `post-merge` e `post-rewrite`. Clonar um repositório não instala hooks automaticamente; em um novo checkout, execute:

```powershell
graphify hook install
graphify hook status
```

Os hooks atualizam a extração estrutural de código. Mudanças em documentos exigem a atualização pela skill. Depois de implementar código, execute `graphify update .` e confirme o resultado. O diretório `graphify-out/`, incluindo caminhos de Python, caches, logs, JSON e HTML, é local e ignorado pelo Git. Nunca copiar o grafo do frontend para este backend.

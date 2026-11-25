# AJA Digital Backend

O ambiente local conecta o frontend ao API Gateway, ao Core e ao Questions. O Core publica notificações no RabbitMQ para o worker Email, e os três serviços persistem os dados no PostgreSQL.

## Iniciar a demonstração

Tenha o Docker com Compose em execução, Node.js 22.6 ou superior com npm e os repositórios `aja-digital-backend` e `aja-digital-frontend` em pastas vizinhas.

1. No diretório `aja-digital-backend`, suba os serviços e aguarde a carga dos dados demonstrativos.

   ```powershell
   docker compose --env-file env.integrated.example -f compose.integrated.yaml -p aja-integrated up -d --build --wait
   ```

2. Em outro terminal, a partir do mesmo diretório, inicie o frontend integrado.

   ```powershell
   cd ../aja-digital-frontend
   npm ci
   npm run dev
   ```

3. Abra o [AJA Digital](http://127.0.0.1:3000). O [Gateway](http://127.0.0.1:3002/api/v1/health) atende a API e o [Mailpit](http://127.0.0.1:8025) recebe os e-mails locais. Use `127.0.0.1` no navegador para manter o mesmo host da API e do cookie de sessão.

No PowerShell, use `npm.cmd` se a política de execução bloquear `npm`. As portas 3000, 3002 e 8025 devem estar livres para esse ambiente. Para usar a versão estática na apresentação, rode `npm run build` no frontend e inicie a prévia com `$env:PORT='3000'; npm run preview`.

## Contas demonstrativas

Todas as contas usam a senha fictícia `AjaDemo-2026!`, exclusiva do ambiente local.

| Perfil | E-mail | Roteiro sugerido |
| --- | --- | --- |
| Administrador | `admin@aja.local.test` | Consulte usuários, turmas e matrículas. |
| Professora | `professora@aja.local.test` | Abra Matemática e Proporções, publique um quiz com questão do acervo e registre frequência. Confira o histórico em Alunos. |
| Aluno | `aluno@aja.local.test` | Abra os materiais e as atividades de Matemática e Proporções e envie uma tentativa da atividade recém-publicada. |
| Aluna | `bia@aja.local.test` | Use uma segunda matrícula para conferir a visão de outro aluno da turma. |

Para conferir a entrega de e-mails, publique como professora um aviso de Matemática com a opção de notificação marcada e consulte a caixa do Mailpit.

## Encerrar

Interrompa o frontend com `Ctrl+C` e rode o comando abaixo no diretório do backend.

```powershell
docker compose --env-file env.integrated.example -f compose.integrated.yaml -p aja-integrated down
```

O comando preserva o volume do PostgreSQL. Para repetir a apresentação com os mesmos dados, use o mesmo nome de projeto Compose e o mesmo arquivo de ambiente.

Para configuração de portas, prévia estática, testes e execução dos serviços, consulte o [guia de desenvolvimento local](docs/backend/local-development.md). O [registro da INT-06](docs/backend/validation-INT-06.md) descreve os fluxos homologados. As fronteiras dos serviços estão em [ARCHITECTURE.md](ARCHITECTURE.md) e a API pública está no [OpenAPI](docs/http/openapi.json).

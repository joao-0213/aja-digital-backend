# Homologação integrada INT-06

Execução local em 25 de novembro de 2026. Base do backend `889ab31` com alterações de Core anteriores à INT-06. Base do frontend `b69803e`. O Graphify apontou o Compose integrado, a configuração do Gateway, o seed e a matriz de ownership. A homologação usou Docker Compose 5.7.0, Java 21, Node.js 24 e Chromium.

O projeto isolado `aja-int06` usou frontend em `127.0.0.1:3000`, Gateway em `127.0.0.1:33126` e Mailpit em `127.0.0.1:38026`. Os containers anteriores permaneceram em execução. As requisições do frontend estático seguiram pela API pública do Gateway, com sessão real no Core.

## Jornadas comprovadas

| Jornada | Evidência |
| --- | --- |
| Administrador | Criou uma turma, matriculou a conta recém-confirmada e conferiu o vínculo ativo na interface. |
| Professora | Abriu a lista de chamada de Matemática e Proporções, registrou uma presença e uma ausência e conferiu esses dados após recarregar e no histórico do perfil do aluno. Selecionou uma questão do acervo e publicou o quiz. |
| Alunos | Enviaram duas tentativas do quiz com notas de 0/1 e 1/1. A primeira foi enviada pelo navegador e apareceu na lista de entregas da professora. Pelo navegador, uma aluna concluiu material e publicou uma resposta no fórum. Ambos continuaram visíveis após recarregar. |
| Devolutiva | A professora salvou um rascunho em uma entrega demonstrativa. O aluno viu a nota 8/10 e o comentário somente após publicação. |
| E-mail | Cadastro, confirmação, recuperação e login com a nova senha passaram no Chromium. A professora também publicou um aviso pelo mural e o aluno abriu o aviso pelo link recebido no Mailpit. O roteiro HTTP confirmou a ausência de e-mail para um aviso silencioso ou destinatário de outra turma. O RabbitMQ mostrou um consumidor e nenhuma mensagem pendente ou na DLQ no primeiro lote. |
| Contrato | Frequência por aluno retornou 403. A prática da questão retornou 200 antes da tentativa, 409 durante e 200 após o envio. O upload e o download preservaram os bytes de um PDF. |
| Persistência | Após `docker compose stop gateway questions core email rabbitmq postgres` e `up -d --wait` no mesmo projeto, 18 linhas de controle permaneceram idênticas em PostgreSQL. A comparação incluiu usuário, turma, matrícula, frequência, avaliação, snapshot do acervo, tentativas, devolutiva, progresso, fórum, catálogo, outbox e entregas. Novas sessões releram a turma, a matrícula, a chamada, o quiz, o resultado e a questão pelo Gateway com HTTP 200. |

## Correções

- O cadastro pedia ao usuário que solicitasse a primeira mensagem, embora o Core já enfileirasse a confirmação. O teste no Chromium recebeu a mensagem automática após o HTTP 201 e falhou ao conferir a instrução de consultar o e-mail. Com a nova instrução e o botão de reenvio, o mesmo fluxo passou, incluindo confirmação e recuperação pelos links do Mailpit. As revisões independentes ECC e Ponytail aprovaram a alteração do frontend, registrada no commit local `456d0b3`.
- O teste Core–Questions enviava a requisição de prática sem o cabeçalho `Origin` obrigatório e recebia 403. O processo de teste do Questions passou a receber a origem permitida e a requisição passou a enviá-la. A política do serviço segue a do contrato HTTP.
- O formulário de aviso da professora ficava sem turmas mesmo com disciplinas atribuídas. O teste HTTP `node runtime/int06/teacher-class-scope.mjs` recebeu uma lista vazia. Com o escopo pela atribuição vigente, a lista e o detalhe passaram a incluir as turmas atribuídas, inclusive arquivadas. O painel manteve o filtro de turmas ativas e o detalhe de turma de outra equipe retornou 404. O formulário passou a publicar o aviso pelo navegador.
- O mesmo bloqueio anterior às atribuições devolvia zero alunos e vínculos ao docente. `node runtime/int06/teacher-student-scope.mjs` reproduziu a lista vazia e passou após o último rebuild do Core. O teste no Gateway confirmou a listagem dos alunos, a abertura do perfil, a ocultação de outras turmas e a restrição da consulta administrativa de matrícula. A regressão em PostgreSQL confirmou também o histórico de vínculos encerrados e a exclusão das matrículas pendentes.
- O perfil mostrava o histórico de frequência vazio mesmo após a chamada gravada. O teste no navegador `node runtime/int06/student-profile-browser.cjs`, com `AJA_EXPECT_PROFILE_ATTENDANCE=true`, abriu o perfil e a matrícula ativa, mas não encontrou a ausência. Após integrar o endpoint de frequência pelo ID do aluno, o teste mostrou a presença e a ausência com o nome da disciplina. A abertura do perfil também passou com teclado em viewport de 390 × 844. As revisões independentes ECC e Ponytail aprovaram a alteração do frontend, registrada no commit local `ff18a7b`.
- As revisões de backend ECC e PostgreSQL aprovaram as alterações. O perfil registrado `database-reviewer` usou o modelo fixo `gpt-6.1-sol` com esforço médio.

## Comandos executados

- `docker compose --env-file env.integrated.example -f compose.integrated.yaml -p aja-int06 up -d --build --wait` com `GATEWAY_PORT=33126`, `MAILPIT_PORT=38026` e `CORE_PUBLIC_API_URL=http://127.0.0.1:33126/api/v1`. Gateway e seis dependências saudáveis, seed concluído com código 0.
- `npm.cmd run build` com `NEXT_PUBLIC_API_URL=http://127.0.0.1:33126/api/v1` e `npm.cmd run preview` com `PORT=3000` no repositório frontend. Exportou 24 rotas.
- `npm.cmd test` passou com 369 testes no frontend final. `npm.cmd run typecheck`, `npm.cmd run check:architecture` e `npm.cmd run check:export` passaram. O teste focado `node --experimental-strip-types --test tests/attendance-client.test.ts` passou com 4 testes.
- `.\gradlew.bat build --console=plain` e `.\gradlew.bat test --rerun-tasks --console=plain` passaram em Questions e Email. A execução forçada registrou 28 testes em Questions e 20 em Email.
- No Core, `.\gradlew.bat test --tests "*AssessmentIntegrationTests" --tests "*HttpQuestionSnapshotProviderTests" --console=plain` passou com 28 testes após corrigir o `Origin`. Após corrigir consultas acadêmicas, `.\gradlew.bat test --tests "*DashboardIntegrationTests" --tests "*AcademicIntegrationTests" --console=plain` passou com 8 testes de dashboard e escopo acadêmico.
- O build final do Core com `.\gradlew.bat build --console=plain` passou com 203 testes. O diagnóstico separado `.\gradlew.bat architectureAudit --console=plain` retornou código 1 e manteve a contagem anterior de 151 apontamentos, já registrada na evidência local de GP-04.3. O relatório está em `services/core/build/reports/architectureAudit/core-violations.txt`.
- `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\acceptance-int04-email.ps1 -ApiBase http://127.0.0.1:33126/api/v1 -MailpitBase http://127.0.0.1:38026 -FrontendBase http://127.0.0.1:3000`. Cadastro, recuperação e aviso confirmados no Mailpit.
- `node ..\aja-digital-frontend\docs\http\evidence\INT-05\api-flow.mjs` com `AJA_API_BASE=http://127.0.0.1:33126/api/v1` e a senha fictícia em `AJA_DEMO_PASSWORD`. Checagens de sessão, frequência, arquivo e logout passaram.
- `python scripts/check-gateway-routes.py` confirmou 98 operações públicas em 75 caminhos. As interfaces privadas ficaram fora do Gateway.

Os roteiros de navegador e de consultas guardados em `runtime/int06` são evidência local deste ambiente. O [README](../../README.md) contém o roteiro de apresentação e as contas demonstrativas.

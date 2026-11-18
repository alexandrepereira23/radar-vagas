# STATUS — Radar de Vagas

Última atualização: 18/11/2026

## 1. Etapa Atual

- **Etapa 1 — Primeira entrega funcional: CONCLUÍDA.**
- **Etapa 2 — Cobertura em múltiplas fontes: CONCLUÍDA.**
- As Etapas 3 e 4 permanecem para as próximas iterações.

---

## 2. Decisões Técnicas (Etapas 1 e 2)

1. **Docker Compose e Isolamento de Porta (`127.0.0.1:5433`):**
   - Com o Docker Desktop ativo, identificou-se que a porta `5432` do host Windows já é ocupada por um processo PostgreSQL nativo do sistema (PID 6500). Para evitar colisão de porta e garantir subida limpa via `docker compose up -d`, o container `radar-vagas-postgres` (`postgres:17.12-alpine`) foi mapeado para `127.0.0.1:5433:5432`.
2. **Migração Flyway V2 (`V2__multiplas_fontes_etapa2.sql`):**
   - Evoluiu `configuracao_fonte` com `categoria`, `estado_validacao`, `metodo_acesso`, `credencial_necessaria` e `observacao_validacao`, permitindo persistir e exibir no produto o registro formal de fontes ativas, experimentais e bloqueadas.
   - Adicionou `codigo_requisicao` em `oportunidade` e `anuncio_origem` e índice por `fonte_principal`.
3. **Filtro de Cargo Técnico e Elegibilidade no Brasil:**
   - Para murais corporativos e portais de busca ampla (`GREENHOUSE_BOARD`, `ASHBY_BOARD`, `LEVER_BOARD`, `GUPY_PORTAL`, `JOOBLE_API`, `ADZUNA_API`), `GitHubIssueClassificador.classificarAnuncioExterno` aplica filtro explícito de cargo técnico (descartando vagas comerciais, jurídicas, RH, operações imobiliárias, etc.) e validação de elegibilidade para residentes no Brasil (rejeitando vagas `US Only`, `EU Only`, etc.).
4. **Agrupamento Conservador Entre Fontes:**
   - Oportunidades vindas de fontes distintas são unidas somente com evidência forte:
     1. Mesmo link específico de candidatura após normalização segura (removendo parâmetros de rastreamento como `utm_*`, `fbclid`, `gclid`, `jobBoardSource`, `gh_src` e sufixos `/application`, mas preservando parâmetros identificadores de vaga).
     2. Mesmo `codigo_requisicao` (`requisition_id`) explícito na mesma empresa identificada.
   - Mesmo cargo e mesma empresa sem link ou código equivalente **não** são unidos, evitando ocultar vagas distintas de equipes ou cidades diferentes.

---

## 3. Registro de Fontes Avaliadas e Integradas (Etapa 2)

| Fornecedor | Identificador / Escopo | Estado | Método de Acesso e Paginação | Credenciais | Resultado do Teste Real (18/11/2026) |
| --- | --- | --- | --- | --- | --- |
| `GITHUB_ISSUES` | `backend-br/vagas`, `frontendbr/vagas`, `qa-brasil/vagas`, `datascience-br/vagas`, `DevOps-Brasil/Vagas` | **ATIVA_VALIDADA** | REST API oficial (`GET /repos/{owner}/{repo}/issues`), `per_page`/`page`, cursor `since` com sobreposição de 60 min | Opcional: `GITHUB_TOKEN` (sem token: 60 req/h) | **Validado com rede real** nos 5 repositórios: coletou vagas reais de Backend, Frontend, QA, Dados e DevOps no Brasil com 0 erros. |
| `GUPY_PORTAL` | Busca ampla: `desenvolvedor` (configurável) | **ATIVA_VALIDADA** | HTTP `GET https://portal.gupy.io/job-search/term=...` extraindo JSON estruturado SSR (`__NEXT_DATA__` -> `initialJobList.data`) sem navegador automatizado | Nenhuma | **Validado com rede real**: retornou vagas reais publicadas no Brasil (`CXP Brasil`, `Banco Industrial do Brasil`, `Somos BHS`, etc.) com `publishedDate`, empresa, modalidade, regime e link direto. |
| `GREENHOUSE_BOARD` | `quintoandar`, `ebanx`, `vtex`, `wildlifestudios` | **ATIVA_VALIDADA** | REST API oficial (`GET https://boards-api.greenhouse.io/v1/boards/{board}/jobs?content=true`) | Nenhuma | **Validado com rede real**: coletou 20 vagas técnicas elegíveis no Brasil das 4 empresas e descartou automaticamente 42 vagas não-técnicas ou fora do Brasil. |
| `ASHBY_BOARD` | `supabase` | **ATIVA_VALIDADA** | REST API oficial (`GET https://api.ashbyhq.com/posting-api/job-board/{board}`) | Nenhuma | **Validado com rede real**: coletou 5 vagas técnicas elegíveis para Brasil/AMER e filtrou 13 vagas de outras regiões/áreas. |
| `JOOBLE_API` | `tecnologia-brasil` | **BLOQUEADA_CREDENCIAL** | Conector HTTP implementado (`POST https://br.jooble.org/api/{key}`) | `JOOBLE_API_KEY` | Conector implementado e testado com servidor HTTP local; aguarda variável `JOOBLE_API_KEY` no ambiente para ativação real. |
| `ADZUNA_API` | `tecnologia-brasil` | **BLOQUEADA_CREDENCIAL** | Conector HTTP implementado (`GET https://api.adzuna.com/v1/api/jobs/br/search/1`) | `ADZUNA_APP_ID` e `ADZUNA_APP_KEY` | Conector implementado e testado; aguarda variáveis `ADZUNA_APP_ID` e `ADZUNA_APP_KEY` no ambiente para ativação real. |
| `JSEARCH_API` | `tecnologia-brasil` | **BLOQUEADA_CREDENCIAL** | API REST OpenWebNinja / RapidAPI JSearch | `JSEARCH_API_KEY` | Bloqueado por ausência da credencial `JSEARCH_API_KEY`. |
| `LEVER_BOARD` | `LeverConector` | **EXPERIMENTAL_ATIVAVEL** | REST API oficial (`GET https://api.lever.co/v0/postings/{site}?mode=json`) | Nenhuma | Conector implementado; empresas BR testadas (`ifood`, `picpay`, `totvs`, `ciandt`, `loggi`, `wellhub`) retornaram HTTP 404 por terem migrado de ATS. |
| `SOLIDES_PORTAL` | `vagas.solides.com.br` | **BLOQUEADA_RESTRICAO_ACESSO** | Portal Next.js App Router | API não documentada | Testado via HTTP real (Status 200): HTML inicial não contém JSON estruturado de vagas (`__NEXT_DATA__` ausente) e não há API pública documentada. |
| `LINKEDIN` | `linkedin-jobs` | **BLOQUEADA_RESTRICAO_ACESSO** | Automação proibida pelos Termos de Uso | Parceria LinkedIn | Bloqueado por conformidade com `linkedin.com/help/linkedin/answer/a1341387` e diretrizes do PRD. |
| `INDEED_GLASSDOOR` | `indeed-glassdoor-br` (e JobSpy) | **BLOQUEADA_RESTRICAO_ACESSO** | Protegido por Cloudflare / desafio anti-bot | Sem API gratuita aberta | Bloqueado em conformidade com o PRD (proibido contornar CAPTCHA ou bloqueios anti-bot). |

---

## 4. Verificações Realizadas

1. **Docker Compose (`docker compose up -d` e `docker compose ps`):**
   - Container `radar-vagas-postgres` (`postgres:17.12-alpine`) em execução e saudável em `127.0.0.1:5433->5432/tcp`.
2. **Testes de Comportamento do Backend (`.\mvnw.cmd test`):**
   - **13 testes executados e aprovados (0 falhas)**:
     - `GitHubClassificadorESegurancaTest` (4 testes)
     - `ColetaECatalogoComportamentoTest` (6 testes)
     - `MultiplasFontesEAgrupamentoTest` (2 testes cobrindo coleta multi-fonte `Greenhouse`, `Gupy __NEXT_DATA__`, `Ashby`, `GitHub Issues`, filtro de cargo técnico, agrupamento conservador entre fontes distintas, filtro por fornecedor e registro explícito de `BLOQUEADA_CREDENCIAL` / `BLOQUEADA_ACESSO`)
     - `RadarVagasBackendApplicationTests` (1 teste)
3. **Verificação End-to-End Real no Container Docker PostgreSQL 17:**
   - Migrações Flyway `V1` e `V2` aplicadas automaticamente no container `radar-vagas-postgres`.
   - Coleta real executada nas 11 fontes ativas (`5` comunidades GitHub + `1` Gupy Portal Brasil + `4` murais Greenhouse + `1` mural Ashby): todas concluídas com `statusCodigo: SUCESSO` e `errosContagem: 0`, persistindo **42 oportunidades técnicas reais** no banco PostgreSQL 17.
4. **Testes e Build do Frontend (`npm test` e `npm run build`):**
   - Testes unitários/comportamentais aprovados via Vitest e bundle de produção compilado sem erros.

---

## 5. Limitações e Pendências de Credenciais

1. **Agregadores com Credencial Obrigatória (`BLOQUEADA_CREDENCIAL`):**
   - **Jooble Brasil (`JOOBLE_API`):** conector implementado em [JoobleConector.java](file:///C:/Users/Alexandre_HP/Documents/radar-vagas/backend/src/main/java/br/com/radarvagas/coleta/JoobleConector.java). Depende da variável de ambiente `JOOBLE_API_KEY` (solicitável gratuitamente em `https://br.jooble.org/api/about`). Quando definida, a fonte passa automaticamente para `ATIVA_VALIDADA`.
   - **Adzuna Brasil (`ADZUNA_API`):** conector implementado em [AdzunaConector.java](file:///C:/Users/Alexandre_HP/Documents/radar-vagas/backend/src/main/java/br/com/radarvagas/coleta/AdzunaConector.java). Depende das variáveis `ADZUNA_APP_ID` e `ADZUNA_APP_KEY` (registráveis em `https://developer.adzuna.com/overview`). Quando definidas, a fonte passa automaticamente para `ATIVA_VALIDADA`.
   - **JSearch (`JSEARCH_API`):** mantida como `BLOQUEADA_CREDENCIAL` por exigir chave de API (`JSEARCH_API_KEY`) com cota limitada/planos pagos (nenhum plano foi adquirido, em conformidade com o PRD).
2. **GitHub Rate Limit sem Token:**
   - Sem `GITHUB_TOKEN`, a API pública de Issues opera com limite de 60 requisições/hora por IP (com os 5 repositórios ativos consumindo 5 requisições por ciclo completo). Com `GITHUB_TOKEN`, o limite sobe para 5.000 requisições/hora.
3. **Limitações de Murais Corporativos e Fontes Bloqueadas (`EXPERIMENTAL_ATIVAVEL` / `BLOQUEADA_RESTRICAO_ACESSO`):**
   - **Murais (`GREENHOUSE_BOARD`, `ASHBY_BOARD`, `LEVER_BOARD`):** exigem configurar os identificadores (`slugs`) das empresas; não substituem uma busca global automática. No Lever ([LeverConector.java](file:///C:/Users/Alexandre_HP/Documents/radar-vagas/backend/src/main/java/br/com/radarvagas/coleta/LeverConector.java)), as empresas brasileiras testadas migraram de plataforma (`HTTP 404`), ficando o conector pronto como `EXPERIMENTAL_ATIVAVEL` caso um slug ativo seja adicionado em [application.yml](file:///C:/Users/Alexandre_HP/Documents/radar-vagas/backend/src/main/resources/application.yml).
   - **Sólides (`SOLIDES_PORTAL`):** bloqueada por ausência de API pública documentada e por não expor dados estruturados (`__NEXT_DATA__` ou JSON-LD de vagas) na resposta HTTP inicial de `vagas.solides.com.br`.
   - **LinkedIn (`LINKEDIN`):** bloqueado por restrição expressa nos Termos de Uso contra automação e scraping (`a1341387`).
   - **Indeed e Glassdoor (`INDEED_GLASSDOOR` / JobSpy):** bloqueados por desafio anti-bot / Cloudflare Turnstile, respeitando a regra do PRD de nunca contornar CAPTCHAs ou controles de acesso.

---

## 6. Próximo Passo (Etapa 3 do PRD)

- **Etapa 3 — Uso diário:**
  - Completar filtros combináveis no servidor (área, tecnologia, nível/senioridade, modalidade, localidade e recência de 24h, 3 dias e 7 dias).
  - Implementar ações pessoais reversíveis (`já me candidatei` e `ocultar por falta de interesse`) com persistência no banco e preservação após novas coletas e reinícios.
  - Refinar estados de carregamento, erro, ausência de resultados e responsividade para uso diário.

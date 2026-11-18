# STATUS — Radar de Vagas

Última atualização: 18/11/2026 (Revisão de validação, paginação e credenciais)

## 1. Etapa Atual

- **Etapa 1 — Primeira entrega funcional:** Concluída.
- **Etapa 2 — Cobertura em múltiplas fontes:** **Concluída e validada com coleta real autenticada.** Fontes públicas (`GITHUB_ISSUES` nos 5 repositórios com `GITHUB_TOKEN` ativo e `rateLimitRestante: 4995`, `GUPY_PORTAL`, `GREENHOUSE_BOARD` nas 4 empresas e `ASHBY_BOARD`) e os dois agregadores implementados (`JOOBLE_API` e `ADZUNA_API`) foram validados em rede real com persistência no PostgreSQL 17.
- **Etapa 3 — Uso diário (Pendente):** Os filtros combináveis de recência de publicação (`24h`, `3 dias`, `7 dias`), área/senioridade/modalidade/localidade no servidor e os fluxos de alteração na API e na tela para ações pessoais (`já me candidatei` e `ocultar por falta de interesse`) são pendências da Etapa 3 (atualmente apenas a entidade/tabela `acao_pessoal` e a preservação em reexecuções estão implementadas).
- **Etapa 4 — Estabilização e implantação pessoal:** Pendente.

---

## 2. Correções Técnicas Implementadas (Revisão P1 e P2)

1. **Paginação Truncada, Janela Estável e Continuação Persistida no GitHub (`P1`):**
   - Criada a migração Flyway [V3__continuacao_paginacao_e_validacao.sql](file:///C:/Users/Alexandre_HP/Documents/radar-vagas/backend/src/main/resources/db/migration/V3__continuacao_paginacao_e_validacao.sql), adicionando `pagina_continuacao`, `cursor_janela_atual` e `maior_atualizacao_janela` em `configuracao_fonte`.
   - Quando [GitHubIssuesConector.java](file:///C:/Users/Alexandre_HP/Documents/radar-vagas/backend/src/main/java/br/com/radarvagas/coleta/GitHubIssuesConector.java) atinge `max-paginas-por-execucao` (ou rate limit) e a página está cheia / possui `rel="next"`, a coleta é reconhecida como incompleta (`SUCESSO_PARCIAL`), registrando a próxima página em `pagina_continuacao` e mantendo fixo o `cursor_janela_atual` (`since`), **sem avançar prematuramente `cursor_ultima_atualizacao`**.
   - Na execução seguinte, a coleta retoma a partir de `pagina_continuacao` sem repetir as primeiras páginas e sem descartar anúncios de páginas posteriores atualizados antes da primeira página. O `cursor_ultima_atualizacao` só avança quando toda a janela conclui sem truncamento e sem erro de persistência.
2. **Carregamento Previsível do `.env` pelo Spring Boot (`P1`):**
   - Configurado `spring.config.import` em [application.yml](file:///C:/Users/Alexandre_HP/Documents/radar-vagas/backend/src/main/resources/application.yml) com `optional:file:./.env[.properties]` e `optional:file:../.env[.properties]`.
   - O backend carrega automaticamente o `.env` da raiz tanto quando iniciado a partir de `backend/` (`.\mvnw.cmd spring-boot:run`) quanto a partir da raiz do projeto.
   - Os valores das chaves nunca são exibidos nos logs nem na API (`/api/coletas/status` expõe apenas o booleano `credencialConfigurada`). Em [application-test.yml](file:///C:/Users/Alexandre_HP/Documents/radar-vagas/backend/src/test/resources/application-test.yml), `spring.config.import: ""` isola os testes automatizados para nunca dispararem chamadas externas pagas.
3. **Tratamento Honesto do JSearch como Conector Não Implementado (`P1`):**
   - `JSEARCH_API` foi atualizado para `estado-validacao: NAO_IMPLEMENTADA` (com status de execução `NAO_IMPLEMENTADA`), deixando explícito na interface, no [README.md](file:///C:/Users/Alexandre_HP/Documents/radar-vagas/README.md) e no [.env.example](file:///C:/Users/Alexandre_HP/Documents/radar-vagas/.env.example) que ainda não há conector implementado e que `JSEARCH_API_KEY` deve permanecer vazia (sem contratação de assinatura).
4. **Separação entre Credencial Presente e Validação por Evidência Persistida (`P1`):**
   - Em [OrquestradorColetaService.java](file:///C:/Users/Alexandre_HP/Documents/radar-vagas/backend/src/main/java/br/com/radarvagas/coleta/OrquestradorColetaService.java), configurar `JOOBLE_API_KEY` ou `ADZUNA_APP_ID`/`ADZUNA_APP_KEY` habilita a fonte com estado `CONFIGURADA_NAO_VALIDADA` (e não mais `ATIVA_VALIDADA`).
   - Uma fonte só transita para `ATIVA_VALIDADA` quando uma execução de coleta persiste sucesso com payload válido.
   - Erros de autenticação (`HTTP 401/403` -> `FALHA_AUTENTICACAO`), rate limit (`LIMITE_ATINGIDO`) e respostas `HTTP 200` malformadas sem o array esperado (`jobs` no Jooble, `results` no Adzuna -> `FALHA_EXECUCAO`) são registrados e **preservados entre reinícios do backend**.
5. **Situação de Atualização por Fonte e Degradação Parcial no Estado Global (`P1`):**
   - `consultarStatusGlobal()` calcula a saúde individual de cada fonte ativa (`ATUALIZADA`, `ATUALIZACAO_PARCIAL`, `COM_FALHA`, `DESATUALIZADA`, `NUNCA_COLETADA`) considerando sua cadência, paginação pendente de continuação e o resultado da execução mais recente.
   - Quando uma fonte saudável coexiste com outra desatualizada, sem coleta ou com falha recente, o estado global informa explicitamente `Atualização parcial/degradada` (`dadosDesatualizados: true`, `atualizacaoParcial: true`) em vez de exibir o catálogo como totalmente atualizado a partir da fonte mais recente.
6. **Consultas Reais Configuráveis, Priorização por Recência e Rotação de Recortes por Limite (`P2`):**
   - [JoobleConector.java](file:///C:/Users/Alexandre_HP/Documents/radar-vagas/backend/src/main/java/br/com/radarvagas/coleta/JoobleConector.java), [AdzunaConector.java](file:///C:/Users/Alexandre_HP/Documents/radar-vagas/backend/src/main/java/br/com/radarvagas/coleta/AdzunaConector.java) e [GupyPortalConector.java](file:///C:/Users/Alexandre_HP/Documents/radar-vagas/backend/src/main/java/br/com/radarvagas/coleta/GupyPortalConector.java) deixaram de usar identificadores administrativos (`tecnologia-brasil`) como texto de busca e utilizam listas configuráveis de termos cobrindo desenvolvimento, QA, dados, infraestrutura/cloud, segurança e estágio, com ordenação por data (`sort_by=date` no Adzuna e ordenação por `publishedDate` na Gupy) e rotação controlada de termos entre execuções via `pagina_continuacao`.
   - [GreenhouseConector.java](file:///C:/Users/Alexandre_HP/Documents/radar-vagas/backend/src/main/java/br/com/radarvagas/coleta/GreenhouseConector.java), [AshbyConector.java](file:///C:/Users/Alexandre_HP/Documents/radar-vagas/backend/src/main/java/br/com/radarvagas/coleta/AshbyConector.java) e [LeverConector.java](file:///C:/Users/Alexandre_HP/Documents/radar-vagas/backend/src/main/java/br/com/radarvagas/coleta/LeverConector.java) ordenam os anúncios por data de publicação decrescente antes de aplicar `max-itens-por-mural` e rotacionam os blocos seguintes via `pagina_continuacao`, registrando explicitamente quando há recorte por limite de volume.
7. **Preservação Semântica e Idempotência de URLs Codificadas (`P2`):**
   - Corrigido [SanitizadorConteudo.java](file:///C:/Users/Alexandre_HP/Documents/radar-vagas/backend/src/main/java/br/com/radarvagas/catalogo/SanitizadorConteudo.java) (`validarENormalizarUrlPublica`) para remontar a URI a partir dos componentes brutos já escapados sem invocação do construtor multi-argumento que causava dupla codificação de `%` (`%2F` -> `%252F`).
8. **Consistência do `package-lock.json` para `npm ci` Multiplataforma:**
   - Adicionados `@emnapi/core@1.11.3` e `@emnapi/runtime@1.11.3` em [frontend/package.json](file:///C:/Users/Alexandre_HP/Documents/radar-vagas/frontend/package.json) e [frontend/package-lock.json](file:///C:/Users/Alexandre_HP/Documents/radar-vagas/frontend/package-lock.json) para satisfazer as `peerDependencies` de `@napi-rs/wasm-runtime` em instalações limpas via `npm ci --no-audit --no-fund` tanto em Linux quanto em Windows.

---

## 3. Quadro Detalhado das Fontes (Configuração vs Implementação vs Teste Local vs Coleta Real)

| Fornecedor | Identificador / Escopo | Conector Implementado? | Configuração / Credencial | Teste Local (Servidor HTTP de Teste) | Coleta Real Validada no Fornecedor? |
| --- | --- | --- | --- | --- | --- |
| `GITHUB_ISSUES` | `backend-br/vagas`, `frontendbr/vagas`, `qa-brasil/vagas`, `datascience-br/vagas`, `DevOps-Brasil/Vagas` | **Sim** | Opcional: `GITHUB_TOKEN` (presente via `.env`) | **Aprovado** (paginação truncada e retomada, rate limit, deduplicação) | **Sim — Validada em rede real autenticada** (`rateLimitRestante: 4995`, 0 erros) |
| `GUPY_PORTAL` | Termos rotativos: `desenvolvedor`, `qa`, `dados`, `devops`, `seguranca`, `estagio` | **Sim** | Nenhuma | **Aprovado** (`__NEXT_DATA__`, priorização por recência) | **Sim — Validada em rede real** |
| `GREENHOUSE_BOARD` | `quintoandar`, `ebanx`, `vtex`, `wildlifestudios` | **Sim** | Nenhuma | **Aprovado** (ordenação por `first_published`, rotação de blocos por limite, filtro técnico BR) | **Sim — Validada em rede real** nas 4 empresas |
| `ASHBY_BOARD` | `supabase` | **Sim** | Nenhuma | **Aprovado** (ordenação por `publishedAt`, elegibilidade Brasil/AMER) | **Sim — Validada em rede real** |
| `JOOBLE_API` | Termos configuráveis (`radar.jooble.termos-busca`) | **Sim** | Obrigatória: `JOOBLE_API_KEY` (presente via `.env`) | **Aprovado** (consultas reais, falha 401/403, rejeição de HTTP 200 sem `jobs`, rotação de termos) | **Sim — Validada em rede real autenticada** (20 vagas técnicas importadas no PostgreSQL 17, 0 erros) |
| `ADZUNA_API` | Termos configuráveis (`radar.adzuna.termos-busca`) + `sort_by=date` | **Sim** | Obrigatória: `ADZUNA_APP_ID` e `ADZUNA_APP_KEY` (presentes via `.env`) | **Aprovado** (consultas reais, `sort_by=date`, falha 401/403, rejeição de HTTP 200 sem `results`) | **Sim — Validada em rede real autenticada** (24 anúncios recebidos, 20 vagas técnicas importadas no PostgreSQL 17, 0 erros) |
| `LEVER_BOARD` | `exemplo-lever` (inativa por padrão) | **Sim** | Nenhuma (exige slug de empresa ativa no Lever) | **Aprovado** | **Não** — empresas BR testadas retornaram HTTP 404 por migração de ATS (`EXPERIMENTAL_ATIVAVEL`) |
| `JSEARCH_API` | `tecnologia-brasil` | **Não** | Desnecessária nesta rodada (`JSEARCH_API_KEY` vazia) | **Aprovado** (retorna `NAO_IMPLEMENTADA`) | **Não aplicável** (`NAO_IMPLEMENTADA`) |
| `SOLIDES_PORTAL` | `vagas.solides.com.br` | **Não** | Sem API pública documentada | Avaliado | **Bloqueada** (`BLOQUEADA_RESTRICAO_ACESSO` — HTML sem JSON estruturado) |
| `LINKEDIN` | `linkedin-jobs` | **Não** | Parceria comercial | Avaliado | **Bloqueada** (`BLOQUEADA_RESTRICAO_ACESSO` — Termos de Uso `a1341387`) |
| `INDEED_GLASSDOOR` | `indeed-glassdoor-br` | **Não** | Sem API aberta gratuita | Avaliado | **Bloqueada** (`BLOQUEADA_RESTRICAO_ACESSO` — Cloudflare / desafio anti-bot) |

---

## 4. Verificações Executadas

1. **Testes Automatizados do Backend no Java 25 (`cd backend; .\mvnw.cmd clean test`):**
   - **20 testes executados e aprovados (0 falhas, 0 erros)** no Java `25.0.3`:
     - `GitHubClassificadorESegurancaTest` (5 testes, incluindo preservação e idempotência de URL codificada `https://empresa.example/jobs/123?redirect=a%2Fb&utm_source=teste` sem transformar `%2F` em `%252F`).
     - `ColetaECatalogoComportamentoTest` (6 testes).
     - `MultiplasFontesEAgrupamentoTest` (2 testes).
     - `ValidacaoCredenciaisEPaginacaoRegressaoTest` (6 testes cobrindo paginação truncada de 20 itens na página 1 + retomada da vaga de 17/11 na página 2, presença de chave sem marcar fonte validada e preservação de `FALHA_AUTENTICACAO` após reinício, rejeição de HTTP 200 malformado sem array `jobs`/`results`, consultas reais configuráveis com `sort_by=date`, fonte saudável coexistindo com fonte desatualizada sinalizando `Atualização parcial/degradada`, priorização de recência e rotação de blocos no Greenhouse, `JSEARCH_API` como `NAO_IMPLEMENTADA` e carregamento de `.env` sem expor segredos).
     - `RadarVagasBackendApplicationTests` (1 teste).
2. **Migrações Flyway (`V1`, `V2`, `V3`) no PostgreSQL 17 (`radar-vagas-postgres` em `127.0.0.1:5433`):**
   - Migração `V3__continuacao_paginacao_e_validacao.sql` validada junto ao schema JPA (`ddl-auto: validate`).
3. **Instalação Limpa, Testes e Build do Frontend (`cd frontend; npm ci --no-audit --no-fund; npm test; npm run build`):**
   - `npm ci --no-audit --no-fund` concluído sem erros com lockfile consistente (`@emnapi/core@1.11.3` e `@emnapi/runtime@1.11.3` presentes).
   - Testes unitários/comportamentais Vitest aprovados (3 suítes/testes em `app.component.spec.ts`) e build de produção Angular gerado com sucesso.

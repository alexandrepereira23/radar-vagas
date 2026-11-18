# Radar de Vagas — Tecnologia no Brasil

Aplicação local para reunir vagas reais e recentes de tecnologia no Brasil a partir de múltiplas fontes, com rastreabilidade de origem, agrupamento conservador, datas honestas e acesso direto à candidatura original.

---

## Fontes Integradas e Registro de Cobertura (Etapa 2)

| Fonte / Fornecedor | Categoria | Estado Atual | Método de Acesso | Credencial |
| --- | --- | --- | --- | --- |
| **GitHub Issues** (`backend-br/vagas`, `frontendbr/vagas`, `qa-brasil/vagas`, `datascience-br/vagas`, `DevOps-Brasil/Vagas`) | Comunidades BR | **Ativa e Validada** | API REST Oficial (`/repos/{owner}/{repo}/issues`) | Opcional (`GITHUB_TOKEN` amplia de 60 para 5.000 req/h) |
| **Gupy Portal Brasil** (`GUPY_PORTAL`) | Portal de Busca Ampla | **Ativa e Validada** | HTTP GET em `portal.gupy.io/job-search/term=...` (extração de JSON estruturado SSR `__NEXT_DATA__` sem navegador automatizado) | Nenhuma |
| **Greenhouse Job Board API** (`quintoandar`, `ebanx`, `vtex`, `wildlifestudios`) | Murais de Empresas | **Ativa e Validada** | API REST Oficial (`boards-api.greenhouse.io/v1/boards/{empresa}/jobs?content=true`) | Nenhuma |
| **Ashby Public Job Posting API** (`supabase`) | Murais de Empresas | **Ativa e Validada** | API REST Oficial (`api.ashbyhq.com/posting-api/job-board/{empresa}`) | Nenhuma |
| **Jooble Brasil** (`JOOBLE_API`) | Agregador | **Bloqueada por Credencial** | Conector implementado (`POST https://br.jooble.org/api/{key}`) | `JOOBLE_API_KEY` (ausente no ambiente) |
| **Adzuna Brasil** (`ADZUNA_API`) | Agregador | **Bloqueada por Credencial** | Conector implementado (`GET https://api.adzuna.com/v1/api/jobs/br/search/1`) | `ADZUNA_APP_ID` e `ADZUNA_APP_KEY` (ausentes no ambiente) |
| **JSearch / OpenWebNinja** (`JSEARCH_API`) | Agregador | **Bloqueada por Credencial** | Registrada para ativação mediante chave | `JSEARCH_API_KEY` (ausente no ambiente) |
| **Lever Postings API** (`LEVER_BOARD`) | Murais de Empresas | **Experimental / Inativa** | Conector implementado (`GET https://api.lever.co/v0/postings/{site}?mode=json`) | Nenhuma (requer identificar empresa BR ativa no Lever) |
| **Sólides Portal** (`SOLIDES_PORTAL`) | Portal Experimental | **Bloqueada por Restrição Técnica** | HTML inicial de `vagas.solides.com.br` não expõe JSON estruturado de vagas nem API pública documentada | — |
| **LinkedIn Vagas** (`LINKEDIN`) | Portal Experimental | **Bloqueada por Termos de Uso** | Automação/scraping vedados pelos Termos do LinkedIn (`a1341387`) | — |
| **Indeed / Glassdoor** (`INDEED_GLASSDOOR`) | Portal Experimental | **Bloqueada por Proteção Anti-Bot** | Protegido por desafio anti-bot/Cloudflare; o projeto não contorna CAPTCHAs ou controles de acesso | — |

---

## Arquitetura e Stack

| Camada | Tecnologia e Versão Fixada |
| --- | --- |
| **Backend** | Java 25 LTS + Spring Boot `4.1.2` (WebMVC, Data JPA, Validation, Flyway) |
| **Banco de Dados** | PostgreSQL `17.12-alpine` via Docker Compose em `127.0.0.1:5433` + migrações Flyway (`V1`, `V2`) |
| **Frontend** | Angular `21.2.26` (`@angular/cli` `21.2.14`, TypeScript `5.9.3`, Vitest `4.0.8`) |
| **Segurança** | Bind exclusivo em `127.0.0.1`, sanitização de HTML, bloqueio de URLs locais/privadas e redaction de segredos |

---

## 1. Como Configurar e Credenciais Opcionais

1. (Opcional) Copie [.env.example](file:///C:/Users/Alexandre_HP/Documents/radar-vagas/.env.example) para `.env` ou defina variáveis de ambiente no terminal antes de subir o backend:
   ```powershell
   $env:GITHUB_TOKEN = "seu_token_github_opcional"
   $env:JOOBLE_API_KEY = "sua_chave_jooble_opcional"
   $env:ADZUNA_APP_ID = "seu_app_id_adzuna_opcional"
   $env:ADZUNA_APP_KEY = "sua_app_key_adzuna_opcional"
   ```

### Guia de Credenciais Opcionais e Limitações Conhecidas

- **`GITHUB_TOKEN` (Opcional — GitHub Issues):**
  - **Funcionamento sem token:** ativo por padrão (limite público de 60 requisições/hora por IP).
  - **Com token:** amplia o limite para 5.000 requisições/hora. Pode ser gerado gratuitamente em `https://github.com/settings/tokens` (Personal Access Token sem escopos de escrita).
- **`JOOBLE_API_KEY` (Opcional — Agregador Jooble Brasil):**
  - O conector [JoobleConector.java](file:///C:/Users/Alexandre_HP/Documents/radar-vagas/backend/src/main/java/br/com/radarvagas/coleta/JoobleConector.java) já está implementado.
  - Sem a variável `JOOBLE_API_KEY`, a fonte permanece marcada explicitamente como `BLOQUEADA_CREDENCIAL`.
  - Quando a chave (solicitada gratuitamente em `https://br.jooble.org/api/about`) é definida no ambiente, o sistema ativa a fonte automaticamente na inicialização.
- **`ADZUNA_APP_ID` e `ADZUNA_APP_KEY` (Opcional — Agregador Adzuna Brasil):**
  - O conector [AdzunaConector.java](file:///C:/Users/Alexandre_HP/Documents/radar-vagas/backend/src/main/java/br/com/radarvagas/coleta/AdzunaConector.java) já está implementado para o endpoint `/v1/api/jobs/br/search/1`.
  - Sem as duas variáveis, permanece marcado como `BLOQUEADA_CREDENCIAL`. Ao defini-las (obtidas em `https://developer.adzuna.com/overview`), a fonte passa a ativa automaticamente.
- **`JSEARCH_API_KEY` (Agregador JSearch / OpenWebNinja):**
  - Registrado como `BLOQUEADA_CREDENCIAL` por exigir assinatura/chave em `https://www.openwebninja.com/api/jsearch` (conforme o PRD, nenhum plano pago foi contratado).
- **Limitações de Fontes Experimentais e Murais (`LEVER_BOARD`, `SOLIDES_PORTAL`, `LINKEDIN`, `INDEED_GLASSDOOR`):**
  - **Murais de Empresas (`GREENHOUSE_BOARD`, `ASHBY_BOARD`, `LEVER_BOARD`):** exigem identificar o identificador (`slug`) de cada empresa; não representam uma busca global automática. No caso do Lever ([LeverConector.java](file:///C:/Users/Alexandre_HP/Documents/radar-vagas/backend/src/main/java/br/com/radarvagas/coleta/LeverConector.java)), o conector está implementado, mas as empresas brasileiras testadas migraram de ATS (retornando `HTTP 404`), ficando como `EXPERIMENTAL_ATIVAVEL`.
  - **Sólides (`SOLIDES_PORTAL`):** o portal `https://vagas.solides.com.br/` utiliza Next.js App Router sem expor JSON estruturado de vagas no HTML inicial e sem API pública documentada para candidatos (`BLOQUEADA_RESTRICAO_ACESSO`).
  - **LinkedIn (`LINKEDIN`):** bloqueado por conformidade com os Termos de Uso (`https://www.linkedin.com/help/linkedin/answer/a1341387`), que proíbem scraping e coleta automatizada sem parceria comercial (`BLOQUEADA_RESTRICAO_ACESSO`).
  - **Indeed e Glassdoor (`INDEED_GLASSDOOR` / JobSpy):** bloqueados por utilizarem proteção anti-bot / Cloudflare Turnstile nas páginas públicas; conforme o PRD, o sistema não contorna CAPTCHAs, bloqueios ou controles de acesso (`BLOQUEADA_RESTRICAO_ACESSO`).

---

## 2. Como Iniciar o Ambiente Local

### Passo 2.1 — Subir o PostgreSQL com Docker Compose

```powershell
docker compose up -d
docker compose ps
```

> O container `radar-vagas-postgres` escuta em `127.0.0.1:5433` para não conflitar com instalações locais do PostgreSQL na porta `5432`.

### Passo 2.2 — Iniciar o Backend (`http://127.0.0.1:8080`)

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

### Passo 2.3 — Iniciar o Frontend (`http://127.0.0.1:4200`)

```powershell
cd frontend
npm start
```

Acesse `http://127.0.0.1:4200` no navegador.

---

## 3. Como Coletar e Consultar as Vagas

- **Pela interface web (`http://127.0.0.1:4200`):**
  1. Clique em **"Coletar agora"** para executar a coleta em todas as fontes ativas (`GITHUB_ISSUES`, `GUPY_PORTAL`, `GREENHOUSE_BOARD`, `ASHBY_BOARD`).
  2. Clique em **"Ver fontes, bloqueios e coletas"** para inspecionar o quadro completo de conectores ativos, experimentais e bloqueados, além das métricas de cada coleta.
  3. Filtre por **Fonte de origem**, **Busca textual**, **Data de publicação** (conhecida vs desconhecida), **Indisponíveis na origem** ou **Bancos de talentos**.

- **Via linha de comando (API REST):**

  ```powershell
  # 1. Consultar registro de todas as fontes (ativas, experimentais e bloqueadas) e últimas execuções
  Invoke-RestMethod -Uri "http://127.0.0.1:8080/api/coletas/status" | ConvertTo-Json -Depth 5

  # 2. Disparar coleta manual nas fontes ativas
  Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:8080/api/coletas/executar" | ConvertTo-Json -Depth 5

  # 3. Consultar vagas paginadas (todas as fontes ou filtrando por fonte específica)
  Invoke-RestMethod -Uri "http://127.0.0.1:8080/api/vagas?page=0&size=15" | ConvertTo-Json -Depth 5
  Invoke-RestMethod -Uri "http://127.0.0.1:8080/api/vagas?page=0&size=10&fonte=GUPY_PORTAL" | ConvertTo-Json -Depth 5
  Invoke-RestMethod -Uri "http://127.0.0.1:8080/api/vagas?page=0&size=10&fonte=GREENHOUSE_BOARD" | ConvertTo-Json -Depth 5
  ```

---

## 4. Como Executar os Testes e o Build

```powershell
# Backend (13 testes de comportamento + empacotamento JAR)
cd backend
.\mvnw.cmd test
.\mvnw.cmd package -DskipTests

# Frontend (testes de comportamento Vitest + build de produção)
cd ..\frontend
npm test
npm run build
```

# Radar de Vagas — Tecnologia no Brasil

Aplicação local para reunir vagas reais e recentes de tecnologia no Brasil a partir de múltiplas fontes, com rastreabilidade de origem, agrupamento conservador, datas honestas e acesso direto à candidatura original.

---

## Quadro de Fontes: Implementação, Testes Locais e Validação Real

O sistema separa explicitamente **conector implementado**, **configuração/credencial presente**, **teste automatizado local** e **coleta real validada no fornecedor**:

| Fonte / Fornecedor | Conector Implementado? | Credencial Necessária | Teste Local (Servidor Simulado) | Coleta Real no Fornecedor | Estado Inicial sem Coleta Real |
| --- | --- | --- | --- | --- | --- |
| **GitHub Issues** (`backend-br/vagas`, `frontendbr/vagas`, `qa-brasil/vagas`, `datascience-br/vagas`, `DevOps-Brasil/Vagas`) | **Sim** ([GitHubIssuesConector.java](file:///C:/Users/Alexandre_HP/Documents/radar-vagas/backend/src/main/java/br/com/radarvagas/coleta/GitHubIssuesConector.java)) | Opcional: `GITHUB_TOKEN` (amplia de 60 para 5.000 req/h) | Aprovado (paginação truncada, retomada, rate limit, deduplicação) | **Validada em rede real** nos 5 repositórios (autenticada com `GITHUB_TOKEN`, `rateLimitRestante: 4995`) | `CONFIGURADA_NAO_VALIDADA` até a 1ª coleta com sucesso (`ATIVA_VALIDADA`) |
| **Gupy Portal Brasil** (`GUPY_PORTAL`) | **Sim** ([GupyPortalConector.java](file:///C:/Users/Alexandre_HP/Documents/radar-vagas/backend/src/main/java/br/com/radarvagas/coleta/GupyPortalConector.java)) | Nenhuma (público) | Aprovado (extração SSR `__NEXT_DATA__`, priorização por recência, rotação de termos) | **Validada em rede real** | `CONFIGURADA_NAO_VALIDADA` até a 1ª coleta com sucesso (`ATIVA_VALIDADA`) |
| **Greenhouse Job Board API** (`quintoandar`, `ebanx`, `vtex`, `wildlifestudios`) | **Sim** ([GreenhouseConector.java](file:///C:/Users/Alexandre_HP/Documents/radar-vagas/backend/src/main/java/br/com/radarvagas/coleta/GreenhouseConector.java)) | Nenhuma (API pública oficial) | Aprovado (priorização por recência, rotação de blocos por limite, filtro técnico BR) | **Validada em rede real** nas 4 empresas | `CONFIGURADA_NAO_VALIDADA` até a 1ª coleta com sucesso (`ATIVA_VALIDADA`) |
| **Ashby Public Job Posting API** (`supabase`) | **Sim** ([AshbyConector.java](file:///C:/Users/Alexandre_HP/Documents/radar-vagas/backend/src/main/java/br/com/radarvagas/coleta/AshbyConector.java)) | Nenhuma (API pública oficial) | Aprovado (priorização por recência, rotação de blocos, elegibilidade Brasil/AMER) | **Validada em rede real** | `CONFIGURADA_NAO_VALIDADA` até a 1ª coleta com sucesso (`ATIVA_VALIDADA`) |
| **Jooble Brasil** (`JOOBLE_API`) | **Sim** ([JoobleConector.java](file:///C:/Users/Alexandre_HP/Documents/radar-vagas/backend/src/main/java/br/com/radarvagas/coleta/JoobleConector.java)) | Obrigatória: `JOOBLE_API_KEY` | Aprovado (termos reais configuráveis, rejeição de HTTP 401/403 e HTTP 200 malformado sem `jobs`) | **Validada em rede real** com `JOOBLE_API_KEY` local (20 vagas técnicas importadas, 0 erros) | `BLOQUEADA_CREDENCIAL` sem chave; `CONFIGURADA_NAO_VALIDADA` com chave até coleta real ter sucesso (`ATIVA_VALIDADA`) |
| **Adzuna Brasil** (`ADZUNA_API`) | **Sim** ([AdzunaConector.java](file:///C:/Users/Alexandre_HP/Documents/radar-vagas/backend/src/main/java/br/com/radarvagas/coleta/AdzunaConector.java)) | Obrigatória: `ADZUNA_APP_ID` e `ADZUNA_APP_KEY` | Aprovado (termos configuráveis, `sort_by=date`, rejeição de 401/403 e HTTP 200 sem `results`) | **Validada em rede real** com `ADZUNA_APP_ID`/`ADZUNA_APP_KEY` locais (24 recebidas, 20 vagas técnicas importadas, 0 erros) | `BLOQUEADA_CREDENCIAL` sem chaves; `CONFIGURADA_NAO_VALIDADA` com chaves até coleta real ter sucesso (`ATIVA_VALIDADA`) |
| **Lever Postings API** (`LEVER_BOARD`) | **Sim** ([LeverConector.java](file:///C:/Users/Alexandre_HP/Documents/radar-vagas/backend/src/main/java/br/com/radarvagas/coleta/LeverConector.java)) | Nenhuma (requer slug de empresa ativa no Lever) | Aprovado | Empresas BR testadas retornaram HTTP 404 por migração de ATS | `EXPERIMENTAL_ATIVAVEL` (inativa até configurar slug válido) |
| **JSearch / OpenWebNinja** (`JSEARCH_API`) | **Não implementado** | Desnecessária nesta rodada (deixar `JSEARCH_API_KEY` vazia; não contratar plano) | Registro de fonte `NAO_IMPLEMENTADA` testado | Não aplicável (sem conector implementado) | `NAO_IMPLEMENTADA` |
| **Sólides Portal** (`SOLIDES_PORTAL`) | Não (bloqueio técnico) | Sem API pública documentada | Avaliado | HTML inicial não expõe `__NEXT_DATA__` nem JSON estruturado | `BLOQUEADA_RESTRICAO_ACESSO` |
| **LinkedIn Vagas** (`LINKEDIN`) | Não (termos de uso) | Parceria comercial | Avaliado | Automação proibida pelos Termos do LinkedIn (`a1341387`) | `BLOQUEADA_RESTRICAO_ACESSO` |
| **Indeed / Glassdoor** (`INDEED_GLASSDOOR`) | Não (proteção anti-bot) | Sem API aberta gratuita | Avaliado | Protegido por Cloudflare/desafio anti-bot (proibido contornar CAPTCHA) | `BLOQUEADA_RESTRICAO_ACESSO` |

---

## Arquitetura e Stack

| Camada | Tecnologia e Versão Fixada |
| --- | --- |
| **Backend** | Java 25 LTS + Spring Boot `4.1.2` (WebMVC, Data JPA, Validation, Flyway) |
| **Banco de Dados** | PostgreSQL `17.12-alpine` via Docker Compose em `127.0.0.1:5433` + migrações Flyway (`V1`, `V2`, `V3`) |
| **Frontend** | Angular `21.2.26` (`@angular/cli` `21.2.14`, TypeScript `5.9.3`, Vitest `4.0.8`) |
| **Segurança** | Bind exclusivo em `127.0.0.1`, sanitização de HTML, preservação idempotente de URLs codificadas, bloqueio de URLs locais/privadas e redaction de segredos |

---

## 1. Carregamento Local de Configuração (`.env`) e Credenciais

### Como o `.env` é carregado pelo Backend

O arquivo [application.yml](file:///C:/Users/Alexandre_HP/Documents/radar-vagas/backend/src/main/resources/application.yml) configura nativamente o Spring Boot para importar o arquivo `.env` no formato `KEY=value`:

```yaml
spring:
  config:
    import:
      - optional:file:./.env[.properties]
      - optional:file:../.env[.properties]
```

Isso garante carregamento único e previsível independentemente do diretório de trabalho:
- **Executando a partir de `backend/`** (`cd backend; .\mvnw.cmd spring-boot:run`): o Spring Boot lê automaticamente `../.env` (na raiz do repositório).
- **Executando a partir da raiz do projeto** (`.\backend\mvnw.cmd -f backend/pom.xml spring-boot:run`): o Spring Boot lê automaticamente `./.env` (na raiz do repositório).
- O arquivo `.env` permanece listado no [.gitignore](file:///C:/Users/Alexandre_HP/Documents/radar-vagas/.gitignore) e seus valores nunca são exibidos nos logs nem na API `/api/coletas/status`.

### Passo a passo para configurar o `.env` localmente

1. Na raiz do projeto, crie `.env` a partir de [.env.example](file:///C:/Users/Alexandre_HP/Documents/radar-vagas/.env.example) caso ainda não exista:
   ```powershell
   if (-not (Test-Path .env)) { Copy-Item .env.example .env }
   ```
2. Edite o arquivo `.env` localmente (nunca cole segredos no chat nem em commits):
   - **`GITHUB_TOKEN` (Opcional — GitHub Issues):**
     - Crie um *Fine-grained personal access token* em `https://github.com/settings/personal-access-tokens/new` com acesso somente a repositórios públicos (*Public repositories*), sem permissões de escrita. Amplia o limite de 60 req/h para 5.000 req/h.
   - **`JOOBLE_API_KEY` (Agregador Jooble Brasil):**
     - Solicite a chave gratuita para o domínio brasileiro em `https://br.jooble.org/api/about` (cota gratuita informada pela documentação: 100 requisições/dia).
   - **`ADZUNA_APP_ID` e `ADZUNA_APP_KEY` (Agregador Adzuna Brasil):**
     - Cadastre uma aplicação gratuita em `https://developer.adzuna.com/signup` e copie `app_id` e `app_key` do painel. Para preservar as chamadas de teste da conta durante a validação, mantenha `RADAR_COLETA_AGENDAMENTO_ATIVO=false` no `.env` e dispare a coleta manualmente apenas para a fonte desejada.
   - **`JSEARCH_API_KEY`:**
     - **Deixe vazia.** O conector JSearch ainda não foi implementado nesta versão; preencher esta variável não ativa a fonte. Não contrate assinatura paga.

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

> Reinicie o backend sempre que alterar chaves no arquivo `.env` da raiz.

### Passo 2.3 — Instalar e Iniciar o Frontend (`http://127.0.0.1:4200`)

```powershell
cd frontend
npm ci
npm start
```

Acesse `http://127.0.0.1:4200` no navegador.

---

## 3. Como Coletar e Verificar se Cada Integração Funcionou

A presença de uma chave no `.env` apenas **habilita** a fonte com estado `CONFIGURADA_NAO_VALIDADA`. Uma fonte só passa para `ATIVA_VALIDADA` após uma execução de coleta retornar HTTP 2xx com a estrutura JSON esperada (`jobs` no Jooble, `results` no Adzuna, array JSON no GitHub, etc.).

- **Pela interface web (`http://127.0.0.1:4200`):**
  1. Clique em **"Ver fontes, bloqueios e coletas"**.
  2. Verifique a coluna **Validação / Saúde**:
     - `Configurada (aguarda coleta)`: credencial carregada do `.env`, aguardando a primeira execução.
     - `Validada por coleta`: acesso e estrutura comprovados com timestamp de último sucesso.
     - `Erro de autenticação/chave`: fornecedor rejeitou a credencial (`HTTP 401/403`); verifique a chave no `.env`.
     - `Falha na última coleta` / `Limite atingido`: erro de rede, HTTP 5xx, payload HTTP 200 malformado ou rate limit (`HTTP 429`).
     - `Conector não implementado`: exibido para `JSEARCH_API`.
  3. Para testar apenas um fornecedor recém-configurado (ex.: Jooble ou Adzuna) sem gastar cota das outras fontes, clique no botão **"Coletar esta fonte"** na linha correspondente da tabela de diagnóstico.

- **Via linha de comando (API REST):**

  ```powershell
  # 1. Consultar status global, saúde individual por fonte e se a credencial foi carregada (credencialConfigurada: true/false)
  Invoke-RestMethod -Uri "http://127.0.0.1:8080/api/coletas/status" | ConvertTo-Json -Depth 5

  # 2. Disparar coleta manual em uma fonte específica pelo ID (ex.: testar apenas Jooble ou Adzuna)
  Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:8080/api/coletas/executar/1" | ConvertTo-Json -Depth 5

  # 3. Disparar coleta manual em todas as fontes ativas
  Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:8080/api/coletas/executar" | ConvertTo-Json -Depth 5

  # 4. Consultar vagas paginadas
  Invoke-RestMethod -Uri "http://127.0.0.1:8080/api/vagas?page=0&size=15" | ConvertTo-Json -Depth 5
  ```

---

## 4. Como Executar os Testes Automatizados e o Build

Os testes automatizados utilizam servidores HTTP locais efêmeros e banco em memória (`@ActiveProfiles("test")`, com `spring.config.import` isolado), sem disparar chamadas externas pagas nem consumir cotas de fornecedores:

```powershell
# Backend (20 testes de comportamento e regressão no Java 25 + empacotamento JAR)
cd backend
.\mvnw.cmd clean test
.\mvnw.cmd package -DskipTests

# Frontend (instalação limpa reproduzível + testes Vitest + build de produção)
cd ..\frontend
npm ci --no-audit --no-fund
npm test
npm run build
```

# Radar de Vagas — Tecnologia no Brasil

Aplicação local para reunir vagas de tecnologia no Brasil, com anúncios de origem rastreáveis, agrupamento conservador, datas honestas e candidatura no site de origem. O catálogo é consultado no banco local; abrir a listagem não dispara coleta externa.

## Stack e execução

| Camada | Versão |
| --- | --- |
| Backend | Java 25, Spring Boot `4.1.2`, Maven Wrapper |
| Banco | PostgreSQL `17.12-alpine`, Flyway V1–V4 |
| Frontend | Angular `21.2.26`, CLI `21.2.14`, TypeScript `5.9.3`, Vitest `4.0.8` |

Na raiz, com Docker ativo:

```powershell
docker compose up -d
docker compose ps
```

O PostgreSQL do Compose atende em `127.0.0.1:5433`. Em um terminal com `JAVA_HOME` apontando para um JDK 25:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Em outro terminal:

```powershell
cd frontend
npm ci --no-audit --no-fund
npm start
```

Interface: `http://127.0.0.1:4200`. API: `http://127.0.0.1:8080`.

### Configuração local

Use [.env.example](.env.example) como referência. O backend importa opcionalmente `./.env` e `../.env` como properties, permitindo iniciar da raiz ou de `backend/`. Variáveis de ambiente também são aceitas. Reinicie o backend após alterar a configuração.

- GitHub: `GITHUB_TOKEN` opcional.
- Jooble: `JOOBLE_API_KEY`.
- Adzuna: `ADZUNA_APP_ID` e `ADZUNA_APP_KEY`.
- JSearch: não implementado; preencher `JSEARCH_API_KEY` não ativa a fonte.
- `RADAR_COLETA_AGENDAMENTO_ATIVO=false` desativa o agendamento para uso exclusivamente manual.

Credencial presente apenas habilita a tentativa (`CONFIGURADA_NAO_VALIDADA`). A validação depende de uma resposta válida coletada e persistida; falhas de autenticação, limite e execução ficam registradas entre reinícios. A API expõe apenas `credencialConfigurada`, nunca o valor da chave.

## Fontes e alcance

| Fonte | Implementação e recorte atual | Evidência desta revisão |
| --- | --- | --- |
| GitHub Issues | 5 comunidades configuradas; paginação real e janela incremental persistidas | Testes HTTP locais de paginação, retomada, idempotência e rate limit |
| Adzuna Brasil | `/search/{page}`, `count`, `results_per_page`, ordenação por data e janela recente | Documentação oficial consultada; páginas seguintes e retomada verificadas com HTTP local |
| Jooble Brasil | `page`, `ResultOnPage`, `totalCount`; profundidade limitada | Páginas seguintes e retomada verificadas com HTTP local; documentação pública retornou 403 nesta rodada; paginação autenticada real ainda não revalidada |
| Gupy Portal | Somente lista SSR inicial de cada termo (`__NEXT_DATA__`); rotação de termos e consumo da sobra persistidos | HTML público acessível; mecanismo de paginação público e permitido **não validado**; sempre informa coleta parcial |
| Greenhouse, Ashby, Lever | Murais por empresa, com limites locais e rotação de blocos | Regressões existentes preservadas; não representam busca global |
| JSearch | Não implementado | Estado `NAO_IMPLEMENTADA` testado |
| Sólides, LinkedIn, Indeed/Glassdoor | Sem conector; bloqueios previamente registrados | Sem nova validação de acesso nesta revisão |

O histórico anterior registra coletas reais de GitHub, Gupy, Greenhouse, Ashby, Jooble e Adzuna. **Não foram repetidas nesta revisão**, que não utilizou credenciais reais. Testes simulados não comprovam cobertura nem acesso autenticado atual. Veja [STATUS.md](STATUS.md).

## Continuação, cotas e critérios de parada

A migração [V4](backend/src/main/resources/db/migration/V4__estado_busca_paginada.sql) acrescenta `configuracao_fonte.estado_busca`. Para Adzuna, Jooble e Gupy, esse estado contém a lista de termos do ciclo, índice do termo, página, tamanho de página, início do ciclo, itens ainda não consumidos e indicação de recorte.

- O corte de `max-itens-por-busca` guarda a **sobra da resposta recebida**, inclusive no último/único termo. A próxima execução consome essa sobra antes de consultar outra página. Ela não depende de reencontrar o mesmo prefixo numa resposta que pode ter mudado.
- O estado guarda somente a página pendente, sem credenciais; é substituído à medida que avança e removido ao encerrar o ciclo. Não há arquivo histórico de respostas. Uma execução que apenas consome a sobra pode ter anúncios válidos com zero novos anúncios recebidos por HTTP.
- `pagina_continuacao` passa a representar a página do termo atual; o termo e a posição restante ficam em `estado_busca`. Estados legados V3 sem JSON são interpretados como índice do termo para a primeira retomada.
- Alterar termos ou tamanho de página durante uma busca não invalida a resposta já recebida: a lista e o tamanho persistidos valem até terminar esse ciclo.
- Limite de volume, termos ou requisições adia o trabalho com `SUCESSO_PARCIAL`. Erro HTTP mantém a página que falhou. Falha de persistência conserva o estado anterior para reexecução idempotente; anúncios já gravados não são recriados. A ausência temporária de credencial também preserva os itens adiados.
- Adzuna usa `count`; Jooble usa `totalCount`. Uma contagem ausente/inconsistente, resposta acima do tamanho solicitado ou teto de profundidade produz recorte explícito. Sem contagem, uma página cheia permite prosseguir dentro da cota, mas não comprova completude.
- O teto de páginas por termo encerra **o recorte configurado** e passa ao próximo termo, mantendo o ciclo parcial. Ele não agenda a importação de todo o histórico. O próximo ciclo começa da primeira página.
- Conclusão significa percorrer os resultados indicados pela fonte para os termos/janela configurados, sem sobra nem recorte conhecido. Paginação por número não é snapshot transacional do fornecedor: inserções/remoções entre páginas podem mover resultados. Novos ciclos se sobrepõem e a identidade de origem no banco evita duplicação, mas não há garantia de cobertura integral de um feed mutável.
- Gupy sempre retorna `SUCESSO_PARCIAL`, mesmo após consumir toda a lista inicial. A ordenação local por `publishedDate` prioriza somente os anúncios dessa resposta. Sem sobra, aguarda a cadência normal para um novo ciclo; não inventa páginas nem contorna bloqueios.

### Limites padrão configuráveis

| Configuração | Adzuna | Jooble | Gupy |
| --- | --- | --- | --- |
| `max-itens-por-busca` (válidos únicos por execução) | 20 | 20 | 20 |
| `itens-por-pagina` (independente do orçamento local) | 20 (até 50) | 20 (até 100) | Lista SSR do portal |
| `max-termos-por-execucao` | 2 | 3 | 2 |
| `max-paginas-por-execucao` (requisições HTTP) | 2 | 3 | Não há paginação validada |
| `max-paginas-por-termo` (profundidade do recorte) | 5 | 5 | Apenas a lista inicial |
| Janela de publicação | `janela-dias=7` via `max_days_old` | Janela/ordenação recente não validada | Janela/ordenação global não validada |

Prefixos Spring: `radar.adzuna`, `radar.jooble`, `radar.gupy`. As variáveis correspondentes estão em [application.yml](backend/src/main/resources/application.yml): `RADAR_ADZUNA_MAX_ITENS`, `RADAR_ADZUNA_ITENS_POR_PAGINA`, `RADAR_ADZUNA_MAX_TERMOS_POR_EXECUCAO`, `RADAR_ADZUNA_MAX_PAGINAS`, `RADAR_ADZUNA_MAX_PAGINAS_POR_TERMO`, `RADAR_ADZUNA_JANELA_DIAS` e equivalentes `RADAR_JOOBLE_*` (exceto janela). Gupy usa `RADAR_GUPY_MAX_ITENS` e `RADAR_GUPY_MAX_TERMOS_POR_EXECUCAO`; o antigo `RADAR_GUPY_MAX_PAGINAS` é aceito como fallback de **termos**, não como paginação.

A janela Adzuna é relativa ao momento da requisição; durante uma retomada, a idade máxima aumenta pelos dias completos decorridos desde o início do ciclo. A API não oferece snapshot fixo nesta integração. Termos são configuráveis via `radar.<fonte>.termos-busca`.

## Coletar e consultar

Na interface, abra **Ver fontes, bloqueios e coletas** para consultar saúde e executar uma fonte individualmente. A saúde global considera falhas, fontes desatualizadas e continuação pendente, inclusive na página 1.

```powershell
Invoke-RestMethod "http://127.0.0.1:8080/api/coletas/status" | ConvertTo-Json -Depth 5
# Substitua 1 pelo ID informado no status para coletar somente a fonte desejada.
Invoke-RestMethod -Method Post "http://127.0.0.1:8080/api/coletas/executar/1"
Invoke-RestMethod "http://127.0.0.1:8080/api/vagas?page=0&size=15"
```

### Filtros e recência

A tela abre em **Últimos 7 dias**, sem restringir área, nível, modalidade, fonte ou candidatura, e exclui ocultas. **Limpar filtros** volta a essa seleção. Use **Qualquer data** para incluir publicações desconhecidas, ou **Somente sem data conhecida** para procurar só essas vagas. A interface mostra **Encontrada em** (não “Publicada em”) quando a fonte não informou a publicação. Horários são exibidos em **America/Sao_Paulo**.

Na API `GET /api/vagas`, os filtros são combinados com AND no banco antes de paginar:

| Parâmetro | Valores e comportamento |
| --- | --- |
| `page`, `size` | Página a partir de zero; padrão `0` e `15`, tamanho limitado a `1–50`. |
| `q`, `localizacao` | Busca livre em cargo, empresa, tecnologia e localização; `localizacao` busca somente no texto de localização armazenado, sem distinguir maiúsculas. |
| `area`, `senioridade`, `modalidade` | Códigos disponíveis em `GET /api/vagas/opcoes`, incluindo `NAO_INFORMADA`. Omitir para não restringir. |
| `tecnologia` | Token completo, sem distinguir maiúsculas; `Java` não casa com `JavaScript`. Para `C++` e `C#`, codifique como `C%2B%2B` e `C%23` na URL. |
| `fonte` | Código do fornecedor; também encontra anúncios secundários sem duplicar vagas. Omitir ou usar `TODAS` para não restringir. |
| `publicacao` | `TODAS` (padrão da API), `CONHECIDA`, `DESCONHECIDA`, `ULTIMAS_24H`, `ULTIMOS_3_DIAS`, `ULTIMOS_7_DIAS`. |
| `candidatura` | `TODAS` (padrão), `NAO_CANDIDATADAS`, `CANDIDATADAS`. |
| `ocultacao` | `EXCLUIR` (padrão), `INCLUIR`, `SOMENTE`. |
| `incluirIndisponiveis`, `bancoTalentos` | Booleanos, ambos `false` por padrão; bancos de talentos continuam separados das vagas abertas. |

Em modos recentes, a publicação precisa estar entre `agora − 24/72/168 horas` e `agora`, incluindo ambas as bordas. A API usa instantes UTC e captura um único `agora` por consulta; datas futuras, desconhecidas e datas de coleta não contam como publicação recente. `GET /api/vagas/opcoes` retorna opções do catálogo inteiro, mesmo quando uma listagem filtrada não encontra vagas.

```powershell
Invoke-RestMethod 'http://127.0.0.1:8080/api/vagas?publicacao=ULTIMOS_7_DIAS&modalidade=REMOTO&tecnologia=C%2B%2B&page=0&size=15'
Invoke-RestMethod 'http://127.0.0.1:8080/api/vagas?publicacao=DESCONHECIDA&ocultacao=SOMENTE'
```

### Acompanhar candidaturas e recuperar ocultas

**Abrir candidatura** só navega para o site de origem. Use **Já me candidatei** para confirmar manualmente e **Desfazer marcação** para reverter. Para recuperar uma vaga, selecione **Somente ocultas** em **Visibilidade**, amplie a publicação para **Qualquer data** se necessário, selecione a vaga e clique em **Restaurar**. Ocultar ou restaurar não muda o estado da candidatura.

```powershell
# Substitua 123 pelo ID exibido pela API.
Invoke-RestMethod -Method Patch 'http://127.0.0.1:8080/api/vagas/123/acao-pessoal' -ContentType 'application/json' -Body '{"jaMeCandidatei":true}'
Invoke-RestMethod -Method Patch 'http://127.0.0.1:8080/api/vagas/123/acao-pessoal' -ContentType 'application/json' -Body '{"oculta":false}'
```

O PATCH aceita um ou ambos os campos como booleanos JSON; omitir um campo preserva seu estado. A resposta contém `jaMeCandidatei`, `oculta`, `candidatadoEm`, `ocultadoEm` e `atualizadoEm`. Repetir um estado não muda suas datas; desfazer limpa a data correspondente. `GET` não cria ações. Entradas inválidas retornam `400` com `detail`, e alterar uma vaga inexistente retorna `404`. Esses controles são para uso local; não exponha a API sem proteção.

## Testes e build

Os testes usam H2 e servidores HTTP locais efêmeros. O comando abaixo carrega diretamente a configuração de teste, sem importar o `.env` do projeto. Propriedades e fontes persistidas são reconstruídas antes de cada método de coleta.

```powershell
# Em backend/, com JAVA_HOME apontando para Java 25
$env:SPRING_CONFIG_LOCATION = 'classpath:/application-test.yml'
.\mvnw.cmd -B clean package '-Dsurefire.runOrder=reversealphabetical'
Remove-Item Env:SPRING_CONFIG_LOCATION

# Em frontend/
npm ci
npm test
npm run build
```

Verificado na Etapa 3: **36 testes backend aprovados** no H2 em ordem inversa (Java **25.0.3**), JAR executável, **7 testes frontend aprovados** e bundle de produção gerado. A integração em **PostgreSQL 17.5** também passou com dados sintéticos: Flyway V1–V4, upgrade V3→V4 e ações persistidas após recarga, recoleta e reinício da API. Veja [STATUS.md](STATUS.md) para as evidências e pendências, incluindo a revalidação da paginação autenticada real de Adzuna e Jooble.

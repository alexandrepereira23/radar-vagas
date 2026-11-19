# STATUS — Radar de Vagas

Última atualização: 19/11/2026 — revisão da Etapa 3, atualização do detalhe após coleta.

## Etapa atual

- **Etapa 1:** entrega funcional preservada (catálogo, API, frontend, GitHub, persistência idempotente).
- **Etapa 2:** correções locais da revisão 02 implementadas e verificadas. A V4 foi revalidada no PostgreSQL 17.5 durante a Etapa 3; a paginação autenticada real de Adzuna/Jooble continua pendente. Gupy permanece um recorte experimental, com paginação não validada e status parcial explícito.
- **Etapa 3:** filtros combináveis no catálogo inteiro, recência UTC, opções de filtro do catálogo e ações pessoais independentes, reversíveis e persistidas. Na tela, **Visibilidade → Somente ocultas → Restaurar** permite recuperar vagas; amplie o período para **Qualquer data** se necessário.
- **Etapa 4:** não iniciada; nenhuma publicação ou alteração na VPS.

## Etapa 3 — contrato e preservação

- `GET /api/vagas` mantém a paginação, a ordenação e os filtros antigos; passa a excluir ocultas por padrão. Os filtros novos são `area`, `senioridade`, `modalidade`, `localizacao`, `tecnologia` (token literal), `candidatura` e `ocultacao`. `GET /api/vagas/opcoes` fornece classificações e tecnologias do catálogo, sem limitar à página atual.
- `publicacao=ULTIMAS_24H|ULTIMOS_3_DIAS|ULTIMOS_7_DIAS` usa janelas corridas e inclusivas em UTC, sem trazer datas futuras ou desconhecidas. O default de 7 dias é só da tela; a API mantém `TODAS` por padrão e aceita `DESCONHECIDA`. Exemplo: `/api/vagas?publicacao=DESCONHECIDA&ocultacao=SOMENTE`. A descoberta não é apresentada como publicação; a interface formata horários em São Paulo.
- `PATCH /api/vagas/{id}/acao-pessoal` recebe estados explícitos, por exemplo `{"jaMeCandidatei":true}`. Usa a tabela existente, sem migração nova; repetição não altera a data original, e abrir o link da vaga não altera o estado.
- V1–V4, os coletores, a configuração de fontes e os limites aprovados permanecem intactos. Veja o [contrato e os comandos](README.md#filtros-e-recência).

### Verificações da Etapa 3

| Verificação | Resultado |
| --- | --- |
| Java **25.0.3**, `clean package` com classes em ordem inversa | **36 testes aprovados** no H2, incluindo os 30 anteriores; Flyway V1–V4, validação JPA e JAR executável. |
| `npm ci`, `npm test`, `npm run build` | Instalação e build concluídos; **7 testes aprovados**. O audit do npm sinalizou 27 vulnerabilidades nas dependências; não houve atualização de versões nesta etapa. |
| Chromium/Playwright com API simulada | Marcar, ocultar e restaurar; layout conferido em 360 e 1440 px, sem rolagem horizontal. |
| PostgreSQL **17.5** descartável | Flyway V1–V4 aplicadas e validadas em banco vazio; no upgrade V3→V4, somente a V4 foi aplicada e o registro de fonte existente foi preservado. |
| Chrome, Angular, API Spring e PostgreSQL 17.5 | Filtros combinados, resultado vazio, limpeza, abertura de link sem marcar, marcação após recarga, ocultação, filtro de ocultas e restauração verificados. Após recoleta e reinício da API, a oportunidade manteve o ID e a marcação no banco. Layout lado a lado a 1440×960 e empilhado a 360×800, sem rolagem horizontal. |

Os seis testes novos do backend cobrem filtros antes da paginação, origem secundária sem duplicatas, limites inclusivos das três janelas, tokens de tecnologia com pontuação, estados ausentes, ações independentes e concorrentes, nova ingestão e entradas inválidas. O `.env` do projeto não foi lido; a regressão de importação usa seu próprio arquivo temporário com valores fictícios. Nenhuma coleta autenticada real foi chamada.

A integração usou duas vagas sintéticas de um servidor GitHub local: a primeira coleta importou duas vagas; a recoleta retornou duas repetidas, zero novas e zero erros. O teste final usou as classes e dependências extraídas do JAR, após timeouts intermitentes com `java -jar`. Os serviços descartáveis foram encerrados. Logs, resultado, trace e capturas locais estão em `%LOCALAPPDATA%\Temp\opencode\radar-etapa3-it-51648`.

### Revisão da Etapa 3 — detalhe após coleta

- Antes, quando a vaga permanecia na página, a tela mesclava o novo resumo com o detalhe antigo. Isso podia atualizar o cargo sem atualizar a descrição e os anúncios de origem.
- As coletas geral e individual agora invalidam o detalhe pelo mesmo caminho. Se a vaga ainda estiver na página, a seleção é mantida e o detalhe completo é buscado novamente na API. Em caso de falha, o painel mostra o erro sem manter conteúdo antigo; selecionar o card novamente permite tentar de novo.
- As requisições de detalhe obsoletas continuam sendo canceladas ao trocar de vaga ou ao confirmar uma ação pessoal, evitando que a resposta anterior sobrescreva o estado mais recente.
- **Verificado nesta revisão:** antes da correção, os quatro casos novos falharam pela ausência do GET de detalhe, e os sete existentes passaram. Depois, `npm test` passou com **11 testes** e `npm run build` gerou o bundle de produção. Os testes usam respostas HTTP simuladas. Backend, PostgreSQL e fornecedores reais não foram revalidados nesta revisão, que alterou apenas frontend e documentação.

## Histórico: correções da revisão 02

### P1 — Anúncios descartados pelo corte de volume

- `BuscaPaginada` centraliza o consumo limitado para Adzuna, Jooble e Gupy.
- V4 adiciona `configuracao_fonte.estado_busca` (TEXT): termos do ciclo, índice do termo, página real, tamanho de página estável, início, sobra da resposta e marca de recorte.
- O último/único termo não é considerado concluído enquanto há sobra. Os itens adiados são consumidos do estado persistido antes de outra consulta HTTP; mudanças de ordem/conteúdo na resposta futura não apagam esses itens já recebidos.
- Estado só avança após a persistência dos anúncios sem erros. Erros de gravação conservam o estado anterior para reexecução idempotente; ausência de credencial e estado ilegível preservam a continuação. Falha HTTP numa página posterior guarda a página que falhou.
- A saúde/cadência reconhece continuação também na página 1. A identidade por fornecedor, mural e ID externo e as restrições existentes continuam impedindo a recriação de anúncios/oportunidades.

### P2 — Rotação de termos versus páginas

- Adzuna consulta `/search/{page}` com tamanho independente do orçamento local, `count`, `sort_by=date` e `max_days_old` (padrão 7 dias). O tamanho e os termos do ciclo persistido prevalecem durante a retomada.
- Jooble envia `page` e `ResultOnPage` e usa `totalCount`. Paginação verificada com servidor HTTP local; janela recente e ordenação no fornecedor não estão validadas.
- Cotas distintas por execução: anúncios válidos únicos, termos e requisições/páginas. Um teto de profundidade por termo (padrão 5 páginas) encerra o recorte configurado com resultado parcial; não promete percorrer todo o histórico.
- Contagens ausentes/inconsistentes ou tamanho acima do solicitado mantêm a incompletude explícita. Sem contagem, páginas cheias permitem prosseguir até o teto, sem provar conclusão integral.
- Gupy mantém somente o SSR inicial acessível. O nome de configuração passa a `max-termos-por-execucao` (alias legado mantido), sem apresentar rotação como paginação. A fonte sempre registra `SUCESSO_PARCIAL`; ordenação local não prova recência global.
- Paginação numérica não oferece snapshot transacional das fontes. Sobreposição entre ciclos e deduplicação reduzem repetição, mas não demonstram cobertura integral de respostas mutáveis. A janela Adzuna usa a idade relativa suportada pelo fornecedor, acrescida dos dias completos de espera na retomada.

### P2 — Isolamento de testes

- `ColetaTestFixture` reconstrói todos os objetos aninhados de `RadarProperties` a partir do ambiente de teste antes de cada método.
- Limpa ações, anúncios, oportunidades, execuções e **todas** as configurações de fonte; recria somente as fontes da fixture. Continuação e propriedades de classes anteriores não vazam.
- A asserção de exatamente duas execuções GitHub foi preservada. A suíte foi executada com ordem inversa, colocando a classe que deixava fontes extras antes da classe afetada, e esta também passou isoladamente.

## Histórico: verificações executadas na revisão 02

Ambiente: Windows, Temurin **25.0.5+8**, Maven Wrapper **3.10.1**, Node **20.20.0**, npm **10.8.2**. O JDK 25 foi extraído temporariamente e selecionado por `JAVA_HOME` nos comandos; `pom.xml` continua declarando Java 25, sem overrides de versão.

| Verificação | Resultado |
| --- | --- |
| Backend: `.\mvnw.cmd -B test` na primeira versão das correções | 28 testes aprovados |
| Backend final: `.\mvnw.cmd -B clean test -Dsurefire.runOrder=reversealphabetical` | **30 testes, 0 falhas, 0 erros** |
| `.\mvnw.cmd -B test -Dtest=ColetaECatalogoComportamentoTest` | **6 testes, 0 falhas, 0 erros** |
| `.\mvnw.cmd -B package -DskipTests` | JAR executável gerado no Java 25 |
| Flyway V1, V2, V3 e V4 + `ddl-auto: validate` | Aprovados no H2 dos testes |
| `npm ci --no-audit --no-fund` | 480 pacotes instalados, sem erro |
| `npm test` | 3 testes aprovados |
| `npm run build` | Bundle Angular de produção gerado |
| PostgreSQL 17/Compose | **Não executado**: daemon Docker indisponível; nenhuma escuta em `127.0.0.1:5433` |

Os 10 testes de `AgregadoresRetomadaTest` cobrem os quatro anúncios Adzuna com orçamento de três e retomada por nova instância após recarregar o estado do banco; mudança da resposta entre execuções; sobra do único termo Jooble/Gupy; páginas seguintes dos agregadores; tamanho estável e parâmetro de janela Adzuna; HTTP 429 na página 2; deduplicação entre termos/reexecuções; teto de profundidade/contagem ausente; credencial temporariamente ausente; estado ilegível preservado. Os 20 testes anteriores seguem aprovados.

Todos os testes de coleta usam HTTP local e chaves fictícias. Nenhuma credencial real foi solicitada, lida ou impressa, nem houve consulta paga a agregadores.

## Histórico: evidência de fontes e limites da revisão 02

- Consultadas as páginas públicas [Adzuna Search](https://developer.adzuna.com/docs/search) e a [especificação OpenAPI vinculada pela documentação](https://developer.adzuna.com/swagger/spec/test2.json), confirmando página, tamanho, contagem e `max_days_old`.
- As páginas `https://br.jooble.org/api/about` e `https://jooble.org/api/about` retornaram **HTTP 403**. O contrato de paginação está testado localmente; aceitação real de `ResultOnPage`, comportamento das contagens e ordenação precisam ser revalidados sem confundir fixtures com coleta real.
- `https://portal.gupy.io/job-search/term=desenvolvedor` retornou HTML público. Não foi comprovado mecanismo de paginação acessível **e permitido** para este conector; não foram usados endpoints internos, navegador automatizado ou contorno de controles. Limitação específica preservada no resultado da coleta.
- O ambiente tem serviço PostgreSQL **18** local; ele não foi utilizado como substituto da validação solicitada em PostgreSQL **17**, nem foram alterados seus bancos.
- O STATUS anterior relatava coletas reais em GitHub (5 repositórios autenticados, saldo 4995), Gupy, Greenhouse (4 empresas), Ashby e agregadores (Jooble: 20 vagas; Adzuna: 24 recebidas/20 válidas), com PostgreSQL 17. Isso é **evidência histórica registrada pelo autor**, não repetida nesta rodada e não prova a nova paginação real.
- Mantidas as correções aprovadas: importação explícita de `.env`, GitHub com janela/continuação V3, credencial separada de validação, saúde por fonte, JSearch não implementada e normalização idempotente de URLs codificadas.

## Próximas verificações pendentes

1. Revalidar paginação Adzuna/Jooble em coleta real limitada, observando contagens e cotas; os testes atuais não consomem credenciais reais.
2. Manter Gupy explicitamente parcial até haver mecanismo público permitido e verificado. Não declarar cobertura ou busca completa a partir da lista inicial.

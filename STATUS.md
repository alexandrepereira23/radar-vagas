# STATUS — Radar de Vagas

Última atualização: 18/11/2026 — revisão 02, retomada e paginação dos agregadores.

## Etapa atual

- **Etapa 1:** entrega funcional preservada (catálogo, API, frontend, GitHub, persistência idempotente).
- **Etapa 2:** correções locais da revisão 02 implementadas e verificadas. A paginação autenticada real de Adzuna/Jooble e a migração V4 no PostgreSQL 17 ainda precisam de revalidação. Gupy permanece um recorte experimental, com paginação não validada e status parcial explícito.
- **Etapas 3 e 4:** pendentes. Filtros combináveis completos, ações pessoais na API/tela e implantação não foram adicionados nesta revisão.

## Correções desta revisão

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

## Verificações efetivamente executadas nesta rodada

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

## Evidência externa e limites

- Consultadas as páginas públicas [Adzuna Search](https://developer.adzuna.com/docs/search) e a [especificação OpenAPI vinculada pela documentação](https://developer.adzuna.com/swagger/spec/test2.json), confirmando página, tamanho, contagem e `max_days_old`.
- As páginas `https://br.jooble.org/api/about` e `https://jooble.org/api/about` retornaram **HTTP 403**. O contrato de paginação está testado localmente; aceitação real de `ResultOnPage`, comportamento das contagens e ordenação precisam ser revalidados sem confundir fixtures com coleta real.
- `https://portal.gupy.io/job-search/term=desenvolvedor` retornou HTML público. Não foi comprovado mecanismo de paginação acessível **e permitido** para este conector; não foram usados endpoints internos, navegador automatizado ou contorno de controles. Limitação específica preservada no resultado da coleta.
- O ambiente tem serviço PostgreSQL **18** local; ele não foi utilizado como substituto da validação solicitada em PostgreSQL **17**, nem foram alterados seus bancos.
- O STATUS anterior relatava coletas reais em GitHub (5 repositórios autenticados, saldo 4995), Gupy, Greenhouse (4 empresas), Ashby e agregadores (Jooble: 20 vagas; Adzuna: 24 recebidas/20 válidas), com PostgreSQL 17. Isso é **evidência histórica registrada pelo autor**, não repetida nesta rodada e não prova a nova paginação real.
- Mantidas as correções aprovadas: importação explícita de `.env`, GitHub com janela/continuação V3, credencial separada de validação, saúde por fonte, JSearch não implementada e normalização idempotente de URLs codificadas.

## Próximas verificações pendentes

1. Com PostgreSQL 17 disponível, aplicar/validar V1–V4 e conferir o schema JPA tanto em banco limpo quanto em atualização V3 → V4.
2. Revalidar paginação Adzuna/Jooble em coleta real limitada, observando contagens e cotas; os testes atuais não consomem credenciais reais.
3. Manter Gupy explicitamente parcial até haver mecanismo público permitido e verificado. Não declarar cobertura ou busca completa a partir da lista inicial.

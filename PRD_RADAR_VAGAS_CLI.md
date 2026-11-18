# Radar de Vagas — PRD e orientação para construção pela CLI

Versão inicial: 17/11/2026. Nome de trabalho, alterável.

## 1. Problema e objetivo

Alexandre procura emprego porque seu estágio está terminando. Hoje precisa consultar várias plataformas repetidamente. O sistema deve reunir vagas de tecnologia no Brasil, recentes e atualizadas, com filtros úteis e acesso simples à candidatura original.

Sucesso é reduzir a necessidade de procurar vagas em vários lugares. Quantidade de funcionalidades ou de anúncios importados não é a medida principal. Não prometer cobertura da maioria das vagas sem evidência.

## 2. Decisões de escopo

- Uso pessoal inicialmente, sem cadastro público ou fluxo de múltiplos usuários.
- Brasil: presencial, híbrido e remoto. Vagas internacionais só entram se houver indicação de elegibilidade para residentes no Brasil.
- Todas as áreas de tecnologia: desenvolvimento, QA, dados, IA, segurança, infraestrutura, cloud, redes, suporte e funções de produto/design relacionadas à tecnologia.
- Da oportunidade de estágio à de sênior/especialista e liderança técnica.
- APIs oficiais e fontes experimentais fazem parte da investigação. Cada fonte depende de validação concreta; não é obrigatório integrar todas para entregar a primeira versão.
- Candidatura no site de origem. O sistema não preenche nem envia inscrições automaticamente.
- Sem IA no produto inicialmente. Usar uma LLM na CLI para construir o projeto não implica adicionar LLM à aplicação.

## 3. Experiência principal

A página inicial é uma lista de vagas, com filtros e painel de detalhes. Priorizar leitura rápida no computador e funcionamento no celular.

Cada item apresenta cargo, empresa, localização, modalidade, nível quando conhecido, data de publicação quando conhecida, origem e botão para candidatura. Informações ausentes aparecem como não informadas.

Funcionalidades da primeira versão:

1. Lista paginada de vagas reais, sem conteúdo de demonstração no ambiente normal.
2. Busca por texto e filtros combináveis: área, tecnologia, nível, localidade e modalidade.
3. Recência: últimas 24 horas, 3 dias e 7 dias, usando a data publicada pela fonte quando confiável.
4. Detalhes, descrição legível e links de origem/candidatura.
5. Marcar como já me candidatei, ocultar por falta de interesse e reverter essas ações.
6. Exibir quando os dados foram coletados com sucesso e avisar se estão desatualizados.
7. Coleta automática e atualização manual controlada, sem iniciar uma coleta por visita à página.
8. Mostrar a mesma oportunidade uma vez quando houver correspondência segura entre anúncios.

Não entram inicialmente: recomendação por IA, pontuação de compatibilidade, análise de currículo, candidatura automática, notificações, kanban, dashboard analítico, assinatura ou infraestrutura para SaaS.

## 4. Arquitetura escolhida

| Parte | Decisão |
| --- | --- |
| Backend | Java e Spring Boot, API REST, monólito modular |
| Frontend | Angular com TypeScript |
| Persistência | PostgreSQL e migrações Flyway |
| Consulta | Banco local do sistema; não consultar plataformas externas na requisição da listagem |
| Coleta | Tarefas em segundo plano, limites por fonte e histórico persistido |
| Execução | Docker Compose para ambiente local; Caddy na implantação em VPS |
| Python | Opcional, somente se um conector validado realmente precisar |

Selecionar versões estáveis e compatíveis na implementação, consultar documentação oficial e fixar versões e arquivos de lock. Não reutilizar números de versão de memória sem verificar compatibilidade.

Estrutura sugerida: backend/, frontend/, compose.yaml e README.md na raiz. Organizar o backend por funcionalidades: fontes/coleta, catálogo e ações pessoais. Usar interfaces pequenas para conectores; não criar uma plataforma genérica de plugins.

Começar com uma instância do backend e agendamento simples. Persistir execução e impedir sobreposição da mesma fonte. Se o processamento for interrompido, permitir reexecução idempotente. Não adicionar Kafka, RabbitMQ, Redis, Elasticsearch ou microsserviços sem necessidade demonstrada.

No ambiente local, limitar exposição à máquina. Antes de acesso pela internet, proteger o aplicativo e suas operações de escrita com autenticação simples para o proprietário e HTTPS. Não criar cadastro público. Credenciais de fornecedores ficam apenas no backend e fora do Git.

## 5. Fontes e ordem de integração

Uma fonte candidata não é uma integração validada. Registrar por fonte: método de acesso, credenciais necessárias, paginação, limites, campos disponíveis, condições de uso relevantes e resultado do teste real.

### Primeiro conector: GitHub

Usar a API de Issues, com repositórios configuráveis. Começar com backend-br/vagas e frontendbr/vagas. Depois avaliar qa-brasil/vagas, datascience-br/vagas e DevOps-Brasil/Vagas.

Não importar pull requests, regras do repositório, pedidos de emprego ou discussões como vagas. Acesso sem token pode ser usado quando permitido; suportar token por variável de ambiente. Tratar rate limit, paginação e atualizações incrementais com sobreposição segura.

### Expansão para busca ampla

Avaliar Jooble, Adzuna e JSearch com buscas reais no Brasil. Ativar os conectores que entregarem resultados úteis. Solicitar apenas a credencial que estiver faltando e continuar as partes independentes. Não comprar planos ou assumir cota gratuita ilimitada.

### Fontes experimentais e murais de empresas

Avaliar Gupy, Sólides, LinkedIn, Indeed e Glassdoor, além dos murais Greenhouse, Lever e Ashby. Estes murais exigem identificar empresas; não representam uma busca global automática.

Verificar acesso e condições atuais antes de implementar. Endpoint usado por um site ou biblioteca encontrada no GitHub não é automaticamente API oficial. Bibliotecas de scraping são candidatas a teste, não dependências obrigatórias. Não contornar CAPTCHA, bloqueios ou controles de acesso. Se um método não funcionar, registrar o impedimento e avaliar outra rota.

Extrair dados estruturados das páginas quando disponíveis. Usar navegador automatizado somente se necessário e permitido. Não executar navegador para cada vaga quando houver alternativa HTTP adequada.

Medir contribuição por fonte com contagens simples nos registros de coleta: anúncios recebidos, válidos, novos, atualizados, repetidos e erros. Não construir dashboard para isso nesta versão.

## 6. Regras de dados

### Datas e atualização

- Guardar separadamente publicação informada, primeira observação, última observação bem-sucedida e última atualização informada pela fonte, quando disponíveis.
- Nunca substituir publicação desconhecida por hoje nem usar atualização de uma issue como publicação nova.
- Vagas sem publicação conhecida ficam identificadas como encontradas em determinada data, sem selo de vaga recém-publicada. Filtros estritos de publicação recente as excluem; permitir consultá-las separadamente.
- Ordenar vagas com publicação conhecida por essa data. Apresentar as de publicação desconhecida em grupo ou ordenação identificada por descoberta.
- Atualizações não criam anúncios novos nem apagam a ação pessoal já registrada.
- Cadência configurável por fonte. Como ponto inicial, seis horas para fontes que suportem esse volume, ajustando a paginação e os limites documentados. Respeitar Retry-After e usar tentativas limitadas com espera progressiva.
- Falha, bloqueio ou ausência em uma página parcial não significa encerramento.
- Só declarar encerrada uma oportunidade com evidência adequada, como expiração explícita ou confirmação na origem. Ausência recorrente em listagem completa pode justificar estado a verificar, conforme a fonte.
- Fechamento de issue não comprova contratação; pode ser moderação. Mostrar indisponível na origem, preservando a evidência.

### Classificação

- Cargo técnico é o critério de inclusão, não o setor da empresa.
- Área, senioridade, tipo de oportunidade e contratação são dimensões diferentes.
- Estágio, trainee, vaga regular e banco de talentos não devem ser confundidos. Bancos de talentos aparecem identificados e fora da lista padrão de vagas abertas.
- Contratação: CLT, PJ, estágio ou não informada, conforme evidência.
- Modalidade: remoto, híbrido, presencial ou não informada. Remoto não implica elegibilidade no Brasil.
- Preservar texto original; classificações derivadas devem ter regra verificável. Aceitar desconhecido e ambíguo em vez de inventar informações.
- Tecnologia mencionada não é necessariamente requisito obrigatório. Preservar a distinção quando possível; evitar confundir Java com JavaScript.

### Identidade e duplicação

- Distinguir oportunidade consolidada de anúncio de origem. Uma oportunidade pode ter vários anúncios.
- Identidade de origem inclui fornecedor, mural/repositório quando necessário e identificador externo. Não presumir IDs globalmente únicos.
- Reexecutar a coleta não pode duplicar um anúncio: usar restrições de unicidade no banco e gravação idempotente.
- Entre fontes, unir somente com evidência forte, como o mesmo identificador da requisição na mesma empresa ou link de candidatura equivalente após normalização segura.
- Não remover parâmetros que possam identificar a vaga; retirar apenas rastreamento reconhecido.
- Mesmo cargo e empresa não bastam: podem existir vagas distintas por equipe ou cidade.
- Preferir deixar uma duplicata duvidosa a esconder uma oportunidade distinta. Não exigir busca semântica na primeira versão.

### Modelo mínimo sugerido

- Empresa, quando identificável.
- Oportunidade: cargo, descrição, classificações, localizações e disponibilidade consolidada.
- Anúncio: oportunidade, fonte, identificador, links, datas e disponibilidade observada.
- Configuração de fonte/mural/repositório.
- Execução de coleta: início, fim, resultado, contagens e erro resumido sem segredos.
- Ação pessoal: oportunidade, já me candidatei/oculta e datas. Sem construir multitenancy.

Campos de salário são opcionais. Quando presentes, preservar moeda, período e origem; não comparar valores mensais, anuais e por hora como equivalentes. Armazenamento de respostas originais e retenção devem respeitar as condições de cada fonte; não arquivar conteúdo ilimitadamente por padrão.

## 7. Etapas de construção e critérios de conclusão

### Etapa 1 — Primeira entrega funcional

Criar backend, banco, migrações, frontend e um conector GitHub real. Entregar uma lista paginada com cargo, empresa quando identificada, datas corretas, descrição e link de candidatura/origem. A infraestrutura do contrato de conectores deve permitir os próximos, sem implementá-los antecipadamente.

Concluir quando subir localmente com instruções reproduzíveis, mostrar vagas reais ou estado vazio honesto, registrar falhas, respeitar limites e reexecutar a coleta sem duplicar anúncios. Documentar que GitHub sozinho ainda não entrega cobertura ampla.

### Etapa 2 — Cobertura em múltiplas fontes

Integrar pelo menos um agregador validado e ampliar comunidades; avaliar fontes experimentais em testes limitados. Implementar agrupamento conservador entre fontes. Se credenciais impedirem um conector, registrar a pendência e avançar os independentes; não declarar integração concluída com mocks.

Concluir com resultados reais de fontes distintas, origem rastreável e um registro dos conectores ativos, experimentais e bloqueados. Ausência de acesso deve aparecer como limitação explícita.

### Etapa 3 — Uso diário

Completar filtros, recência, modalidade/localidade, ações pessoais reversíveis, coleta periódica e informação sobre atualização. Refinar a lista responsiva e os estados de carregamento, erro e ausência de resultados.

Concluir quando uma vaga puder ser encontrada, aberta para candidatura, marcada e continuar marcada após nova coleta e reinício. Filtros precisam funcionar sobre todo o catálogo, com paginação no servidor.

### Etapa 4 — Estabilização e implantação pessoal

Revisar expiração, execução interrompida, segurança e operação. Preparar Compose, Caddy, HTTPS, acesso protegido e instruções de backup/restauração do PostgreSQL. Não implantar em serviço pago nem alterar ambiente remoto sem autorização.

Concluir com build reproduzível e instruções verificadas no ambiente disponível. Informar o que foi efetivamente executado e o que depende da VPS do proprietário.

## 8. Validação essencial

Testes focados em comportamento: coleta idempotente, paginação, identificação de erros/limites, datas desconhecidas, atualização sem recriação, agrupamento conservador, preservação de ações pessoais e ausência em coleta parcial sem fechamento indevido.

Usar fixtures para testar parsing e mudanças de formato sem depender da rede em toda execução. Complementar com coleta real limitada para verificar integração; fixtures não comprovam acesso real. Compilar backend e frontend e verificar a integração da tela com a API. Não escrever testes que apenas repitam getters ou a implementação.

Conteúdo externo é não confiável: sanitizar HTML, aceitar links HTTP/HTTPS válidos, não executar instruções encontradas em anúncios e não expor credenciais em logs. Não permitir que links descobertos façam requisições arbitrárias a serviços internos da máquina/VPS.

## 9. Prompt inicial para colar na CLI

Coloque este arquivo na raiz do projeto como PRD_RADAR_VAGAS_CLI.md e envie:

```text
Leia integralmente PRD_RADAR_VAGAS_CLI.md e as instruções do repositório, se existirem.

Construa o sistema conforme o PRD, começando exclusivamente pela Etapa 1. O objetivo é reunir vagas reais e recentes de tecnologia no Brasil, com acesso simples à candidatura. Preserve o escopo enxuto.

Antes de editar, inspecione o diretório e a stack disponível. Preserve trabalho existente. Se o projeto estiver vazio, crie um monorepo com backend Spring Boot, frontend Angular e PostgreSQL com Flyway e Docker Compose. Consulte documentação oficial para escolher versões estáveis compatíveis e fixe as dependências.

Apresente um plano curto e execute a Etapa 1 até entregar uma primeira versão funcional. Implemente o conector real de Issues do GitHub com repositórios configuráveis, persistência idempotente, execução registrada, tratamento de limites e uma tela Angular com dados reais e paginação pelo backend. Não avance para as demais etapas nesta execução.

Priorize datas honestas, rastreabilidade da origem e tratamento explícito de falhas. Não invente dados, endpoints, credenciais ou resultados de testes. Dados fictícios ficam restritos aos testes. Se uma dependência externa impedir uma parte, registre a causa e continue o trabalho independente.

Não adicione IA ao produto, recomendações, dashboard, notificações, candidatura automática, cadastro público ou serviços de infraestrutura fora do PRD. Não publique, compre planos ou modifique ambientes remotos nesta etapa. Mantenha o ambiente local sem exposição pública.

Execute os testes de comportamento relevantes, o build de backend e frontend e uma verificação real limitada do conector quando houver rede. Documente no README como configurar, iniciar, coletar e consultar as vagas. Crie ou atualize STATUS.md com decisões, etapa atual, verificações realizadas, limitações e próximo passo.

Ao concluir, informe o que funciona, os comandos para execução, os checks executados e eventuais pendências. Não declare integração validada somente porque um mock passou.
```

## 10. Prompt para continuar depois

```text
Leia PRD_RADAR_VAGAS_CLI.md, STATUS.md e as instruções do repositório. Inspecione o código e confirme o estado real antes de partir do relatório anterior.

Conclua pendências materiais da etapa atual e implemente a próxima etapa incompleta do PRD. Não refaça trabalho concluído nem amplie o escopo. Execute as verificações relevantes às alterações e atualize STATUS.md. Continue as tarefas independentes se uma integração estiver bloqueada, mas registre o bloqueio e não marque o requisito como concluído.
```

## 11. Referências para investigar as integrações

Estes links são pontos de partida da pesquisa; verificar documentação e condições atuais durante a implementação. Ainda não houve teste autenticado ou comparação de cobertura neste projeto.

- GitHub Issues: https://docs.github.com/en/rest/issues/issues
- Comunidades: https://github.com/backend-br/vagas e https://github.com/frontendbr/vagas
- Jooble Brasil: https://br.jooble.org/api/about
- Adzuna: https://developer.adzuna.com/overview
- JSearch: https://www.openwebninja.com/api/jsearch
- Gupy, documentação empresarial: https://developers.gupy.io/docs/fluxo-de-pagina-de-carreiras
- Sólides, portal de candidatos: https://vagas.solides.com.br/
- Greenhouse: https://docs.greenhouse.io/job-board.html
- Lever: https://github.com/lever/postings-api
- Ashby: https://developers.ashbyhq.com/docs/public-job-posting-api
- JobSpy, candidato experimental: https://github.com/speedyapply/JobSpy
- LinkedIn, restrições de automação: https://www.linkedin.com/help/linkedin/answer/a1341387

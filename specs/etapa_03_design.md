# Etapa 3 — desenho técnico

- API: um predicado parametrizado compartilha filtros da página e da contagem. A publicação usa um único modo; intervalos inclusivos usam o `Clock` UTC existente. A consulta de opções traz códigos/rótulos dos enums e tecnologias do catálogo, independentemente dos filtros da lista.
- Escrita: `PATCH` recebe flags explícitas e valida tipos JSON sem coerção. A transação bloqueia a oportunidade antes de ler/criar sua ação única; só transições alteram datas. A coleta continua sem gravar ações. Não há migração nova.
- Interface: consulta/paginação no servidor e seleção protegida contra respostas antigas. Ações confirmam a resposta persistida e recarregam a consulta após salvar. Estados de erro são independentes; horário formatado com fuso São Paulo.
- Segurança: preservar execução local, sem novos usuários ou exposição pública; autenticação/HTTPS continuam pré-requisitos para publicação. Sem SQL concatenado com entradas, sem HTML de anúncios interpretado e sem alteração na validação de links externos.
- Verificação: testes de API, persistência e recência com relógio controlado; testes Angular de parâmetros, estados e requisições concorrentes; integração com dados descartáveis, nunca credenciais reais.

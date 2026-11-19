import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AppComponent } from './app.component';
import { AnuncioOrigem, EstadoAcaoPessoal, FonteConfigurada, OpcoesVagas, PaginaVagas, StatusColetaGlobal, VagaDetalhe, VagaResumo } from './models/vaga.model';

const opcoes: OpcoesVagas = {
  areas: [{ codigo: 'DESENVOLVIMENTO', rotulo: 'Desenvolvimento' }],
  senioridades: [{ codigo: 'SENIOR', rotulo: 'Sênior' }],
  modalidades: [{ codigo: 'REMOTO', rotulo: 'Remoto' }],
  tecnologias: ['Java', 'JavaScript', 'C++', 'C#', '.NET'],
  fontes: ['LEVER_BOARD'],
  catalogoVazio: false
};

describe('AppComponent — Comportamento da Tela Inicial e Integração com API', () => {
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('deve consultar status e listar vagas sem disparar coleta automaticamente ao abrir a página', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();

    const reqStatus = httpMock.expectOne('/api/coletas/status');
    expect(reqStatus.request.method).toBe('GET');

    const reqVagas = httpMock.expectOne(
      (req) => req.url === '/api/vagas' && req.params.get('page') === '0' && req.params.get('size') === '15'
    );
    expect(reqVagas.request.method).toBe('GET');
    expect(reqVagas.request.params.get('publicacao')).toBe('ULTIMOS_7_DIAS');
    expect(reqVagas.request.params.get('candidatura')).toBe('TODAS');
    expect(reqVagas.request.params.get('ocultacao')).toBe('EXCLUIR');
    responderOpcoes(true);

    // Garante que nenhuma requisição POST /api/coletas/executar foi feita na visita
    httpMock.expectNone('/api/coletas/executar');

    const statusPayload: StatusColetaGlobal = {
      ultimaColetaSucessoGlobalEm: null,
      dadosDesatualizados: true,
      motivoEstadoDados: 'Nenhuma coleta concluída ainda.',
      coletaEmAndamento: false,
      fontes: [],
      ultimasExecucoes: []
    };
    reqStatus.flush(statusPayload);

    const paginaVazia: PaginaVagas = {
      itens: [],
      pagina: 0,
      tamanhoPagina: 15,
      totalElementos: 0,
      totalPaginas: 0,
      temProxima: false,
      temAnterior: false
    };
    reqVagas.flush(paginaVazia);
    fixture.detectChanges();

    const compilado = fixture.nativeElement as HTMLElement;
    expect(compilado.querySelector('[data-testid="estado-vazio"]')?.textContent).toContain(
      'Nenhuma vaga encontrada no catálogo local'
    );
  });

  it('deve renderizar vagas com publicação conhecida e desconhecida honestamente e paginar pelo backend', async () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();

    httpMock.expectOne('/api/coletas/status').flush({
      ultimaColetaSucessoGlobalEm: '2026-11-17T19:00:00Z',
      dadosDesatualizados: false,
      motivoEstadoDados: 'Catálogo atualizado a partir do banco local.',
      coletaEmAndamento: false,
      fontes: [],
      ultimasExecucoes: []
    });
    responderOpcoes();
    consultaLista().flush(pagina());
    await fixture.whenStable();
    mudarCampo(fixture, '#select-publicacao', 'TODAS');
    elemento(fixture, 'btn-buscar').click();

    const pagina1: PaginaVagas = {
      itens: [
        {
          id: 101,
          cargo: 'Pessoa Desenvolvedora Java Sênior',
          empresa: null,
          localizacao: 'São Paulo/SP',
          modalidadeCodigo: 'REMOTO',
          modalidadeRotulo: 'Remoto',
          senioridadeCodigo: 'SENIOR',
          senioridadeRotulo: 'Sênior',
          regimeCodigo: 'CLT',
          regimeRotulo: 'CLT',
          areaCodigo: 'DESENVOLVIMENTO',
          areaRotulo: 'Desenvolvimento',
          tipoOportunidadeCodigo: 'VAGA_ABERTA',
          tipoOportunidadeRotulo: 'Vaga aberta',
          disponibilidadeCodigo: 'DISPONIVEL',
          disponibilidadeRotulo: 'Disponível',
          motivoDisponibilidade: null,
          tecnologias: ['Java', 'Spring Boot'],
          publicadoEm: null,
          publicacaoConhecida: false,
          primeiraObservacaoEm: '2026-11-17T18:00:00Z',
          ultimaObservacaoEm: '2026-11-17T19:00:00Z',
          atualizadoNaFonteEm: null,
          fontePrincipal: 'GITHUB_ISSUES',
          muralPrincipal: 'backend-br/vagas',
          identificadorExternoPrincipal: '9001',
          linkCandidatura: 'https://github.com/backend-br/vagas/issues/9001',
          linkOrigemPrincipal: 'https://github.com/backend-br/vagas/issues/9001',
          jaMeCandidatei: false,
          oculta: false
        }
      ],
      pagina: 0,
      tamanhoPagina: 15,
      totalElementos: 20,
      totalPaginas: 2,
      temProxima: true,
      temAnterior: false
    };

    const comData = { ...vaga(102), publicadoEm: '2020-11-17T01:00:00Z' };
    const lista = consultaLista(0);
    expect(lista.request.params.get('publicacao')).toBe('TODAS');
    lista.flush({ ...pagina1, itens: [comData, ...pagina1.itens] });
    // A lista do servidor vem com a publicação conhecida primeiro.
    detalhar(comData);
    fixture.detectChanges();
    (elDaFixture(fixture).querySelectorAll<HTMLButtonElement>('[data-testid="btn-ver-detalhe"]')[1]).click();

    const detalhe101: VagaDetalhe = {
      ...pagina1.itens[0],
      descricao: 'Atuação em microsserviços Java com PostgreSQL.',
      anunciosOrigem: [
        {
          id: 1,
          fornecedor: 'GITHUB_ISSUES',
          identificadorMural: 'backend-br/vagas',
          identificadorExterno: '9001',
          tituloOriginal: '[Remoto] Pessoa Desenvolvedora Java Sênior',
          labelsOriginais: ['Remoto', 'Sênior'],
          urlOrigem: 'https://github.com/backend-br/vagas/issues/9001',
          urlCandidatura: 'https://github.com/backend-br/vagas/issues/9001',
          disponibilidadeCodigo: 'DISPONIVEL',
          disponibilidadeRotulo: 'Disponível',
          motivoIndisponibilidade: null,
          publicadoNaFonteEm: null,
          atualizadoNaFonteEm: null,
          primeiraObservacaoEm: '2026-11-17T18:00:00Z',
          ultimaObservacaoEm: '2026-11-17T19:00:00Z'
        }
      ]
    };
    httpMock.expectOne('/api/vagas/101').flush(detalhe101);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    // Empresa ausente deve aparecer como 'Não informada'
    expect(el.textContent).toContain('Não informada');
    // Publicação desconhecida nunca recebe selo de recente e exibe aviso honesto
    expect(el.querySelector('[data-testid="selo-recente"]')).toBeNull();
    expect(el.querySelector('[data-testid="selo-publicacao-desconhecida"]')?.textContent).toContain(
      'Publicação não informada'
    );
    expect(el.querySelector('[data-testid="data-publicacao"]')?.textContent).toMatch(/16\/11\/2020.*22:00/);

    // Aciona próxima página no backend
    const btnProx = el.querySelector('[data-testid="btn-proxima-pagina"]') as HTMLButtonElement;
    expect(btnProx.disabled).toBe(false);
    btnProx.click();

    const reqPag2 = httpMock.expectOne((r) => r.url === '/api/vagas' && r.params.get('page') === '1');
    expect(reqPag2.request.method).toBe('GET');
    reqPag2.flush({
      ...pagina1,
      itens: [],
      pagina: 1,
      temProxima: false,
      temAnterior: true
    });
  });

  it('deve exibir diagnóstico separando fonte validada, configurada sem validação, falha de chave e conector não implementado', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    responderOpcoes();

    httpMock.expectOne('/api/coletas/status').flush({
      ultimaColetaSucessoGlobalEm: '2026-11-18T15:00:00Z',
      dadosDesatualizados: true,
      atualizacaoParcial: true,
      motivoEstadoDados: 'Atualização parcial/degradada: 1 de 2 fonte(s) ativa(s) em dia; 1 com falha (Jooble Brasil).',
      coletaEmAndamento: false,
      fontes: [
        {
          id: 1,
          fornecedor: 'GITHUB_ISSUES',
          identificadorMural: 'backend-br/vagas',
          nomeExibicao: 'Comunidade Backend Brasil',
          ativo: true,
          cadenciaMinutos: 360,
          estadoValidacao: 'ATIVA_VALIDADA',
          situacaoAtualizacao: 'ATUALIZADA',
          ultimaColetaSucessoEm: '2026-11-18T15:00:00Z',
          cursorUltimaAtualizacao: '2026-11-18T14:55:00Z'
        },
        {
          id: 2,
          fornecedor: 'JOOBLE_API',
          identificadorMural: 'tecnologia-brasil',
          nomeExibicao: 'Jooble Brasil',
          ativo: true,
          cadenciaMinutos: 360,
          estadoValidacao: 'FALHA_AUTENTICACAO',
          situacaoAtualizacao: 'COM_FALHA',
          observacaoValidacao: 'Falha de autenticação/autorização (HTTP 403) ao consultar Jooble API.',
          ultimaColetaSucessoEm: null,
          cursorUltimaAtualizacao: null
        },
        {
          id: 3,
          fornecedor: 'JSEARCH_API',
          identificadorMural: 'tecnologia-brasil',
          nomeExibicao: 'JSearch / OpenWebNinja',
          ativo: false,
          cadenciaMinutos: 360,
          estadoValidacao: 'NAO_IMPLEMENTADA',
          situacaoAtualizacao: 'INATIVA',
          observacaoValidacao: 'Conector ainda não implementado no código.',
          ultimaColetaSucessoEm: null,
          cursorUltimaAtualizacao: null
        }
      ],
      ultimasExecucoes: []
    });

    httpMock.expectOne((r) => r.url === '/api/vagas').flush({
      itens: [],
      pagina: 0,
      tamanhoPagina: 15,
      totalElementos: 0,
      totalPaginas: 0,
      temProxima: false,
      temAnterior: false
    });
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const banner = el.querySelector('[data-testid="status-coleta-banner"]') as HTMLElement;
    expect(banner.classList.contains('status-stale')).toBe(true);
    expect(banner.textContent).toContain('Atualização parcial/degradada');

    const btnDiag = el.querySelector('[data-testid="btn-diagnostico"]') as HTMLButtonElement;
    btnDiag.click();
    fixture.detectChanges();

    const painel = el.querySelector('[data-testid="painel-diagnostico"]') as HTMLElement;
    expect(painel.textContent).toContain('Validada por coleta');
    expect(painel.textContent).toContain('Erro de autenticação/chave');
    expect(painel.textContent).toContain('Conector não implementado');
  });

  function responderOpcoes(catalogoVazio = false): void {
    httpMock.expectOne('/api/vagas/opcoes').flush({ ...opcoes, catalogoVazio });
  }

  function consultaLista(page?: number) {
    return httpMock.expectOne((r) => r.url === '/api/vagas' && (page === undefined || r.params.get('page') === String(page)));
  }

  function pagina(itens: VagaResumo[] = [], page = 0, total = itens.length, size = 15): PaginaVagas {
    const totalPaginas = Math.ceil(total / size);
    return { itens, pagina: page, tamanhoPagina: size, totalElementos: total, totalPaginas,
      temProxima: page + 1 < totalPaginas, temAnterior: page > 0 };
  }

  function vaga(id: number): VagaResumo {
    return {
      id, cargo: `Vaga ${id}`, empresa: 'Empresa', localizacao: 'Recife/PE',
      modalidadeCodigo: 'REMOTO', modalidadeRotulo: 'Remoto', senioridadeCodigo: 'SENIOR', senioridadeRotulo: 'Sênior',
      regimeCodigo: 'CLT', regimeRotulo: 'CLT', areaCodigo: 'DESENVOLVIMENTO', areaRotulo: 'Desenvolvimento',
      tipoOportunidadeCodigo: 'VAGA_ABERTA', tipoOportunidadeRotulo: 'Vaga aberta', disponibilidadeCodigo: 'DISPONIVEL',
      disponibilidadeRotulo: 'Disponível', motivoDisponibilidade: null, tecnologias: ['Java'],
      publicadoEm: '2026-11-17T19:00:00Z', publicacaoConhecida: true,
      primeiraObservacaoEm: '2026-11-17T19:00:00Z', ultimaObservacaoEm: '2026-11-17T19:00:00Z', atualizadoNaFonteEm: null,
      fontePrincipal: 'GITHUB_ISSUES', muralPrincipal: 'backend-br/vagas', identificadorExternoPrincipal: String(id),
      linkCandidatura: 'https://example.invalid/candidatar', linkOrigemPrincipal: 'https://example.invalid/anuncio',
      jaMeCandidatei: false, oculta: false
    };
  }

  function detalhar(resumo: VagaResumo, descricao = `Descrição ${resumo.id}`, anunciosOrigem: AnuncioOrigem[] = []): void {
    const detalhe: VagaDetalhe = { ...resumo, descricao, anunciosOrigem };
    httpMock.expectOne(`/api/vagas/${resumo.id}`).flush(detalhe);
  }

  function elDaFixture(fixture: ComponentFixture<AppComponent>): HTMLElement {
    return fixture.nativeElement;
  }

  function elemento<T extends HTMLElement = HTMLButtonElement>(fixture: ComponentFixture<AppComponent>, testId: string): T {
    return elDaFixture(fixture).querySelector<T>(`[data-testid="${testId}"]`)!;
  }

  function mudarCampo(fixture: ComponentFixture<AppComponent>, seletor: string, valor: string | boolean): void {
    const controle = elDaFixture(fixture).querySelector<HTMLInputElement | HTMLSelectElement>(seletor)!;
    if (typeof valor === 'boolean') {
      (controle as HTMLInputElement).checked = valor;
    } else {
      controle.value = valor;
    }
    controle.dispatchEvent(new Event(controle instanceof HTMLInputElement && controle.type === 'text' ? 'input' : 'change'));
  }

  async function abrir(inicial: PaginaVagas = pagina(), fontes: FonteConfigurada[] = [], origens: AnuncioOrigem[] = []): Promise<ComponentFixture<AppComponent>> {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    httpMock.expectOne('/api/coletas/status').flush({ fontes, ultimasExecucoes: [] });
    responderOpcoes();
    consultaLista(0).flush(inicial);
    if (inicial.itens[0]) detalhar(inicial.itens[0], undefined, origens);
    fixture.detectChanges();
    await fixture.whenStable();
    return fixture;
  }

  it('combina filtros de catálogo, mantém os aplicados ao paginar e restaura os padrões ao limpar', async () => {
    const fixture = await abrir();
    expect(elemento(fixture, 'periodo-aplicado').textContent).toContain('Últimos 7 dias');
    expect(elemento(fixture, 'estado-vazio').textContent).toContain('Nenhuma vaga corresponde aos filtros');
    mudarCampo(fixture, '#input-busca', '  back-end  ');
    mudarCampo(fixture, '#select-fonte', 'LEVER_BOARD');
    mudarCampo(fixture, '#select-area', 'DESENVOLVIMENTO');
    mudarCampo(fixture, '#select-senioridade', 'SENIOR');
    mudarCampo(fixture, '#select-modalidade', 'REMOTO');
    mudarCampo(fixture, '#select-tecnologia', 'C++');
    mudarCampo(fixture, '#input-localizacao', 'Recife');
    mudarCampo(fixture, '#select-publicacao', 'DESCONHECIDA');
    mudarCampo(fixture, '#select-candidatura', 'CANDIDATADAS');
    mudarCampo(fixture, '#select-ocultacao', 'INCLUIR');
    mudarCampo(fixture, '[data-testid="check-indisponiveis"]', true);
    mudarCampo(fixture, '[data-testid="check-banco-talentos"]', true);
    httpMock.expectNone((r) => r.url === '/api/vagas');
    elemento(fixture, 'btn-buscar').click();

    const req = consultaLista(0);
    expect(Object.fromEntries(req.request.params.keys().map((key) => [key, req.request.params.get(key)]))).toEqual({
      page: '0', size: '15', q: 'back-end', fonte: 'LEVER_BOARD', area: 'DESENVOLVIMENTO', senioridade: 'SENIOR',
      modalidade: 'REMOTO', tecnologia: 'C++', localizacao: 'Recife', publicacao: 'DESCONHECIDA',
      candidatura: 'CANDIDATADAS', ocultacao: 'INCLUIR', incluirIndisponiveis: 'true', bancoTalentos: 'true'
    });
    expect(req.request.urlWithParams).toContain('tecnologia=C%2B%2B');
    const nova = { ...vaga(1), publicadoEm: null, publicacaoConhecida: false, jaMeCandidatei: true, tecnologias: ['C++'] };
    req.flush(pagina([nova], 0, 16));
    detalhar(nova);
    fixture.detectChanges();
    expect(elemento(fixture, 'periodo-aplicado').textContent).toContain('Somente sem data conhecida');
    expect(elemento(fixture, 'data-descoberta').textContent).toContain('Encontrada em');
    expect(elemento(fixture, 'resumo-paginacao').textContent).toContain('16');

    mudarCampo(fixture, '#input-busca', 'rascunho');
    elemento(fixture, 'btn-proxima-pagina').click();
    const pag2 = consultaLista(1);
    for (const key of req.request.params.keys().filter((key) => key !== 'page')) {
      expect(pag2.request.params.get(key)).toBe(req.request.params.get(key));
    }
    pag2.flush(pagina([], 1, 16));
    fixture.detectChanges();
    expect(elDaFixture(fixture).querySelector('#select-tecnologia option[value="C++"]')).not.toBeNull();

    elemento(fixture, 'btn-limpar').click();
    const limpa = consultaLista(0);
    expect(limpa.request.params.get('q')).toBeNull();
    expect(limpa.request.params.get('publicacao')).toBe('ULTIMOS_7_DIAS');
    expect(limpa.request.params.get('ocultacao')).toBe('EXCLUIR');
    limpa.flush(pagina());
  });

  const dataAcao = '2026-11-17T19:00:00Z';
  function estadoAcao(jaMeCandidatei: boolean, oculta: boolean): EstadoAcaoPessoal {
    return { jaMeCandidatei, oculta, atualizadoEm: dataAcao,
      candidatadoEm: jaMeCandidatei ? dataAcao : null, ocultadoEm: oculta ? dataAcao : null };
  }

  it('separa abrir link de marcar candidatura, mostra a confirmação, permite desfazer e mantém o estado em falha', async () => {
    const unica = vaga(1);
    const fixture = await abrir(pagina([unica]));
    const marcar = elemento(fixture, 'btn-marcar-candidatura');
    const ocultar = elemento(fixture, 'btn-ocultar-vaga');
    const link = elemento<HTMLAnchorElement>(fixture, 'btn-candidatura-detalhe');
    expect(link.rel).toBe('noopener noreferrer');
    link.dispatchEvent(new Event('click', { bubbles: true, cancelable: true }));
    httpMock.expectNone('/api/vagas/1/acao-pessoal');
    marcar.click();
    marcar.click();
    ocultar.click(); // mesmo antes de a renderização desabilitar os dois botões
    fixture.detectChanges();
    const acao = httpMock.expectOne('/api/vagas/1/acao-pessoal');
    expect(acao.request.method).toBe('PATCH');
    expect(acao.request.body).toEqual({ jaMeCandidatei: true });
    expect(marcar.disabled && ocultar.disabled).toBe(true);
    expect(elemento(fixture, 'salvando-acao')).not.toBeNull();
    acao.flush(estadoAcao(true, false));
    fixture.detectChanges();
    expect(elemento(fixture, 'candidatura-detalhe').textContent).toMatch(/Já me candidatei.*16:00/s);
    const marcada = { ...unica, jaMeCandidatei: true };
    consultaLista(0).flush(pagina([marcada]));
    fixture.detectChanges();
    expect(elemento(fixture, 'selo-candidatura').textContent).toContain('Já me candidatei');
    expect(marcar.textContent).toContain('Desfazer marcação');

    marcar.click();
    const falha = httpMock.expectOne('/api/vagas/1/acao-pessoal');
    expect(falha.request.body).toEqual({ jaMeCandidatei: false });
    falha.flush(null, { status: 500, statusText: 'Erro' });
    fixture.detectChanges();
    expect(elemento(fixture, 'erro-acao').textContent).toContain('Não foi possível salvar');
    expect(marcar.textContent).toContain('Desfazer marcação');
    expect(marcar.disabled).toBe(false);
    httpMock.expectNone((r) => r.url === '/api/vagas');

    marcar.click();
    httpMock.expectOne('/api/vagas/1/acao-pessoal').flush(estadoAcao(false, false));
    consultaLista(0).flush(pagina([unica]));
    fixture.detectChanges();
    expect(elemento(fixture, 'selo-candidatura')).toBeNull();
    expect(elemento(fixture, 'erro-acao')).toBeNull();
    expect(marcar.textContent).toContain('Já me candidatei');
  });

  it('reconsulta com filtros aplicados após ocultar a última vaga da página e restaura via Somente ocultas', async () => {
    const primeira = vaga(1);
    const ultima = { ...vaga(11), jaMeCandidatei: true };
    const fixture = await abrir(pagina([primeira]));
    mudarCampo(fixture, '#input-busca', 'java');
    elemento(fixture, 'btn-buscar').click();
    const tamanho = elDaFixture(fixture).querySelector<HTMLSelectElement>('#select-tamanho')!;
    const opt10 = [...tamanho.options].find((opt) => opt.text.includes('10 itens'))!;
    const anterior = consultaLista(0);
    mudarCampo(fixture, '#select-tamanho', opt10.value);
    expect(anterior.cancelled).toBe(true);
    const consulta = consultaLista(0);
    expect(consulta.request.params.get('size')).toBe('10');
    consulta.flush(pagina([primeira], 0, 11, 10));
    fixture.detectChanges();
    elemento(fixture, 'btn-proxima-pagina').click();
    consultaLista(1).flush(pagina([ultima], 1, 11, 10));
    detalhar(ultima);
    fixture.detectChanges();
    mudarCampo(fixture, '#input-busca', 'rascunho');
    mudarCampo(fixture, '#select-ocultacao', 'SOMENTE');
    elemento(fixture, 'btn-ocultar-vaga').click();
    const ocultar = httpMock.expectOne('/api/vagas/11/acao-pessoal');
    expect(ocultar.request.body).toEqual({ oculta: true });
    ocultar.flush(estadoAcao(true, true));

    const reconsulta = consultaLista(1);
    expect(reconsulta.request.params.get('q')).toBe('java');
    expect(reconsulta.request.params.get('ocultacao')).toBe('EXCLUIR');
    expect(reconsulta.request.params.get('size')).toBe('10');
    reconsulta.flush(pagina([], 1, 10, 10));
    const voltar = consultaLista(0);
    expect(voltar.request.params.get('size')).toBe('10');
    expect(voltar.request.params.get('q')).toBe('java');
    voltar.flush(pagina([primeira], 0, 10, 10));
    detalhar(primeira);
    fixture.detectChanges();
    expect(elemento(fixture, 'painel-detalhes').querySelector('h2')?.textContent).toContain('Vaga 1');
    expect(elemento(fixture, 'resumo-paginacao').textContent).toContain('Página 1 de 1');
    expect(elemento(fixture, 'lista-vagas').textContent).not.toContain('Vaga 11');

    mudarCampo(fixture, '#input-busca', 'java');
    elemento(fixture, 'btn-buscar').click();
    const somente = consultaLista(0);
    expect(somente.request.params.get('ocultacao')).toBe('SOMENTE');
    const oculta = { ...ultima, oculta: true };
    somente.flush(pagina([oculta], 0, 1, 10));
    detalhar(oculta);
    fixture.detectChanges();
    const restaurar = elemento(fixture, 'btn-ocultar-vaga');
    expect(restaurar.textContent).toContain('Restaurar');
    restaurar.click();
    const patchRestaurar = httpMock.expectOne('/api/vagas/11/acao-pessoal');
    expect(patchRestaurar.request.body).toEqual({ oculta: false });
    patchRestaurar.flush(estadoAcao(true, false));
    const depois = consultaLista(0);
    expect(depois.request.params.get('ocultacao')).toBe('SOMENTE');
    depois.flush(pagina([], 0, 0, 10));
    fixture.detectChanges();
    expect(elemento(fixture, 'estado-vazio').textContent).toContain('Nenhuma vaga corresponde');
    expect(elemento(fixture, 'painel-detalhes').querySelector('h2')).toBeNull();
  });

  it('cancela buscas e detalhes anteriores e separa erros de lista, detalhe e resultado vazio', async () => {
    const primeira = vaga(1);
    const segunda = vaga(2);
    const fixture = await abrir(pagina([primeira, segunda]));
    const cards = elDaFixture(fixture).querySelectorAll<HTMLButtonElement>('[data-testid="btn-ver-detalhe"]');
    cards[1].click();
    const detalheAntigo = httpMock.expectOne('/api/vagas/2');
    cards[0].click();
    expect(detalheAntigo.cancelled).toBe(true);
    detalhar(primeira);
    cards[1].click();
    httpMock.expectOne('/api/vagas/2').flush(null, { status: 404, statusText: 'Não encontrada' });
    fixture.detectChanges();
    expect(elemento(fixture, 'erro-detalhe').textContent).toContain('Vaga 2');
    expect(elemento(fixture, 'painel-detalhes').querySelector('article')).toBeNull();

    cards[1].click();
    const detalheDuranteBusca = httpMock.expectOne('/api/vagas/2');
    mudarCampo(fixture, '#input-busca', 'anterior');
    elemento(fixture, 'btn-buscar').click();
    expect(detalheDuranteBusca.cancelled).toBe(true);
    const buscaAntiga = consultaLista(0);
    fixture.detectChanges();
    expect(elemento(fixture, 'estado-carregando')).not.toBeNull();
    expect(elemento(fixture, 'estado-vazio')).toBeNull();

    mudarCampo(fixture, '#input-busca', 'atual');
    elemento(fixture, 'btn-buscar').click();
    expect(buscaAntiga.cancelled).toBe(true);
    const buscaAtual = consultaLista(0);
    expect(buscaAtual.request.params.get('q')).toBe('atual');
    buscaAtual.flush(null, { status: 500, statusText: 'Erro' });
    fixture.detectChanges();
    expect(elemento(fixture, 'erro-carregamento')).not.toBeNull();
    expect(elemento(fixture, 'erro-detalhe')).toBeNull();
    expect(elemento(fixture, 'estado-vazio')).toBeNull();
  });

  const fonteColeta: FonteConfigurada = {
    id: 7, fornecedor: 'GITHUB_ISSUES', identificadorMural: 'origem-inicial', nomeExibicao: 'GitHub',
    ativo: true, cadenciaMinutos: 360, ultimaColetaSucessoEm: null, cursorUltimaAtualizacao: null
  };

  function concluirColeta(fixture: ComponentFixture<AppComponent>, individual = false): void {
    if (individual) {
      elemento(fixture, 'btn-diagnostico').click();
      fixture.detectChanges();
      const botao = [...elDaFixture(fixture).querySelectorAll('button')]
        .find((b) => b.textContent?.includes('Coletar esta fonte'))!;
      botao.click();
    } else {
      elemento(fixture, 'btn-coletar-agora').click();
    }
    const resultado = { statusRotulo: 'Sucesso', anunciosRecebidos: 1, anunciosValidos: 1,
      anunciosNovos: 0, anunciosAtualizados: 1, anunciosRepetidos: 0, errosContagem: 0 };
    const req = httpMock.expectOne(individual ? '/api/coletas/executar/7' : '/api/coletas/executar');
    expect(req.request.method).toBe('POST');
    req.flush(individual ? resultado : [resultado]);
    httpMock.expectOne('/api/coletas/status').flush({ fontes: [fonteColeta], ultimasExecucoes: [] });
    responderOpcoes();
  }

  const origemInicial: AnuncioOrigem = {
    id: 1, fornecedor: 'GITHUB_ISSUES', identificadorMural: 'origem-inicial', identificadorExterno: '1',
    tituloOriginal: 'Anúncio', labelsOriginais: [], urlOrigem: 'https://example.invalid/anuncio',
    urlCandidatura: 'https://example.invalid/candidatar', disponibilidadeCodigo: 'DISPONIVEL',
    disponibilidadeRotulo: 'Disponível', motivoIndisponibilidade: null, publicadoNaFonteEm: null,
    atualizadoNaFonteEm: null, primeiraObservacaoEm: dataAcao, ultimaObservacaoEm: dataAcao
  };

  for (const individual of [false, true]) {
    it(`revalida descrição e origens do mesmo ID após coleta ${individual ? 'individual' : 'geral'}`, async () => {
      const inicial = { ...vaga(1), jaMeCandidatei: true };
      const fixture = await abrir(pagina([inicial]), [fonteColeta], [origemInicial]);
      expect(elemento(fixture, 'descricao-vaga').textContent).toBe('Descrição 1');
      expect(elDaFixture(fixture).querySelectorAll('.origins-list li')).toHaveLength(1);

      concluirColeta(fixture, individual);
      const atualizada = { ...inicial, cargo: 'Cargo atualizado', quantidadeAnunciosOrigem: 2 };
      consultaLista(0).flush(pagina([atualizada]));
      const revalidacao = httpMock.expectOne('/api/vagas/1');
      fixture.detectChanges();
      expect(elemento(fixture, 'btn-ver-detalhe').getAttribute('aria-current')).toBe('true');
      expect(elemento(fixture, 'painel-detalhes').textContent).toContain('Carregando detalhes');
      expect(elemento(fixture, 'descricao-vaga')).toBeNull();

      revalidacao.flush({ ...atualizada, descricao: 'Descrição atualizada', anunciosOrigem: [origemInicial,
        { ...origemInicial, id: 2, fornecedor: 'LEVER_BOARD', identificadorMural: 'origem-adicional' }] });
      fixture.detectChanges();
      expect(elemento(fixture, 'painel-detalhes').querySelector('h2')?.textContent).toBe('Cargo atualizado');
      expect(elemento(fixture, 'descricao-vaga').textContent).toBe('Descrição atualizada');
      expect([...elDaFixture(fixture).querySelectorAll('.origins-list li')].map((li) => li.textContent))
        .toEqual([expect.stringContaining('origem-inicial'), expect.stringContaining('origem-adicional')]);
      expect(elemento(fixture, 'btn-marcar-candidatura').textContent).toContain('Desfazer marcação');
    });
  }

  it('não mantém o detalhe antigo após falha na revalidação e permite tentar novamente pelo card', async () => {
    const primeira = vaga(1);
    const fixture = await abrir(pagina([primeira]));
    concluirColeta(fixture);
    consultaLista(0).flush(pagina([primeira]));
    httpMock.expectOne('/api/vagas/1').flush(null, { status: 500, statusText: 'Erro' });
    fixture.detectChanges();
    expect(elemento(fixture, 'erro-detalhe').textContent).toContain('Vaga 1');
    expect(elemento(fixture, 'descricao-vaga')).toBeNull();
    elemento(fixture, 'btn-ver-detalhe').click();
    detalhar(primeira, 'Descrição recuperada');
    fixture.detectChanges();
    expect(elemento(fixture, 'erro-detalhe')).toBeNull();
    expect(elemento(fixture, 'descricao-vaga').textContent).toBe('Descrição recuperada');
  });

  it('cancela a revalidação ao trocar de vaga sem perder uma candidatura confirmada durante a coleta', async () => {
    const primeira = vaga(1);
    const segunda = vaga(2);
    const fixture = await abrir(pagina([primeira, segunda]));
    elemento(fixture, 'btn-marcar-candidatura').click();
    const acao = httpMock.expectOne('/api/vagas/1/acao-pessoal');
    concluirColeta(fixture);
    consultaLista(0).flush(pagina([primeira, segunda]));
    const detalheAntesDoPatch = httpMock.expectOne('/api/vagas/1');

    acao.flush(estadoAcao(true, false));
    expect(detalheAntesDoPatch.cancelled).toBe(true);
    const marcada = { ...primeira, jaMeCandidatei: true };
    consultaLista(0).flush(pagina([marcada, segunda]));
    const detalheAntesDaTroca = httpMock.expectOne('/api/vagas/1');
    fixture.detectChanges();
    const cards = elDaFixture(fixture).querySelectorAll<HTMLButtonElement>('[data-testid="btn-ver-detalhe"]');
    cards[1].click();
    expect(detalheAntesDaTroca.cancelled).toBe(true);
    detalhar(segunda);
    fixture.detectChanges();
    expect(elemento(fixture, 'descricao-vaga').textContent).toBe('Descrição 2');
    expect(elemento(fixture, 'selo-candidatura').textContent).toContain('Já me candidatei');

    cards[0].click();
    detalhar(marcada);
    fixture.detectChanges();
    expect(elemento(fixture, 'candidatura-detalhe').textContent).toMatch(/Já me candidatei.*16:00/s);
  });
});

import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AppComponent } from './app.component';
import { PaginaVagas, StatusColetaGlobal, VagaDetalhe } from './models/vaga.model';

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

  it('deve renderizar vagas com publicação conhecida e desconhecida honestamente e paginar pelo backend', () => {
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

    httpMock.expectOne((r) => r.url === '/api/vagas' && r.params.get('page') === '0').flush(pagina1);

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
});

import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { RadarApiService } from './services/radar-api.service';
import { SaoPauloDataPipe } from './sao-paulo-data.pipe';
import {
  EstadoAcaoPessoal,
  ExecucaoColeta,
  FiltrosVagas,
  FonteConfigurada,
  OpcoesVagas,
  PaginaVagas,
  Publicacao,
  StatusColetaGlobal,
  VagaDetalhe,
  VagaResumo
} from './models/vaga.model';

const FILTROS_INICIAIS: FiltrosVagas = {
  q: '', fonte: '', area: '', senioridade: '', modalidade: '', tecnologia: '', localizacao: '',
  publicacao: 'ULTIMOS_7_DIAS', candidatura: 'TODAS', ocultacao: 'EXCLUIR',
  incluirIndisponiveis: false, bancoTalentos: false
};

const ROTULOS_PUBLICACAO: Record<Publicacao, string> = {
  ULTIMAS_24H: 'Últimas 24 horas',
  ULTIMOS_3_DIAS: 'Últimos 3 dias',
  ULTIMOS_7_DIAS: 'Últimos 7 dias',
  TODAS: 'Qualquer data (inclui sem data)',
  CONHECIDA: 'Somente com data conhecida',
  DESCONHECIDA: 'Somente sem data conhecida'
};

const FONTES_ANTERIORES = ['GITHUB_ISSUES', 'GUPY_PORTAL', 'GREENHOUSE_BOARD', 'ASHBY_BOARD', 'JOOBLE_API', 'ADZUNA_API'];

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [FormsModule, SaoPauloDataPipe],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent implements OnInit {
  paginaVagas: PaginaVagas | null = null;
  vagaSelecionada: VagaDetalhe | null = null;
  idSelecionado: number | null = null;
  statusColeta: StatusColetaGlobal | null = null;
  opcoesVagas: OpcoesVagas | null = null;
  fontesFiltro = FONTES_ANTERIORES;
  tecnologiasFiltro: string[] = [];
  readonly publicacoes = Object.entries(ROTULOS_PUBLICACAO);

  carregandoLista = false;
  carregandoDetalhe = false;
  executandoColeta = false;
  exibirDiagnosticoColeta = false;

  erroCarregamento: string | null = null;
  erroDetalhe: string | null = null;
  erroOpcoes: string | null = null;
  erroStatus: string | null = null;
  erroAcao: string | null = null;
  mensagemAcao: string | null = null;
  erroColeta: string | null = null;
  mensagemColeta: string | null = null;

  readonly acoesPendentes = new Set<number>();
  private acaoConfirmada: { id: number; estado: EstadoAcaoPessoal } | null = null;
  private readonly destroyRef = inject(DestroyRef);
  private consultaLista?: Subscription;
  private consultaDetalhe?: Subscription;
  private consultaOpcoes?: Subscription;
  private consultaStatus?: Subscription;

  paginaAtual = 0;
  tamanhoPagina = 15;
  filtros = { ...FILTROS_INICIAIS };
  private filtrosAplicados = { ...FILTROS_INICIAIS };

  constructor(private readonly api: RadarApiService) {}

  ngOnInit(): void {
    this.carregarStatusColeta();
    this.carregarOpcoesVagas();
    this.carregarVagas(0);
  }

  carregarStatusColeta(): void {
    this.consultaStatus?.unsubscribe();
    this.erroStatus = null;
    this.consultaStatus = this.api.consultarStatusColeta().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (status) => {
        this.statusColeta = status;
      },
      error: () => {
        this.erroStatus = 'Não foi possível consultar o status de coleta no backend local.';
      }
    });
  }

  carregarOpcoesVagas(): void {
    this.consultaOpcoes?.unsubscribe();
    this.erroOpcoes = null;
    this.consultaOpcoes = this.api.consultarOpcoesVagas().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (opcoes) => {
        this.opcoesVagas = opcoes;
        this.fontesFiltro = [...new Set([...FONTES_ANTERIORES, ...opcoes.fontes, this.filtros.fonte])].filter(Boolean);
        this.tecnologiasFiltro = [...new Set([...opcoes.tecnologias, this.filtros.tecnologia])].filter(Boolean);
      },
      error: () => {
        this.erroOpcoes = 'Não foi possível atualizar as opções de filtros.';
      }
    });
  }

  get periodoAplicado(): string {
    return ROTULOS_PUBLICACAO[this.filtrosAplicados.publicacao];
  }

  carregarVagas(pagina: number): void {
    this.consultaLista?.unsubscribe();
    this.consultaDetalhe?.unsubscribe();
    this.carregandoDetalhe = this.idSelecionado !== null && this.vagaSelecionada === null;
    this.erroDetalhe = null;
    this.carregandoLista = true;
    this.erroCarregamento = null;
    this.paginaAtual = Math.max(0, pagina);

    this.consultaLista = this.api
      .listarVagas({
        ...this.filtrosAplicados,
        page: this.paginaAtual,
        size: this.tamanhoPagina
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (res) => {
          const ultimaPagina = Math.max(0, res.totalPaginas - 1);
          if (pagina > ultimaPagina) {
            this.carregarVagas(ultimaPagina);
            return;
          }
          this.paginaVagas = res;
          this.carregandoLista = false;
          const vaga = res.itens.find((v) => v.id === this.idSelecionado) ?? res.itens[0];
          if (!vaga) {
            this.limparSelecao();
          } else if (this.vagaSelecionada?.id === vaga.id) {
            // O total e a participação da vaga na página vêm sempre da nova consulta.
            this.vagaSelecionada = { ...this.vagaSelecionada, ...vaga };
          } else {
            this.selecionarVaga(vaga);
          }
        },
        error: () => {
          this.paginaVagas = null;
          this.carregandoLista = false;
          this.carregandoDetalhe = false;
          this.erroCarregamento =
            'Falha ao consultar o catálogo de vagas no backend local. Verifique se a API está em execução.';
        }
      });
  }

  aplicarFiltros(): void {
    this.filtrosAplicados = { ...this.filtros };
    this.carregarVagas(0);
  }

  limparFiltros(): void {
    this.filtros = { ...FILTROS_INICIAIS };
    this.tamanhoPagina = 15;
    this.aplicarFiltros();
  }

  irParaPagina(novaPagina: number): void {
    if (!this.paginaVagas || this.carregandoLista) {
      return;
    }
    if (novaPagina < 0 || novaPagina >= this.paginaVagas.totalPaginas) {
      return;
    }
    this.carregarVagas(novaPagina);
  }

  alterarTamanhoPagina(): void {
    this.carregarVagas(0);
  }

  selecionarVaga(vaga: VagaResumo): void {
    this.limparSelecao();
    this.idSelecionado = vaga.id;
    this.carregandoDetalhe = true;
    this.consultaDetalhe = this.api.buscarDetalheVaga(vaga.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (detalhe) => {
        this.vagaSelecionada = detalhe;
        this.carregandoDetalhe = false;
      },
      error: () => {
        this.carregandoDetalhe = false;
        this.erroDetalhe = `Não foi possível carregar os detalhes de ${vaga.cargo}. Selecione a vaga para tentar novamente.`;
      }
    });
  }

  private limparSelecao(): void {
    this.consultaDetalhe?.unsubscribe();
    this.idSelecionado = null;
    this.vagaSelecionada = null;
    this.carregandoDetalhe = false;
    this.erroDetalhe = null;
  }

  get datasAcaoSelecionada(): EstadoAcaoPessoal | null {
    const acao = this.acaoConfirmada;
    return acao && acao.id === this.vagaSelecionada?.id ? acao.estado : null;
  }

  alterarAcao(vaga: VagaResumo, campo: 'jaMeCandidatei' | 'oculta'): void {
    const { id, cargo } = vaga;
    if (this.acoesPendentes.has(id)) {
      return;
    }
    this.acoesPendentes.add(id);
    this.erroAcao = null;
    this.mensagemAcao = null;
    this.api.atualizarAcaoPessoal(id, { [campo]: !vaga[campo] })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (estado) => {
          this.acoesPendentes.delete(id);
          const { jaMeCandidatei, oculta } = estado;
          const estadosPessoais = { jaMeCandidatei, oculta };
          if (this.idSelecionado === id) {
            this.acaoConfirmada = { id, estado };
          }
          if (this.vagaSelecionada?.id === id) {
            this.vagaSelecionada = { ...this.vagaSelecionada, ...estadosPessoais };
          }
          this.mensagemAcao = campo === 'oculta'
            ? `${cargo}: ${oculta ? 'vaga ocultada' : 'vaga restaurada'}.`
            : `${cargo}: ${jaMeCandidatei ? 'candidatura marcada' : 'marcação de candidatura desfeita'}.`;
          this.carregarVagas(this.paginaAtual);
        },
        error: () => {
          this.acoesPendentes.delete(id);
          this.erroAcao = `Não foi possível salvar a ação para ${cargo}. Tente novamente.`;
        }
      });
  }

  dispararColetaManual(): void {
    if (this.executandoColeta) {
      return;
    }
    this.executandoColeta = true;
    this.erroColeta = null;
    this.mensagemColeta = null;

    this.api.executarColetaManual().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (execucoes: ExecucaoColeta[]) => {
        this.executandoColeta = false;
        const novos = execucoes.reduce((acc, e) => acc + e.anunciosNovos, 0);
        const atualizados = execucoes.reduce((acc, e) => acc + e.anunciosAtualizados, 0);
        const repetidos = execucoes.reduce((acc, e) => acc + e.anunciosRepetidos, 0);
        const erros = execucoes.reduce((acc, e) => acc + e.errosContagem, 0);

        this.mensagemColeta = `Coleta concluída em ${execucoes.length} fonte(s) ativa(s): ${novos} novo(s), ${atualizados} atualizado(s), ${repetidos} repetido(s), ${erros} erro(s).`;
        this.recarregarCatalogoAposColeta();
      },
      error: () => {
        this.executandoColeta = false;
        this.erroColeta = 'Falha ao executar coleta manual no backend.';
        this.carregarStatusColeta();
      }
    });
  }

  executarFonteEspecifica(fonte: FonteConfigurada): void {
    if (this.executandoColeta) {
      return;
    }
    this.executandoColeta = true;
    this.erroColeta = null;
    this.mensagemColeta = null;

    this.api.executarFonteIndividual(fonte.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (exec: ExecucaoColeta) => {
        this.executandoColeta = false;
        this.mensagemColeta = `Fonte ${fonte.nomeExibicao} (${exec.statusRotulo}): ${exec.anunciosRecebidos} recebido(s), ${exec.anunciosValidos} válido(s), ${exec.anunciosNovos} novo(s), ${exec.errosContagem} erro(s).`;
        this.recarregarCatalogoAposColeta();
      },
      error: () => {
        this.executandoColeta = false;
        this.erroColeta = `Falha ao executar a fonte ${fonte.nomeExibicao}.`;
        this.carregarStatusColeta();
      }
    });
  }

  private recarregarCatalogoAposColeta(): void {
    // A lista não traz a descrição nem as origens. Mantém o ID para reconsultar o detalhe completo.
    this.vagaSelecionada = null;
    this.carregarStatusColeta();
    this.carregarOpcoesVagas();
    this.carregarVagas(this.paginaAtual);
  }

  alternarDiagnostico(): void {
    this.exibirDiagnosticoColeta = !this.exibirDiagnosticoColeta;
  }

  formatarEmpresa(empresa: string | null | undefined): string {
    return empresa && empresa.trim().length > 0 ? empresa : 'Não informada';
  }

  formatarLocalizacao(localizacao: string | null | undefined): string {
    return localizacao && localizacao.trim().length > 0 ? localizacao : 'Não informada';
  }

  rotuloFornecedor(fornecedor: string): string {
    switch (fornecedor) {
      case 'GITHUB_ISSUES':
        return 'GitHub Issues';
      case 'GUPY_PORTAL':
        return 'Gupy Portal';
      case 'GREENHOUSE_BOARD':
        return 'Greenhouse';
      case 'ASHBY_BOARD':
        return 'Ashby';
      case 'LEVER_BOARD':
        return 'Lever';
      case 'JOOBLE_API':
        return 'Jooble';
      case 'ADZUNA_API':
        return 'Adzuna';
      case 'JSEARCH_API':
        return 'JSearch (Não implementado)';
      default:
        return fornecedor;
    }
  }

  rotuloEstadoValidacao(estado: string | undefined): string {
    switch (estado) {
      case 'ATIVA_VALIDADA':
        return 'Validada por coleta';
      case 'CONFIGURADA_NAO_VALIDADA':
        return 'Configurada (aguarda coleta)';
      case 'FALHA_AUTENTICACAO':
        return 'Erro de autenticação/chave';
      case 'FALHA_EXECUCAO':
        return 'Falha na última coleta';
      case 'LIMITE_ATINGIDO':
        return 'Limite atingido (Rate Limit)';
      case 'BLOQUEADA_CREDENCIAL':
        return 'Bloqueada (Credencial ausente)';
      case 'NAO_IMPLEMENTADA':
        return 'Conector não implementado';
      case 'BLOQUEADA_RESTRICAO_ACESSO':
        return 'Bloqueada (Restrição de acesso)';
      case 'EXPERIMENTAL_ATIVAVEL':
        return 'Experimental / Inativa';
      default:
        return estado || 'Registrada';
    }
  }

  rotuloSituacaoAtualizacao(situacao: string | undefined): string {
    switch (situacao) {
      case 'ATUALIZADA':
        return 'Em dia';
      case 'ATUALIZACAO_PARCIAL':
        return 'Parcial / Continuação pendente';
      case 'COM_FALHA':
        return 'Com falha recente';
      case 'DESATUALIZADA':
        return 'Desatualizada';
      case 'NUNCA_COLETADA':
        return 'Nunca coletada';
      case 'INATIVA':
        return 'Inativa';
      default:
        return '—';
    }
  }

  ehRecente24h(vaga: VagaResumo): boolean {
    if (!vaga.publicacaoConhecida || !vaga.publicadoEm) {
      return false;
    }
    const pubMs = Date.parse(vaga.publicadoEm);
    if (Number.isNaN(pubMs)) {
      return false;
    }
    const diffHoras = (Date.now() - pubMs) / (1000 * 60 * 60);
    return diffHoras >= 0 && diffHoras <= 24;
  }
}

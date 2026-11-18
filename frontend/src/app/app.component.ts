import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RadarApiService } from './services/radar-api.service';
import {
  ExecucaoColeta,
  PaginaVagas,
  StatusColetaGlobal,
  VagaDetalhe,
  VagaResumo
} from './models/vaga.model';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent implements OnInit {
  paginaVagas: PaginaVagas | null = null;
  vagaSelecionada: VagaDetalhe | null = null;
  statusColeta: StatusColetaGlobal | null = null;

  carregandoLista = false;
  carregandoDetalhe = false;
  executandoColeta = false;
  exibirDiagnosticoColeta = false;

  erroCarregamento: string | null = null;
  erroColeta: string | null = null;
  mensagemColeta: string | null = null;

  // Parâmetros de consulta paginada no servidor
  paginaAtual = 0;
  tamanhoPagina = 15;
  termoBusca = '';
  filtroFonte = 'TODAS';
  filtroPublicacao: 'TODAS' | 'CONHECIDA' | 'DESCONHECIDA' = 'TODAS';
  incluirIndisponiveis = false;
  bancoTalentos = false;

  constructor(private readonly api: RadarApiService) {}

  ngOnInit(): void {
    this.carregarStatusColeta();
    this.carregarVagas(0);
  }

  carregarStatusColeta(): void {
    this.api.consultarStatusColeta().subscribe({
      next: (status) => {
        this.statusColeta = status;
      },
      error: () => {
        this.erroCarregamento = 'Não foi possível consultar o status de coleta no backend local.';
      }
    });
  }

  carregarVagas(pagina: number): void {
    this.carregandoLista = true;
    this.erroCarregamento = null;
    this.paginaAtual = Math.max(0, pagina);

    this.api
      .listarVagas({
        page: this.paginaAtual,
        size: this.tamanhoPagina,
        q: this.termoBusca,
        fonte: this.filtroFonte,
        publicacao: this.filtroPublicacao,
        incluirIndisponiveis: this.incluirIndisponiveis,
        bancoTalentos: this.bancoTalentos
      })
      .subscribe({
        next: (res) => {
          this.paginaVagas = res;
          this.carregandoLista = false;
          if (res.itens.length > 0) {
            const aindaNaPagina =
              this.vagaSelecionada && res.itens.some((v) => v.id === this.vagaSelecionada?.id);
            if (!aindaNaPagina) {
              this.selecionarVaga(res.itens[0]);
            }
          } else {
            this.vagaSelecionada = null;
          }
        },
        error: () => {
          this.carregandoLista = false;
          this.erroCarregamento =
            'Falha ao consultar o catálogo de vagas no backend local. Verifique se a API está em execução.';
        }
      });
  }

  aplicarFiltros(): void {
    this.carregarVagas(0);
  }

  limparFiltros(): void {
    this.termoBusca = '';
    this.filtroFonte = 'TODAS';
    this.filtroPublicacao = 'TODAS';
    this.incluirIndisponiveis = false;
    this.bancoTalentos = false;
    this.carregarVagas(0);
  }

  irParaPagina(novaPagina: number): void {
    if (!this.paginaVagas) {
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
    this.carregandoDetalhe = true;
    this.api.buscarDetalheVaga(vaga.id).subscribe({
      next: (detalhe) => {
        this.vagaSelecionada = detalhe;
        this.carregandoDetalhe = false;
      },
      error: () => {
        this.carregandoDetalhe = false;
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

    this.api.executarColetaManual().subscribe({
      next: (execucoes: ExecucaoColeta[]) => {
        this.executandoColeta = false;
        const novos = execucoes.reduce((acc, e) => acc + e.anunciosNovos, 0);
        const atualizados = execucoes.reduce((acc, e) => acc + e.anunciosAtualizados, 0);
        const repetidos = execucoes.reduce((acc, e) => acc + e.anunciosRepetidos, 0);
        const erros = execucoes.reduce((acc, e) => acc + e.errosContagem, 0);

        this.mensagemColeta = `Coleta concluída em ${execucoes.length} fonte(s) ativa(s): ${novos} novo(s), ${atualizados} atualizado(s), ${repetidos} repetido(s), ${erros} erro(s).`;
        this.carregarStatusColeta();
        this.carregarVagas(this.paginaAtual);
      },
      error: () => {
        this.executandoColeta = false;
        this.erroColeta = 'Falha ao executar coleta manual no backend.';
        this.carregarStatusColeta();
      }
    });
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
      default:
        return fornecedor;
    }
  }

  rotuloEstadoValidacao(estado: string | undefined): string {
    switch (estado) {
      case 'ATIVA_VALIDADA':
        return 'Ativa e Validada';
      case 'BLOQUEADA_CREDENCIAL':
        return 'Bloqueada (Credencial ausente)';
      case 'BLOQUEADA_RESTRICAO_ACESSO':
        return 'Bloqueada (Restrição de acesso)';
      case 'EXPERIMENTAL_ATIVAVEL':
        return 'Experimental / Inativa';
      default:
        return estado || 'Registrada';
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

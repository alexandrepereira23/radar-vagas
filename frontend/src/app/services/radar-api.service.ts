import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  AlteracaoAcaoPessoal,
  EstadoAcaoPessoal,
  ExecucaoColeta,
  OpcoesVagas,
  PaginaVagas,
  ParametrosConsultaVagas,
  StatusColetaGlobal,
  VagaDetalhe
} from '../models/vaga.model';

@Injectable({
  providedIn: 'root'
})
export class RadarApiService {
  private readonly apiUrl = '/api';

  constructor(private readonly http: HttpClient) {}

  listarVagas(params: ParametrosConsultaVagas): Observable<PaginaVagas> {
    let httpParams = new HttpParams()
      .set('page', String(params.page))
      .set('size', String(params.size))
      .set('publicacao', params.publicacao)
      .set('candidatura', params.candidatura)
      .set('ocultacao', params.ocultacao)
      .set('incluirIndisponiveis', params.incluirIndisponiveis)
      .set('bancoTalentos', params.bancoTalentos);

    for (const campo of ['q', 'fonte', 'area', 'senioridade', 'modalidade', 'tecnologia', 'localizacao'] as const) {
      const valor = campo === 'tecnologia' ? params[campo] : params[campo].trim();
      if (valor && !(campo === 'fonte' && valor === 'TODAS')) {
        httpParams = httpParams.set(campo, valor);
      }
    }

    return this.http.get<PaginaVagas>(`${this.apiUrl}/vagas`, { params: httpParams });
  }

  consultarOpcoesVagas(): Observable<OpcoesVagas> {
    return this.http.get<OpcoesVagas>(`${this.apiUrl}/vagas/opcoes`);
  }

  atualizarAcaoPessoal(id: number, alteracao: AlteracaoAcaoPessoal): Observable<EstadoAcaoPessoal> {
    return this.http.patch<EstadoAcaoPessoal>(`${this.apiUrl}/vagas/${id}/acao-pessoal`, alteracao);
  }

  buscarDetalheVaga(id: number): Observable<VagaDetalhe> {
    return this.http.get<VagaDetalhe>(`${this.apiUrl}/vagas/${id}`);
  }

  consultarStatusColeta(): Observable<StatusColetaGlobal> {
    return this.http.get<StatusColetaGlobal>(`${this.apiUrl}/coletas/status`);
  }

  executarColetaManual(): Observable<ExecucaoColeta[]> {
    return this.http.post<ExecucaoColeta[]>(`${this.apiUrl}/coletas/executar`, {});
  }

  executarFonteIndividual(idFonte: number): Observable<ExecucaoColeta> {
    return this.http.post<ExecucaoColeta>(`${this.apiUrl}/coletas/executar/${idFonte}`, {});
  }
}

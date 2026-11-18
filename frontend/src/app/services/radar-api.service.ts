import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  ExecucaoColeta,
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
      .set('publicacao', params.publicacao ?? 'TODAS')
      .set('incluirIndisponiveis', String(Boolean(params.incluirIndisponiveis)))
      .set('bancoTalentos', String(Boolean(params.bancoTalentos)));

    if (params.q && params.q.trim().length > 0) {
      httpParams = httpParams.set('q', params.q.trim());
    }
    if (params.fonte && params.fonte.trim().length > 0 && params.fonte !== 'TODAS') {
      httpParams = httpParams.set('fonte', params.fonte.trim());
    }

    return this.http.get<PaginaVagas>(`${this.apiUrl}/vagas`, { params: httpParams });
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
}

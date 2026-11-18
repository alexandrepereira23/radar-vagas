export interface VagaResumo {
  id: number;
  cargo: string;
  empresa: string | null;
  localizacao: string | null;
  modalidadeCodigo: string;
  modalidadeRotulo: string;
  senioridadeCodigo: string;
  senioridadeRotulo: string;
  regimeCodigo: string;
  regimeRotulo: string;
  areaCodigo: string;
  areaRotulo: string;
  tipoOportunidadeCodigo: string;
  tipoOportunidadeRotulo: string;
  disponibilidadeCodigo: string;
  disponibilidadeRotulo: string;
  motivoDisponibilidade: string | null;
  tecnologias: string[];
  publicadoEm: string | null;
  publicacaoConhecida: boolean;
  primeiraObservacaoEm: string;
  ultimaObservacaoEm: string;
  atualizadoNaFonteEm: string | null;
  fontePrincipal: string;
  muralPrincipal: string;
  identificadorExternoPrincipal: string;
  linkCandidatura: string;
  linkOrigemPrincipal: string;
  jaMeCandidatei: boolean;
  oculta: boolean;
  quantidadeAnunciosOrigem?: number;
}

export interface AnuncioOrigem {
  id: number;
  fornecedor: string;
  identificadorMural: string;
  identificadorExterno: string;
  tituloOriginal: string;
  labelsOriginais: string[];
  urlOrigem: string;
  urlCandidatura: string;
  disponibilidadeCodigo: string;
  disponibilidadeRotulo: string;
  motivoIndisponibilidade: string | null;
  publicadoNaFonteEm: string | null;
  atualizadoNaFonteEm: string | null;
  primeiraObservacaoEm: string;
  ultimaObservacaoEm: string;
}

export interface VagaDetalhe extends VagaResumo {
  descricao: string;
  anunciosOrigem: AnuncioOrigem[];
}

export interface PaginaVagas {
  itens: VagaResumo[];
  pagina: number;
  tamanhoPagina: number;
  totalElementos: number;
  totalPaginas: number;
  temProxima: boolean;
  temAnterior: boolean;
}

export interface FonteConfigurada {
  id: number;
  fornecedor: string;
  identificadorMural: string;
  nomeExibicao: string;
  ativo: boolean;
  cadenciaMinutos: number;
  categoria?: string;
  estadoValidacao?: string;
  metodoAcesso?: string | null;
  credencialNecessaria?: string | null;
  observacaoValidacao?: string | null;
  ultimaColetaSucessoEm: string | null;
  cursorUltimaAtualizacao: string | null;
}

export interface ExecucaoColeta {
  id: number;
  fornecedor: string;
  identificadorMural: string;
  statusCodigo: string;
  statusRotulo: string;
  iniciadoEm: string;
  finalizadoEm: string | null;
  anunciosRecebidos: number;
  anunciosValidos: number;
  anunciosNovos: number;
  anunciosAtualizados: number;
  anunciosRepetidos: number;
  anunciosIgnorados: number;
  errosContagem: number;
  rateLimitRestante: number | null;
  rateLimitResetEm: string | null;
  erroResumido: string | null;
}

export interface StatusColetaGlobal {
  ultimaColetaSucessoGlobalEm: string | null;
  dadosDesatualizados: boolean;
  motivoEstadoDados: string;
  coletaEmAndamento: boolean;
  fontes: FonteConfigurada[];
  ultimasExecucoes: ExecucaoColeta[];
}

export interface ParametrosConsultaVagas {
  page: number;
  size: number;
  q?: string;
  fonte?: string;
  publicacao?: 'TODAS' | 'CONHECIDA' | 'DESCONHECIDA';
  incluirIndisponiveis?: boolean;
  bancoTalentos?: boolean;
}

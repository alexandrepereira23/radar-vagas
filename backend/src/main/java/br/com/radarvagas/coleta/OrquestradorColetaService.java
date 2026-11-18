package br.com.radarvagas.coleta;

import br.com.radarvagas.catalogo.CatalogoVagasService;
import br.com.radarvagas.catalogo.SanitizadorConteudo;
import br.com.radarvagas.config.RadarProperties;
import jakarta.annotation.PostConstruct;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OrquestradorColetaService {

	private static final Logger log = LoggerFactory.getLogger(OrquestradorColetaService.class);

	private final ConfiguracaoFonteRepository configuracaoFonteRepository;
	private final ExecucaoColetaRepository execucaoColetaRepository;
	private final CatalogoVagasService catalogoVagasService;
	private final RadarProperties properties;
	private final Clock clock;
	private final Map<String, ConectorFonteVagas> conectoresPorFornecedor;
	private final Set<String> fontesEmExecucao = ConcurrentHashMap.newKeySet();

	public OrquestradorColetaService(
			ConfiguracaoFonteRepository configuracaoFonteRepository,
			ExecucaoColetaRepository execucaoColetaRepository,
			CatalogoVagasService catalogoVagasService,
			RadarProperties properties,
			Clock clock,
			List<ConectorFonteVagas> conectores
	) {
		this.configuracaoFonteRepository = configuracaoFonteRepository;
		this.execucaoColetaRepository = execucaoColetaRepository;
		this.catalogoVagasService = catalogoVagasService;
		this.properties = properties;
		this.clock = clock;
		this.conectoresPorFornecedor = conectores.stream()
				.collect(Collectors.toMap(ConectorFonteVagas::fornecedor, Function.identity()));
	}

	@PostConstruct
	public void inicializarFontesERecuperarInterrompidas() {
		Instant agora = Instant.now(clock);

		// Recupera execuções interrompidas caso o processo anterior tenha sido encerrado no meio da coleta
		List<ExecucaoColeta> todasRecentes = execucaoColetaRepository.findTop20ByOrderByIniciadoEmDesc();
		for (ExecucaoColeta exec : todasRecentes) {
			if (exec.getStatus() == StatusExecucaoColeta.EM_ANDAMENTO) {
				exec.setStatus(StatusExecucaoColeta.FALHA);
				exec.setFinalizadoEm(agora);
				exec.setErroResumido("Execução anterior interrompida antes da conclusão; reexecução idempotente disponível.");
				execucaoColetaRepository.save(exec);
			}
		}

		// 1. Sincroniza repositórios configurados do GitHub Issues
		List<RadarProperties.RepositorioConfig> repos = properties.getGithub().getRepositorios();
		if (repos != null) {
			boolean temToken = properties.getGithub().getToken() != null && !properties.getGithub().getToken().isBlank();
			for (RadarProperties.RepositorioConfig repo : repos) {
				if (repo.getIdentificador() == null || repo.getIdentificador().isBlank()) {
					continue;
				}
				String idMural = repo.getIdentificador().trim();
				sincronizarConfiguracaoFonte(
						GitHubIssueClassificador.FORNECEDOR_GITHUB,
						idMural,
						repo.getNomeExibicao() != null ? repo.getNomeExibicao() : idMural,
						repo.isAtivo(),
						repo.getCadenciaMinutos() > 0 ? repo.getCadenciaMinutos() : 360,
						"COMUNIDADE",
						repo.isAtivo() ? "ATIVA_VALIDADA" : "EXPERIMENTAL_ATIVAVEL",
						"API REST Oficial GitHub Issues (/repos/" + idMural + "/issues)",
						temToken ? "GITHUB_TOKEN configurado (5000 req/h)" : "Opcional: GITHUB_TOKEN (sem token: 60 req/h)",
						"Validado com coleta real na API do GitHub.",
						agora
				);
			}
		}

		// 2. Sincroniza demais fontes registradas (Agregadores, Murais de Empresas e Portais Experimentais)
		List<RadarProperties.FonteCatalogoConfig> extras = properties.getFontesRegistradas();
		if (extras != null) {
			for (RadarProperties.FonteCatalogoConfig fc : extras) {
				if (fc.getFornecedor() == null || fc.getIdentificador() == null) {
					continue;
				}
				boolean ativoEfetivo = fc.isAtivo();
				String estadoEfetivo = fc.getEstadoValidacao() != null ? fc.getEstadoValidacao() : "ATIVA_VALIDADA";
				String obsEfetiva = fc.getObservacaoValidacao();

				if (JoobleConector.FORNECEDOR.equals(fc.getFornecedor())) {
					boolean temChave = properties.getJooble().getApiKey() != null && !properties.getJooble().getApiKey().isBlank();
					if (temChave) {
						ativoEfetivo = true;
						estadoEfetivo = "ATIVA_VALIDADA";
						obsEfetiva = "JOOBLE_API_KEY presente no ambiente.";
					} else {
						ativoEfetivo = false;
						estadoEfetivo = "BLOQUEADA_CREDENCIAL";
					}
				} else if (AdzunaConector.FORNECEDOR.equals(fc.getFornecedor())) {
					boolean temChave = properties.getAdzuna().getAppId() != null && !properties.getAdzuna().getAppId().isBlank()
							&& properties.getAdzuna().getAppKey() != null && !properties.getAdzuna().getAppKey().isBlank();
					if (temChave) {
						ativoEfetivo = true;
						estadoEfetivo = "ATIVA_VALIDADA";
						obsEfetiva = "ADZUNA_APP_ID e ADZUNA_APP_KEY presentes no ambiente.";
					} else {
						ativoEfetivo = false;
						estadoEfetivo = "BLOQUEADA_CREDENCIAL";
					}
				}

				sincronizarConfiguracaoFonte(
						fc.getFornecedor().trim(),
						fc.getIdentificador().trim(),
						fc.getNomeExibicao() != null ? fc.getNomeExibicao() : fc.getIdentificador().trim(),
						ativoEfetivo,
						fc.getCadenciaMinutos() > 0 ? fc.getCadenciaMinutos() : 360,
						fc.getCategoria() != null ? fc.getCategoria() : "OUTRA",
						estadoEfetivo,
						fc.getMetodoAcesso(),
						fc.getCredencialNecessaria(),
						obsEfetiva,
						agora
				);
			}
		}
	}

	private void sincronizarConfiguracaoFonte(
			String fornecedor,
			String idMural,
			String nomeExibicao,
			boolean ativo,
			int cadenciaMinutos,
			String categoria,
			String estadoValidacao,
			String metodoAcesso,
			String credencialNecessaria,
			String observacaoValidacao,
			Instant agora
	) {
		Optional<ConfiguracaoFonte> existente = configuracaoFonteRepository
				.findByFornecedorAndIdentificadorMural(fornecedor, idMural);

		ConfiguracaoFonte cfg = existente.orElseGet(() -> {
			ConfiguracaoFonte nova = new ConfiguracaoFonte();
			nova.setFornecedor(fornecedor);
			nova.setIdentificadorMural(idMural);
			nova.setCriadoEm(agora);
			return nova;
		});

		cfg.setNomeExibicao(nomeExibicao);
		cfg.setAtivo(ativo);
		cfg.setCadenciaMinutos(cadenciaMinutos);
		cfg.setCategoria(categoria);
		cfg.setEstadoValidacao(estadoValidacao);
		cfg.setMetodoAcesso(metodoAcesso);
		cfg.setCredencialNecessaria(credencialNecessaria);
		cfg.setObservacaoValidacao(observacaoValidacao);
		cfg.setAtualizadoEm(agora);
		configuracaoFonteRepository.save(cfg);
	}

	public List<ExecucaoColetaDto> executarFontesAtivas() {
		List<ConfiguracaoFonte> ativas = configuracaoFonteRepository.findByAtivoTrueOrderByIdAsc();
		List<ExecucaoColetaDto> resultados = new ArrayList<>();
		for (ConfiguracaoFonte fonte : ativas) {
			resultados.add(executarFonte(fonte));
		}
		return resultados;
	}

	public Optional<ExecucaoColetaDto> executarFontePorId(Long fonteId) {
		return configuracaoFonteRepository.findById(fonteId).map(this::executarFonte);
	}

	public List<ExecucaoColetaDto> executarFontesVencidasPelaCadencia() {
		Instant agora = Instant.now(clock);
		List<ConfiguracaoFonte> ativas = configuracaoFonteRepository.findByAtivoTrueOrderByIdAsc();
		List<ExecucaoColetaDto> executadas = new ArrayList<>();

		for (ConfiguracaoFonte fonte : ativas) {
			Instant ultima = fonte.getUltimaColetaSucessoEm();
			boolean vencida = (ultima == null)
					|| Duration.between(ultima, agora).toMinutes() >= Math.max(1, fonte.getCadenciaMinutos());
			if (vencida) {
				executadas.add(executarFonte(fonte));
			}
		}
		return executadas;
	}

	public ExecucaoColetaDto executarFonte(ConfiguracaoFonte fonte) {
		String chaveLock = fonte.getFornecedor() + ":" + fonte.getIdentificadorMural();
		Instant inicio = Instant.now(clock);

		if ("BLOQUEADA_RESTRICAO_ACESSO".equalsIgnoreCase(fonte.getEstadoValidacao())) {
			ExecucaoColeta bloqueada = new ExecucaoColeta();
			bloqueada.setFornecedor(fonte.getFornecedor());
			bloqueada.setIdentificadorMural(fonte.getIdentificadorMural());
			bloqueada.setStatus(StatusExecucaoColeta.BLOQUEADA_ACESSO);
			bloqueada.setIniciadoEm(inicio);
			bloqueada.setFinalizadoEm(inicio);
			bloqueada.setErroResumido(fonte.getObservacaoValidacao() != null
					? fonte.getObservacaoValidacao()
					: "Fonte bloqueada por restrição de acesso ou termos de uso.");
			return mapearExecucaoDto(execucaoColetaRepository.save(bloqueada));
		}

		if (!fontesEmExecucao.add(chaveLock)) {
			ExecucaoColeta ignorada = new ExecucaoColeta();
			ignorada.setFornecedor(fonte.getFornecedor());
			ignorada.setIdentificadorMural(fonte.getIdentificadorMural());
			ignorada.setStatus(StatusExecucaoColeta.IGNORADA_SOBREPOSICAO);
			ignorada.setIniciadoEm(inicio);
			ignorada.setFinalizadoEm(inicio);
			ignorada.setErroResumido("Coleta ignorada porque já há uma execução em andamento para esta mesma fonte.");
			return mapearExecucaoDto(execucaoColetaRepository.save(ignorada));
		}

		ExecucaoColeta execucao = new ExecucaoColeta();
		execucao.setFornecedor(fonte.getFornecedor());
		execucao.setIdentificadorMural(fonte.getIdentificadorMural());
		execucao.setStatus(StatusExecucaoColeta.EM_ANDAMENTO);
		execucao.setIniciadoEm(inicio);
		execucao = execucaoColetaRepository.save(execucao);

		try {
			ConectorFonteVagas conector = conectoresPorFornecedor.get(fonte.getFornecedor());
			if (conector == null) {
				boolean ehBloqueioCredencial = "BLOQUEADA_CREDENCIAL".equalsIgnoreCase(fonte.getEstadoValidacao());
				execucao.setStatus(ehBloqueioCredencial ? StatusExecucaoColeta.BLOQUEADA_CREDENCIAL : StatusExecucaoColeta.FALHA);
				execucao.setFinalizadoEm(Instant.now(clock));
				execucao.setErrosContagem(ehBloqueioCredencial ? 0 : 1);
				execucao.setErroResumido(fonte.getObservacaoValidacao() != null
						? fonte.getObservacaoValidacao()
						: "Nenhum conector ativo para o fornecedor: " + fonte.getFornecedor());
				return mapearExecucaoDto(execucaoColetaRepository.save(execucao));
			}

			ResultadoColetaFonte resultado = conector.coletar(fonte);

			int novos = 0;
			int atualizados = 0;
			int repetidos = 0;
			int errosPersistencia = 0;
			String ultimoErroPersistencia = null;

			for (AnuncioExtraido anuncio : resultado.anunciosValidos()) {
				try {
					CatalogoVagasService.ResultadoPersistenciaAnuncio res =
							catalogoVagasService.registrarOuAtualizarAnuncio(anuncio);
					switch (res) {
						case NOVO -> novos++;
						case ATUALIZADO -> atualizados++;
						case REPETIDO -> repetidos++;
					}
				} catch (Exception ex) {
					errosPersistencia++;
					ultimoErroPersistencia = SanitizadorConteudo.sanitizarMensagemErro(
							"Erro ao persistir anúncio #" + anuncio.identificadorExterno() + ": " + ex.getMessage(),
							properties.getGithub().getToken()
					);
					log.warn("Erro ao persistir anúncio {} de {}: {}",
							anuncio.identificadorExterno(),
							fonte.getIdentificadorMural(),
							ultimoErroPersistencia);
				}
			}

			Instant fim = Instant.now(clock);
			int totalErros = resultado.errosContagem() + errosPersistencia;
			StatusExecucaoColeta statusFinal = resultado.status();
			if (errosPersistencia > 0 && statusFinal == StatusExecucaoColeta.SUCESSO) {
				statusFinal = (novos + atualizados + repetidos) > 0
						? StatusExecucaoColeta.SUCESSO_PARCIAL
						: StatusExecucaoColeta.FALHA;
			}

			String erroFinal = resultado.erroResumido() != null ? resultado.erroResumido() : ultimoErroPersistencia;

			execucao.setStatus(statusFinal);
			execucao.setFinalizadoEm(fim);
			execucao.setAnunciosRecebidos(resultado.anunciosRecebidos());
			execucao.setAnunciosValidos(resultado.anunciosValidos().size());
			execucao.setAnunciosNovos(novos);
			execucao.setAnunciosAtualizados(atualizados);
			execucao.setAnunciosRepetidos(repetidos);
			execucao.setAnunciosIgnorados(resultado.anunciosIgnorados());
			execucao.setErrosContagem(totalErros);
			execucao.setRateLimitRestante(resultado.rateLimitRestante());
			execucao.setRateLimitResetEm(resultado.rateLimitResetEm());
			execucao.setErroResumido(erroFinal);
			execucao = execucaoColetaRepository.save(execucao);

			if (statusFinal == StatusExecucaoColeta.SUCESSO || statusFinal == StatusExecucaoColeta.SUCESSO_PARCIAL) {
				fonte.setUltimaColetaSucessoEm(fim);
				if (statusFinal == StatusExecucaoColeta.SUCESSO && resultado.maiorAtualizacaoObservada() != null) {
					fonte.setCursorUltimaAtualizacao(resultado.maiorAtualizacaoObservada());
				}
				fonte.setAtualizadoEm(fim);
				configuracaoFonteRepository.save(fonte);
			}

			return mapearExecucaoDto(execucao);
		} catch (Exception ex) {
			Instant fim = Instant.now(clock);
			execucao.setStatus(StatusExecucaoColeta.FALHA);
			execucao.setFinalizadoEm(fim);
			execucao.setErrosContagem(execucao.getErrosContagem() + 1);
			execucao.setErroResumido(SanitizadorConteudo.sanitizarMensagemErro(
					"Falha inesperada na execução da coleta: " + ex.getMessage(),
					properties.getGithub().getToken()
			));
			return mapearExecucaoDto(execucaoColetaRepository.save(execucao));
		} finally {
			fontesEmExecucao.remove(chaveLock);
		}
	}

	public StatusColetaGlobalDto consultarStatusGlobal() {
		Instant agora = Instant.now(clock);
		List<ConfiguracaoFonte> fontes = configuracaoFonteRepository.findAllByOrderByIdAsc();
		List<ExecucaoColeta> execucoes = execucaoColetaRepository.findTop20ByOrderByIniciadoEmDesc();

		Instant ultimaGlobal = fontes.stream()
				.filter(ConfiguracaoFonte::isAtivo)
				.map(ConfiguracaoFonte::getUltimaColetaSucessoEm)
				.filter(i -> i != null)
				.max(Instant::compareTo)
				.orElse(null);

		int limiteHoras = Math.max(1, properties.getColeta().getDesatualizadoAposHoras());
		boolean desatualizados;
		String motivoEstado;

		if (ultimaGlobal == null) {
			desatualizados = true;
			motivoEstado = "Nenhuma coleta concluída ainda. Clique em 'Coletar agora' para buscar vagas nas fontes ativas.";
		} else {
			long horasDesdeUltima = Duration.between(ultimaGlobal, agora).toHours();
			if (horasDesdeUltima >= limiteHoras) {
				desatualizados = true;
				motivoEstado = "Dados coletados há " + horasDesdeUltima + " hora(s) (acima do limite de " + limiteHoras + "h).";
			} else {
				desatualizados = false;
				motivoEstado = "Catálogo atualizado a partir do banco local com múltiplas fontes ativas.";
			}
		}

		List<FonteConfiguradaDto> fontesDto = fontes.stream()
				.map(f -> new FonteConfiguradaDto(
						f.getId(),
						f.getFornecedor(),
						f.getIdentificadorMural(),
						f.getNomeExibicao(),
						f.isAtivo(),
						f.getCadenciaMinutos(),
						f.getCategoria(),
						f.getEstadoValidacao(),
						f.getMetodoAcesso(),
						f.getCredencialNecessaria(),
						f.getObservacaoValidacao(),
						f.getUltimaColetaSucessoEm(),
						f.getCursorUltimaAtualizacao()
				))
				.toList();

		List<ExecucaoColetaDto> execucoesDto = execucoes.stream()
				.map(this::mapearExecucaoDto)
				.toList();

		return new StatusColetaGlobalDto(
				ultimaGlobal,
				desatualizados,
				motivoEstado,
				!fontesEmExecucao.isEmpty(),
				fontesDto,
				execucoesDto
		);
	}

	private ExecucaoColetaDto mapearExecucaoDto(ExecucaoColeta e) {
		return new ExecucaoColetaDto(
				e.getId(),
				e.getFornecedor(),
				e.getIdentificadorMural(),
				e.getStatus().name(),
				e.getStatus().getRotulo(),
				e.getIniciadoEm(),
				e.getFinalizadoEm(),
				e.getAnunciosRecebidos(),
				e.getAnunciosValidos(),
				e.getAnunciosNovos(),
				e.getAnunciosAtualizados(),
				e.getAnunciosRepetidos(),
				e.getAnunciosIgnorados(),
				e.getErrosContagem(),
				e.getRateLimitRestante(),
				e.getRateLimitResetEm(),
				e.getErroResumido()
		);
	}
}

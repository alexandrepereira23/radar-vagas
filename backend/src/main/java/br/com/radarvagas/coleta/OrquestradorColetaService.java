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
			boolean temToken = possuiCredencialConfigurada(GitHubIssueClassificador.FORNECEDOR_GITHUB);
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
						repo.isAtivo() ? "CONFIGURADA_NAO_VALIDADA" : "EXPERIMENTAL_ATIVAVEL",
						"API REST Oficial GitHub Issues (/repos/" + idMural + "/issues)",
						temToken ? "GITHUB_TOKEN configurado (5000 req/h)" : "Opcional: GITHUB_TOKEN (sem token: 60 req/h)",
						"Fonte configurada; validação confirmada após coleta bem-sucedida.",
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
				String fornecedor = fc.getFornecedor().trim();
				boolean ativoEfetivo = fc.isAtivo();
				String estadoEfetivo = fc.getEstadoValidacao() != null ? fc.getEstadoValidacao() : "CONFIGURADA_NAO_VALIDADA";
				String obsEfetiva = fc.getObservacaoValidacao();
				String credNecessaria = fc.getCredencialNecessaria();

				if ("JSEARCH_API".equalsIgnoreCase(fornecedor)) {
					ativoEfetivo = false;
					estadoEfetivo = "NAO_IMPLEMENTADA";
					credNecessaria = "Desnecessária nesta rodada (conector não implementado; não contratar plano)";
					obsEfetiva = "Conector ainda não implementado no código. Preencher JSEARCH_API_KEY não ativa esta fonte.";
				} else if (JoobleConector.FORNECEDOR.equals(fornecedor)) {
					boolean temChave = possuiCredencialConfigurada(JoobleConector.FORNECEDOR);
					if (temChave) {
						ativoEfetivo = true;
						estadoEfetivo = "CONFIGURADA_NAO_VALIDADA";
						obsEfetiva = "Credencial JOOBLE_API_KEY presente no ambiente; aguardando coleta com sucesso para confirmar validação.";
					} else {
						ativoEfetivo = false;
						estadoEfetivo = "BLOQUEADA_CREDENCIAL";
						obsEfetiva = "Conector implementado. Bloqueado até que a variável JOOBLE_API_KEY seja configurada.";
					}
				} else if (AdzunaConector.FORNECEDOR.equals(fornecedor)) {
					boolean temChave = possuiCredencialConfigurada(AdzunaConector.FORNECEDOR);
					if (temChave) {
						ativoEfetivo = true;
						estadoEfetivo = "CONFIGURADA_NAO_VALIDADA";
						obsEfetiva = "Credenciais ADZUNA_APP_ID e ADZUNA_APP_KEY presentes no ambiente; aguardando coleta com sucesso para confirmar validação.";
					} else {
						ativoEfetivo = false;
						estadoEfetivo = "BLOQUEADA_CREDENCIAL";
						obsEfetiva = "Conector implementado. Bloqueado até que ADZUNA_APP_ID e ADZUNA_APP_KEY sejam configurados.";
					}
				} else if (ativoEfetivo && "ATIVA_VALIDADA".equalsIgnoreCase(estadoEfetivo)) {
					estadoEfetivo = "CONFIGURADA_NAO_VALIDADA";
				}

				sincronizarConfiguracaoFonte(
						fornecedor,
						fc.getIdentificador().trim(),
						fc.getNomeExibicao() != null ? fc.getNomeExibicao() : fc.getIdentificador().trim(),
						ativoEfetivo,
						fc.getCadenciaMinutos() > 0 ? fc.getCadenciaMinutos() : 360,
						fc.getCategoria() != null ? fc.getCategoria() : "OUTRA",
						estadoEfetivo,
						fc.getMetodoAcesso(),
						credNecessaria,
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
			String estadoInicialConfiguracao,
			String metodoAcesso,
			String credencialNecessaria,
			String observacaoInicialConfiguracao,
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
		cfg.setMetodoAcesso(metodoAcesso);
		cfg.setCredencialNecessaria(credencialNecessaria);

		// Preserva evidência persistida (validação real, erro de autenticação, rate limit ou falha de execução)
		// sem resetar para validado em cada reinício
		if (!ativo) {
			cfg.setEstadoValidacao(estadoInicialConfiguracao);
			cfg.setObservacaoValidacao(observacaoInicialConfiguracao);
		} else if (existente.isPresent()) {
			String estadoPersistido = cfg.getEstadoValidacao();
			boolean temEvidenciaFalha = "FALHA_AUTENTICACAO".equalsIgnoreCase(estadoPersistido)
					|| "FALHA_EXECUCAO".equalsIgnoreCase(estadoPersistido)
					|| "LIMITE_ATINGIDO".equalsIgnoreCase(estadoPersistido);
			boolean temEvidenciaSucesso = cfg.getUltimaColetaSucessoEm() != null
					&& "ATIVA_VALIDADA".equalsIgnoreCase(estadoPersistido);

			if (!temEvidenciaFalha && !temEvidenciaSucesso) {
				cfg.setEstadoValidacao("CONFIGURADA_NAO_VALIDADA");
				cfg.setObservacaoValidacao(observacaoInicialConfiguracao);
			}
		} else {
			cfg.setEstadoValidacao(estadoInicialConfiguracao);
			cfg.setObservacaoValidacao(observacaoInicialConfiguracao);
		}

		cfg.setAtualizadoEm(agora);
		configuracaoFonteRepository.save(cfg);
	}

	public boolean possuiCredencialConfigurada(String fornecedor) {
		if (fornecedor == null) {
			return false;
		}
		return switch (fornecedor.trim().toUpperCase()) {
			case GitHubIssueClassificador.FORNECEDOR_GITHUB ->
					properties.getGithub().getToken() != null && !properties.getGithub().getToken().isBlank();
			case JoobleConector.FORNECEDOR ->
					properties.getJooble().getApiKey() != null && !properties.getJooble().getApiKey().isBlank();
			case AdzunaConector.FORNECEDOR ->
					properties.getAdzuna().getAppId() != null && !properties.getAdzuna().getAppId().isBlank()
							&& properties.getAdzuna().getAppKey() != null && !properties.getAdzuna().getAppKey().isBlank();
			case "JSEARCH_API" -> false;
			default -> true;
		};
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
			boolean temContinuacaoPendente = fonte.getPaginaContinuacao() != null && fonte.getPaginaContinuacao() > 1;
			boolean vencida = temContinuacaoPendente
					|| (ultima == null)
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

		if ("NAO_IMPLEMENTADA".equalsIgnoreCase(fonte.getEstadoValidacao())
				|| "JSEARCH_API".equalsIgnoreCase(fonte.getFornecedor())) {
			ExecucaoColeta naoImpl = new ExecucaoColeta();
			naoImpl.setFornecedor(fonte.getFornecedor());
			naoImpl.setIdentificadorMural(fonte.getIdentificadorMural());
			naoImpl.setStatus(StatusExecucaoColeta.NAO_IMPLEMENTADA);
			naoImpl.setIniciadoEm(inicio);
			naoImpl.setFinalizadoEm(inicio);
			naoImpl.setErroResumido(fonte.getObservacaoValidacao() != null
					? fonte.getObservacaoValidacao()
					: "Conector ainda não implementado para o fornecedor: " + fonte.getFornecedor());
			return mapearExecucaoDto(execucaoColetaRepository.save(naoImpl));
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
				execucao.setStatus(ehBloqueioCredencial ? StatusExecucaoColeta.BLOQUEADA_CREDENCIAL : StatusExecucaoColeta.NAO_IMPLEMENTADA);
				execucao.setFinalizadoEm(Instant.now(clock));
				execucao.setErrosContagem(0);
				execucao.setErroResumido(fonte.getObservacaoValidacao() != null
						? fonte.getObservacaoValidacao()
						: "Conector não implementado para o fornecedor: " + fonte.getFornecedor());
				return mapearExecucaoDto(execucaoColetaRepository.save(execucao));
			}

			int paginaAntesDaExecucao = (fonte.getPaginaContinuacao() != null && fonte.getPaginaContinuacao() > 1)
					? fonte.getPaginaContinuacao()
					: 1;

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
							todosSegredosConfigurados()
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
			if (errosPersistencia > 0 && (statusFinal == StatusExecucaoColeta.SUCESSO || statusFinal == StatusExecucaoColeta.SUCESSO_PARCIAL)) {
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

			atualizarEstadoECursorDaFonte(
					fonte,
					resultado,
					statusFinal,
					errosPersistencia,
					paginaAntesDaExecucao,
					erroFinal,
					fim
			);

			return mapearExecucaoDto(execucao);
		} catch (Exception ex) {
			Instant fim = Instant.now(clock);
			String erroSanitizado = SanitizadorConteudo.sanitizarMensagemErro(
					"Falha inesperada na execução da coleta: " + ex.getMessage(),
					todosSegredosConfigurados()
			);
			execucao.setStatus(StatusExecucaoColeta.FALHA);
			execucao.setFinalizadoEm(fim);
			execucao.setErrosContagem(execucao.getErrosContagem() + 1);
			execucao.setErroResumido(erroSanitizado);
			execucao = execucaoColetaRepository.save(execucao);

			fonte.setEstadoValidacao("FALHA_EXECUCAO");
			fonte.setObservacaoValidacao(erroSanitizado);
			fonte.setAtualizadoEm(fim);
			configuracaoFonteRepository.save(fonte);

			return mapearExecucaoDto(execucao);
		} finally {
			fontesEmExecucao.remove(chaveLock);
		}
	}

	private void atualizarEstadoECursorDaFonte(
			ConfiguracaoFonte fonte,
			ResultadoColetaFonte resultado,
			StatusExecucaoColeta statusFinal,
			int errosPersistencia,
			int paginaAntesDaExecucao,
			String erroFinal,
			Instant fim
	) {
		Instant maiorObservadaAcumulada = maxInstant(
				fonte.getMaiorAtualizacaoJanela(),
				resultado.maiorAtualizacaoObservada()
		);

		if (statusFinal == StatusExecucaoColeta.SUCESSO || statusFinal == StatusExecucaoColeta.SUCESSO_PARCIAL) {
			fonte.setUltimaColetaSucessoEm(fim);
			fonte.setEstadoValidacao("ATIVA_VALIDADA");

			if (errosPersistencia > 0) {
				// Se houve erro ao persistir item desta página, mantém a página atual para reexecução idempotente
				fonte.setPaginaContinuacao(paginaAntesDaExecucao);
				fonte.setCursorJanelaAtual(resultado.cursorJanelaInicio());
				fonte.setMaiorAtualizacaoJanela(maiorObservadaAcumulada);
			} else if (resultado.coletaIncompleta() && resultado.proximaPaginaContinuacao() != null) {
				// Coleta truncada por limite de páginas/volume: salva continuação sem avançar cursorUltimaAtualizacao!
				fonte.setPaginaContinuacao(resultado.proximaPaginaContinuacao());
				fonte.setCursorJanelaAtual(resultado.cursorJanelaInicio());
				fonte.setMaiorAtualizacaoJanela(maiorObservadaAcumulada);
			} else if (statusFinal == StatusExecucaoColeta.SUCESSO && !resultado.coletaIncompleta()) {
				// Janela concluída integralmente sem truncamento e sem erros de persistência: avança cursor!
				if (maiorObservadaAcumulada != null) {
					fonte.setCursorUltimaAtualizacao(maiorObservadaAcumulada);
				}
				fonte.setPaginaContinuacao(null);
				fonte.setCursorJanelaAtual(null);
				fonte.setMaiorAtualizacaoJanela(null);
			}

			fonte.setObservacaoValidacao(erroFinal != null
					? erroFinal
					: "Coleta validada com sucesso (" + resultado.anunciosRecebidos() + " recebido(s), "
							+ resultado.anunciosValidos().size() + " válido(s)).");
		} else if (statusFinal == StatusExecucaoColeta.BLOQUEADA_CREDENCIAL) {
			fonte.setEstadoValidacao("BLOQUEADA_CREDENCIAL");
			if (erroFinal != null) {
				fonte.setObservacaoValidacao(erroFinal);
			}
		} else if (statusFinal == StatusExecucaoColeta.LIMITE_ATINGIDO) {
			fonte.setEstadoValidacao("LIMITE_ATINGIDO");
			if (erroFinal != null) {
				fonte.setObservacaoValidacao(erroFinal);
			}
		} else if (statusFinal == StatusExecucaoColeta.FALHA) {
			fonte.setEstadoValidacao(resultado.falhaAutenticacao() ? "FALHA_AUTENTICACAO" : "FALHA_EXECUCAO");
			if (erroFinal != null) {
				fonte.setObservacaoValidacao(erroFinal);
			}
		}

		fonte.setAtualizadoEm(fim);
		configuracaoFonteRepository.save(fonte);
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

		int limiteHorasGlobal = Math.max(1, properties.getColeta().getDesatualizadoAposHoras());

		int fontesAtivasTotal = 0;
		int fontesAtualizadas = 0;
		int fontesParciais = 0;
		int fontesComFalha = 0;
		int fontesDesatualizadas = 0;
		List<String> nomesComFalha = new ArrayList<>();
		List<String> nomesDesatualizadas = new ArrayList<>();
		List<String> nomesParciais = new ArrayList<>();

		List<FonteConfiguradaDto> fontesDto = new ArrayList<>();

		for (ConfiguracaoFonte f : fontes) {
			Optional<ExecucaoColeta> ultimaExecOpt = execucaoColetaRepository
					.findFirstByFornecedorAndIdentificadorMuralOrderByIniciadoEmDesc(
							f.getFornecedor(),
							f.getIdentificadorMural()
					);

			String ultimoStatusExec = ultimaExecOpt.map(e -> e.getStatus().name()).orElse(null);
			String ultimoErroExec = ultimaExecOpt.map(ExecucaoColeta::getErroResumido).orElse(null);
			boolean credConfigurada = possuiCredencialConfigurada(f.getFornecedor());

			String situacaoFonte;
			boolean fonteDesatualizada;

			if (!f.isAtivo()) {
				situacaoFonte = "INATIVA";
				fonteDesatualizada = false;
			} else {
				fontesAtivasTotal++;
				boolean estadoIndicaFalha = "FALHA_AUTENTICACAO".equalsIgnoreCase(f.getEstadoValidacao())
						|| "FALHA_EXECUCAO".equalsIgnoreCase(f.getEstadoValidacao())
						|| "LIMITE_ATINGIDO".equalsIgnoreCase(f.getEstadoValidacao());
				boolean ultimaExecFalhou = ultimaExecOpt.isPresent() && (
						ultimaExecOpt.get().getStatus() == StatusExecucaoColeta.FALHA
						|| ultimaExecOpt.get().getStatus() == StatusExecucaoColeta.LIMITE_ATINGIDO
						|| ultimaExecOpt.get().getStatus() == StatusExecucaoColeta.BLOQUEADA_CREDENCIAL
						|| ultimaExecOpt.get().getStatus() == StatusExecucaoColeta.BLOQUEADA_ACESSO
				);

				if (estadoIndicaFalha || ultimaExecFalhou) {
					situacaoFonte = "COM_FALHA";
					fonteDesatualizada = true;
					fontesComFalha++;
					nomesComFalha.add(f.getNomeExibicao());
				} else if (f.getUltimaColetaSucessoEm() == null) {
					situacaoFonte = "NUNCA_COLETADA";
					fonteDesatualizada = true;
					fontesDesatualizadas++;
					nomesDesatualizadas.add(f.getNomeExibicao());
				} else {
					long minutosDesdeSucesso = Duration.between(f.getUltimaColetaSucessoEm(), agora).toMinutes();
					long limiteMinutosFonte = Math.min(
							limiteHorasGlobal * 60L,
							Math.max(60L, (long) f.getCadenciaMinutos() * 2L)
					);
					boolean temContinuacao = f.getPaginaContinuacao() != null && f.getPaginaContinuacao() > 1;
					boolean ultimaFoiParcial = ultimaExecOpt.isPresent()
							&& ultimaExecOpt.get().getStatus() == StatusExecucaoColeta.SUCESSO_PARCIAL;

					if (minutosDesdeSucesso >= limiteMinutosFonte) {
						situacaoFonte = "DESATUALIZADA";
						fonteDesatualizada = true;
						fontesDesatualizadas++;
						nomesDesatualizadas.add(f.getNomeExibicao());
					} else if (temContinuacao || ultimaFoiParcial) {
						situacaoFonte = "ATUALIZACAO_PARCIAL";
						fonteDesatualizada = false;
						fontesParciais++;
						nomesParciais.add(f.getNomeExibicao());
					} else {
						situacaoFonte = "ATUALIZADA";
						fonteDesatualizada = false;
						fontesAtualizadas++;
					}
				}
			}

			fontesDto.add(new FonteConfiguradaDto(
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
					f.getCursorUltimaAtualizacao(),
					credConfigurada,
					situacaoFonte,
					fonteDesatualizada,
					ultimoStatusExec,
					ultimoErroExec,
					f.getPaginaContinuacao()
			));
		}

		boolean desatualizados;
		boolean atualizacaoParcial;
		String motivoEstado;

		if (fontesAtivasTotal == 0) {
			desatualizados = true;
			atualizacaoParcial = false;
			motivoEstado = "Nenhuma fonte ativa configurada no catálogo.";
		} else if (ultimaGlobal == null) {
			desatualizados = true;
			atualizacaoParcial = false;
			if (fontesComFalha > 0) {
				motivoEstado = "Nenhuma coleta concluída com sucesso (" + fontesComFalha
						+ " fonte(s) ativa(s) com falha: " + resumirNomes(nomesComFalha) + ").";
			} else {
				motivoEstado = "Nenhuma coleta concluída ainda. Clique em 'Coletar agora' para buscar vagas nas fontes ativas.";
			}
		} else if (fontesComFalha > 0 || fontesDesatualizadas > 0) {
			int emDia = fontesAtualizadas + fontesParciais;
			desatualizados = true;
			atualizacaoParcial = emDia > 0;
			List<String> partes = new ArrayList<>();
			if (fontesComFalha > 0) {
				partes.add(fontesComFalha + " com falha (" + resumirNomes(nomesComFalha) + ")");
			}
			if (fontesDesatualizadas > 0) {
				partes.add(fontesDesatualizadas + " desatualizada(s)/sem coleta (" + resumirNomes(nomesDesatualizadas) + ")");
			}
			if (fontesParciais > 0) {
				partes.add(fontesParciais + " com coleta parcial (" + resumirNomes(nomesParciais) + ")");
			}
			motivoEstado = "Atualização parcial/degradada: " + emDia + " de " + fontesAtivasTotal
					+ " fonte(s) ativa(s) em dia; " + String.join(", ", partes) + ".";
		} else if (fontesParciais > 0) {
			desatualizados = false;
			atualizacaoParcial = true;
			motivoEstado = "Atualização parcial: " + fontesParciais + " de " + fontesAtivasTotal
					+ " fonte(s) ativa(s) com coleta parcial ou paginação pendente de continuação ("
					+ resumirNomes(nomesParciais) + ").";
		} else {
			desatualizados = false;
			atualizacaoParcial = false;
			motivoEstado = "Catálogo atualizado: todas as " + fontesAtivasTotal
					+ " fonte(s) ativa(s) coletadas com sucesso dentro da cadência.";
		}

		List<ExecucaoColetaDto> execucoesDto = execucoes.stream()
				.map(this::mapearExecucaoDto)
				.toList();

		return new StatusColetaGlobalDto(
				ultimaGlobal,
				desatualizados,
				motivoEstado,
				!fontesEmExecucao.isEmpty(),
				fontesDto,
				execucoesDto,
				atualizacaoParcial,
				fontesAtivasTotal,
				fontesAtualizadas,
				fontesComFalha,
				fontesDesatualizadas,
				fontesParciais
		);
	}

	private String resumirNomes(List<String> nomes) {
		if (nomes == null || nomes.isEmpty()) {
			return "";
		}
		if (nomes.size() <= 3) {
			return String.join(", ", nomes);
		}
		return String.join(", ", nomes.subList(0, 3)) + " e +" + (nomes.size() - 3);
	}

	private Instant maxInstant(Instant a, Instant b) {
		if (a == null) {
			return b;
		}
		if (b == null) {
			return a;
		}
		return a.isAfter(b) ? a : b;
	}

	private String[] todosSegredosConfigurados() {
		return new String[]{
				properties.getGithub().getToken(),
				properties.getJooble().getApiKey(),
				properties.getAdzuna().getAppId(),
				properties.getAdzuna().getAppKey()
		};
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

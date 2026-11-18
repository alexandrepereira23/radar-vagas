package br.com.radarvagas.coleta;

import br.com.radarvagas.catalogo.SanitizadorConteudo;
import br.com.radarvagas.config.RadarProperties;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class GitHubIssuesConector implements ConectorFonteVagas {

	private static final Logger log = LoggerFactory.getLogger(GitHubIssuesConector.class);

	private final RadarProperties properties;
	private final GitHubIssueClassificador classificador;
	private final ObjectMapper objectMapper;
	private final HttpClient httpClient;

	public GitHubIssuesConector(
			RadarProperties properties,
			GitHubIssueClassificador classificador,
			ObjectMapper objectMapper
	) {
		this.properties = properties;
		this.classificador = classificador;
		this.objectMapper = objectMapper;
		int timeoutSeg = Math.max(2, properties.getGithub().getTimeoutSegundos());
		this.httpClient = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(timeoutSeg))
				.followRedirects(HttpClient.Redirect.NEVER)
				.build();
	}

	@Override
	public String fornecedor() {
		return GitHubIssueClassificador.FORNECEDOR_GITHUB;
	}

	@Override
	public ResultadoColetaFonte coletar(ConfiguracaoFonte configuracao) {
		String repositorio = configuracao.getIdentificadorMural();
		int porPagina = Math.max(1, Math.min(100, properties.getGithub().getItensPorPagina()));
		int maxPaginas = Math.max(1, properties.getGithub().getMaxPaginasPorExecucao());

		Instant sinceIncremental = calcularSinceComSobreposicao(configuracao.getCursorUltimaAtualizacao());

		List<AnuncioExtraido> anunciosValidos = new ArrayList<>();
		int recebidos = 0;
		int ignorados = 0;
		int erros = 0;
		Integer rateLimitRestante = null;
		Instant rateLimitResetEm = null;
		Instant maiorUpdatedObservado = configuracao.getCursorUltimaAtualizacao();
		String erroResumido = null;
		boolean limiteAtingido = false;

		for (int pagina = 1; pagina <= maxPaginas; pagina++) {
			String url = montarUrlPagina(repositorio, porPagina, pagina, sinceIncremental);
			RespostaHttpPagina respostaPagina = requisitarComRetentativa(url);

			if (respostaPagina.rateLimitRestante() != null) {
				rateLimitRestante = respostaPagina.rateLimitRestante();
			}
			if (respostaPagina.rateLimitResetEm() != null) {
				rateLimitResetEm = respostaPagina.rateLimitResetEm();
			}

			if (respostaPagina.erroFatal() != null) {
				erros++;
				erroResumido = SanitizadorConteudo.sanitizarMensagemErro(
						respostaPagina.erroFatal(),
						properties.getGithub().getToken()
				);
				limiteAtingido = respostaPagina.limiteAtingido();
				break;
			}

			try {
				JsonNode raiz = objectMapper.readTree(respostaPagina.corpo());
				if (raiz == null || !raiz.isArray()) {
					erros++;
					erroResumido = "Resposta inesperada da API do GitHub (não é uma lista JSON).";
					break;
				}

				int itensNaPagina = raiz.size();
				if (itensNaPagina == 0) {
					break;
				}

				for (JsonNode item : raiz) {
					recebidos++;
					try {
						GitHubIssueClassificador.DadosBrutosIssue bruta = mapearNoJson(repositorio, item);
						if (bruta.updatedAt() != null && (maiorUpdatedObservado == null || bruta.updatedAt().isAfter(maiorUpdatedObservado))) {
							maiorUpdatedObservado = bruta.updatedAt();
						}
						Optional<AnuncioExtraido> extraido = classificador.classificar(bruta);
						if (extraido.isPresent()) {
							anunciosValidos.add(extraido.get());
						} else {
							ignorados++;
						}
					} catch (Exception itemEx) {
						erros++;
						erroResumido = SanitizadorConteudo.sanitizarMensagemErro(
								"Erro ao processar item da fonte: " + itemEx.getMessage(),
								properties.getGithub().getToken()
						);
					}
				}

				if (rateLimitRestante != null && rateLimitRestante <= 0) {
					limiteAtingido = true;
					erroResumido = "Limite de requisições da API do GitHub atingido após a página " + pagina + ".";
					break;
				}

				if (itensNaPagina < porPagina) {
					break;
				}
			} catch (Exception parseEx) {
				erros++;
				erroResumido = SanitizadorConteudo.sanitizarMensagemErro(
						"Falha ao interpretar JSON do GitHub: " + parseEx.getMessage(),
						properties.getGithub().getToken()
				);
				break;
			}
		}

		StatusExecucaoColeta statusFinal = determinarStatusFinal(
				limiteAtingido,
				erros,
				recebidos,
				anunciosValidos.size()
		);

		return new ResultadoColetaFonte(
				statusFinal,
				anunciosValidos,
				recebidos,
				ignorados,
				erros,
				rateLimitRestante,
				rateLimitResetEm,
				maiorUpdatedObservado,
				erroResumido
		);
	}

	private StatusExecucaoColeta determinarStatusFinal(
			boolean limiteAtingido,
			int erros,
			int recebidos,
			int validos
	) {
		if (limiteAtingido) {
			return recebidos > 0 ? StatusExecucaoColeta.SUCESSO_PARCIAL : StatusExecucaoColeta.LIMITE_ATINGIDO;
		}
		if (erros > 0) {
			return (recebidos > 0 || validos > 0)
					? StatusExecucaoColeta.SUCESSO_PARCIAL
					: StatusExecucaoColeta.FALHA;
		}
		return StatusExecucaoColeta.SUCESSO;
	}

	private Instant calcularSinceComSobreposicao(Instant cursorAtual) {
		if (cursorAtual == null) {
			return null;
		}
		int minutosSobreposicao = Math.max(0, properties.getGithub().getSobreposicaoIncrementalMinutos());
		return cursorAtual.minus(minutosSobreposicao, ChronoUnit.MINUTES);
	}

	private String montarUrlPagina(String repositorio, int porPagina, int pagina, Instant since) {
		String base = properties.getGithub().getApiBaseUrl();
		if (base.endsWith("/")) {
			base = base.substring(0, base.length() - 1);
		}
		StringBuilder sb = new StringBuilder(base)
				.append("/repos/")
				.append(repositorio)
				.append("/issues?state=all&sort=updated&direction=desc&per_page=")
				.append(porPagina)
				.append("&page=")
				.append(pagina);
		if (since != null) {
			sb.append("&since=").append(URLEncoder.encode(since.toString(), StandardCharsets.UTF_8));
		}
		return sb.toString();
	}

	private record RespostaHttpPagina(
			String corpo,
			Integer rateLimitRestante,
			Instant rateLimitResetEm,
			boolean limiteAtingido,
			String erroFatal
	) {
	}

	private RespostaHttpPagina requisitarComRetentativa(String url) {
		int maxTentativas = Math.max(1, properties.getGithub().getMaxTentativas());
		long esperaBaseMs = Math.max(10L, properties.getGithub().getEsperaInicialMs());
		int timeoutSeg = Math.max(2, properties.getGithub().getTimeoutSegundos());

		Integer ultimoRateRestante = null;
		Instant ultimoRateReset = null;

		for (int tentativa = 1; tentativa <= maxTentativas; tentativa++) {
			try {
				HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
						.uri(URI.create(url))
						.timeout(Duration.ofSeconds(timeoutSeg))
						.header("Accept", "application/vnd.github+json")
						.header("X-GitHub-Api-Version", "2022-11-28")
						.header("User-Agent", properties.getGithub().getUserAgent())
						.GET();

				String token = properties.getGithub().getToken();
				if (token != null && !token.isBlank()) {
					reqBuilder.header("Authorization", "Bearer " + token.trim());
				}

				HttpResponse<String> response = httpClient.send(
						reqBuilder.build(),
						HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
				);

				HttpHeaders headers = response.headers();
				ultimoRateRestante = extrairInteiroHeader(headers, "x-ratelimit-remaining");
				ultimoRateReset = extrairEpochHeader(headers, "x-ratelimit-reset");
				Integer retryAfterSeg = extrairInteiroHeader(headers, "retry-after");

				int status = response.statusCode();
				if (status >= 200 && status < 300) {
					return new RespostaHttpPagina(
							response.body(),
							ultimoRateRestante,
							ultimoRateReset,
							false,
							null
					);
				}

				boolean ehRateLimit = status == 429
						|| (status == 403 && ((ultimoRateRestante != null && ultimoRateRestante == 0)
						|| contemIndicacaoRateLimit(response.body())));

				if (ehRateLimit) {
					if (tentativa < maxTentativas && retryAfterSeg != null && retryAfterSeg <= 5) {
						aguardarMs(retryAfterSeg * 1000L);
						continue;
					}
					String msgLimite = "Limite da API do GitHub atingido (HTTP " + status + ")"
							+ (ultimoRateReset != null ? ". Reset previsto em " + ultimoRateReset : "")
							+ (retryAfterSeg != null ? ". Retry-After: " + retryAfterSeg + "s" : "")
							+ ".";
					return new RespostaHttpPagina(null, ultimoRateRestante, ultimoRateReset, true, msgLimite);
				}

				if (status >= 500 && tentativa < maxTentativas) {
					long espera = esperaBaseMs * (1L << (tentativa - 1));
					aguardarMs(espera);
					continue;
				}

				return new RespostaHttpPagina(
						null,
						ultimoRateRestante,
						ultimoRateReset,
						false,
						"Resposta HTTP " + status + " ao consultar GitHub Issues."
				);
			} catch (Exception ex) {
				if (tentativa < maxTentativas) {
					long espera = esperaBaseMs * (1L << (tentativa - 1));
					aguardarMs(espera);
					continue;
				}
				log.warn("Falha de comunicação com GitHub Issues após {} tentativa(s): {}",
						tentativa,
						SanitizadorConteudo.sanitizarMensagemErro(ex.getMessage(), properties.getGithub().getToken()));
				return new RespostaHttpPagina(
						null,
						ultimoRateRestante,
						ultimoRateReset,
						false,
						"Erro de rede ao acessar GitHub Issues: " + ex.getClass().getSimpleName() + " - " + ex.getMessage()
				);
			}
		}

		return new RespostaHttpPagina(null, ultimoRateRestante, ultimoRateReset, false, "Tentativas esgotadas.");
	}

	private GitHubIssueClassificador.DadosBrutosIssue mapearNoJson(String repositorio, JsonNode item) {
		String numero = textoOuNulo(item, "number");
		if (numero == null) {
			numero = textoOuNulo(item, "id");
		}
		String titulo = textoOuNulo(item, "title");
		String corpo = textoOuNulo(item, "body");
		String estado = textoOuNulo(item, "state");
		String htmlUrl = textoOuNulo(item, "html_url");

		boolean pullRequest = item.hasNonNull("pull_request");

		List<String> labels = new ArrayList<>();
		JsonNode labelsNode = item.get("labels");
		if (labelsNode != null && labelsNode.isArray()) {
			for (JsonNode l : labelsNode) {
				if (l.isTextual()) {
					labels.add(l.asText());
				} else if (l.hasNonNull("name")) {
					labels.add(l.get("name").asText());
				}
			}
		}

		Instant createdAt = parseInstantSeguro(textoOuNulo(item, "created_at"));
		Instant updatedAt = parseInstantSeguro(textoOuNulo(item, "updated_at"));

		return new GitHubIssueClassificador.DadosBrutosIssue(
				repositorio,
				numero,
				titulo,
				corpo,
				estado,
				pullRequest,
				labels,
				htmlUrl,
				createdAt,
				updatedAt
		);
	}

	private String textoOuNulo(JsonNode node, String campo) {
		if (node == null || !node.hasNonNull(campo)) {
			return null;
		}
		JsonNode valor = node.get(campo);
		if (valor.isNull()) {
			return null;
		}
		String texto = valor.asText();
		return (texto == null || texto.isBlank() || "null".equalsIgnoreCase(texto)) ? null : texto;
	}

	private Instant parseInstantSeguro(String valor) {
		if (valor == null || valor.isBlank()) {
			return null;
		}
		try {
			return Instant.parse(valor.trim());
		} catch (Exception e) {
			return null;
		}
	}

	private Integer extrairInteiroHeader(HttpHeaders headers, String nome) {
		Optional<String> val = headers.firstValue(nome);
		if (val.isEmpty()) {
			return null;
		}
		try {
			return Integer.parseInt(val.get().trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private Instant extrairEpochHeader(HttpHeaders headers, String nome) {
		Optional<String> val = headers.firstValue(nome);
		if (val.isEmpty()) {
			return null;
		}
		try {
			long epochSec = Long.parseLong(val.get().trim());
			return Instant.ofEpochSecond(epochSec);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private boolean contemIndicacaoRateLimit(String corpo) {
		if (corpo == null) {
			return false;
		}
		String lower = corpo.toLowerCase();
		return lower.contains("rate limit") || lower.contains("secondary rate limit");
	}

	private void aguardarMs(long ms) {
		try {
			Thread.sleep(Math.min(ms, 5_000L));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}

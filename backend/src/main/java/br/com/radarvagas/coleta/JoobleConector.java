package br.com.radarvagas.coleta;

import br.com.radarvagas.catalogo.ModalidadeTrabalho;
import br.com.radarvagas.catalogo.RegimeContratacao;
import br.com.radarvagas.catalogo.SanitizadorConteudo;
import br.com.radarvagas.config.RadarProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class JoobleConector implements ConectorFonteVagas {

	public static final String FORNECEDOR = "JOOBLE_API";

	private final RadarProperties properties;
	private final GitHubIssueClassificador classificador;
	private final ObjectMapper objectMapper;
	private final HttpClient httpClient;

	public JoobleConector(
			RadarProperties properties,
			GitHubIssueClassificador classificador,
			ObjectMapper objectMapper
	) {
		this.properties = properties;
		this.classificador = classificador;
		this.objectMapper = objectMapper;
		int timeoutSeg = Math.max(2, properties.getJooble().getTimeoutSegundos());
		this.httpClient = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(timeoutSeg))
				.followRedirects(HttpClient.Redirect.NEVER)
				.build();
	}

	@Override
	public String fornecedor() {
		return FORNECEDOR;
	}

	@Override
	public ResultadoColetaFonte coletar(ConfiguracaoFonte configuracao) {
		String apiKey = properties.getJooble().getApiKey();
		if (apiKey == null || apiKey.isBlank()) {
			return new ResultadoColetaFonte(
					StatusExecucaoColeta.BLOQUEADA_CREDENCIAL,
					List.of(),
					0,
					0,
					0,
					null,
					null,
					configuracao.getCursorUltimaAtualizacao(),
					"Credencial ausente: configure a variável de ambiente JOOBLE_API_KEY para habilitar o agregador Jooble Brasil."
			);
		}

		String base = properties.getJooble().getApiBaseUrl();
		if (base.endsWith("/")) {
			base = base.substring(0, base.length() - 1);
		}
		String url = base + "/api/" + apiKey.trim();
		String localizacaoPadrao = properties.getJooble().getLocalizacao() != null
				&& !properties.getJooble().getLocalizacao().isBlank()
				? properties.getJooble().getLocalizacao().trim()
				: "Brasil";

		List<String> termos = resolverTermosBusca();
		int maxItens = Math.max(1, properties.getJooble().getMaxItensPorBusca());
		int maxTermosExecucao = Math.max(1, properties.getJooble().getMaxTermosPorExecucao());

		int totalTermos = termos.size();
		int indiceInicial = (configuracao.getPaginaContinuacao() != null
				&& configuracao.getPaginaContinuacao() >= 1
				&& configuracao.getPaginaContinuacao() <= totalTermos)
				? configuracao.getPaginaContinuacao() - 1
				: 0;

		Map<String, AnuncioExtraido> validosPorId = new LinkedHashMap<>();
		int recebidos = 0;
		int ignorados = 0;
		int erros = 0;
		String erroResumido = null;
		boolean falhaAutenticacao = false;
		boolean limiteAtingido = false;
		boolean recorteVolume = false;
		int termosProcessados = 0;
		int ultimoIndiceConcluido = indiceInicial - 1;

		for (int i = indiceInicial; i < totalTermos && termosProcessados < maxTermosExecucao; i++) {
			if (validosPorId.size() >= maxItens) {
				recorteVolume = true;
				break;
			}
			String keywords = termos.get(i);
			termosProcessados++;

			try {
				String payloadJson = objectMapper.writeValueAsString(Map.of(
						"keywords", keywords,
						"location", localizacaoPadrao,
						"page", "1"
				));

				HttpRequest request = HttpRequest.newBuilder()
						.uri(URI.create(url))
						.timeout(Duration.ofSeconds(Math.max(2, properties.getJooble().getTimeoutSegundos())))
						.header("Content-Type", "application/json")
						.header("Accept", "application/json")
						.header("User-Agent", properties.getGithub().getUserAgent())
						.POST(HttpRequest.BodyPublishers.ofString(payloadJson, StandardCharsets.UTF_8))
						.build();

				HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
				int statusHttp = response.statusCode();

				if (statusHttp == 401 || statusHttp == 403) {
					erros++;
					falhaAutenticacao = true;
					erroResumido = "Falha de autenticação/autorização (HTTP " + statusHttp
							+ ") ao consultar Jooble API. Verifique a chave JOOBLE_API_KEY.";
					break;
				}
				if (statusHttp == 429) {
					erros++;
					limiteAtingido = true;
					erroResumido = "Limite de requisições atingido na Jooble API (HTTP 429).";
					break;
				}
				if (statusHttp < 200 || statusHttp >= 300) {
					erros++;
					erroResumido = "Resposta HTTP " + statusHttp + " ao consultar Jooble API.";
					break;
				}

				JsonNode raiz = objectMapper.readTree(response.body());
				JsonNode jobs = (raiz != null && raiz.isObject()) ? raiz.get("jobs") : null;
				if (jobs == null || !jobs.isArray()) {
					erros++;
					erroResumido = "Payload inválido da Jooble API (campo 'jobs' ausente ou não é uma lista JSON).";
					break;
				}

				for (JsonNode job : jobs) {
					if (validosPorId.size() >= maxItens) {
						recorteVolume = true;
						break;
					}
					recebidos++;
					String idExterno = textoOuNulo(job, "id");
					String titulo = textoOuNulo(job, "title");
					String empresa = textoOuNulo(job, "company");
					String localizacao = textoOuNulo(job, "location");
					String snippet = textoOuNulo(job, "snippet");
					String link = textoOuNulo(job, "link");

					Optional<AnuncioExtraido> extraido = classificador.classificarAnuncioExterno(
							FORNECEDOR,
							configuracao.getIdentificadorMural(),
							idExterno,
							titulo,
							empresa,
							snippet,
							localizacao,
							ModalidadeTrabalho.NAO_INFORMADA,
							RegimeContratacao.NAO_INFORMADO,
							null,
							List.of(),
							link,
							link,
							null,
							null,
							false,
							true,
							idExterno
					);

					if (extraido.isPresent()) {
						validosPorId.putIfAbsent(extraido.get().identificadorExterno(), extraido.get());
					} else {
						ignorados++;
					}
				}
				ultimoIndiceConcluido = i;
			} catch (Exception ex) {
				erros++;
				erroResumido = SanitizadorConteudo.sanitizarMensagemErro(
						"Erro ao consultar Jooble API: " + ex.getMessage(),
						apiKey
				);
				break;
			}
		}

		List<AnuncioExtraido> validos = new ArrayList<>(validosPorId.values());
		boolean faltamTermos = erros == 0 && (ultimoIndiceConcluido + 1 < totalTermos);
		boolean coletaIncompleta = faltamTermos || recorteVolume;
		Integer proximaContinuacao = null;
		if (erros > 0) {
			proximaContinuacao = indiceInicial + 1;
		} else if (coletaIncompleta) {
			int proximoIndice = (ultimoIndiceConcluido + 1) % totalTermos;
			proximaContinuacao = proximoIndice == 0 ? null : proximoIndice + 1;
			if (proximaContinuacao != null && erroResumido == null) {
				erroResumido = "Recorte por orçamento de cota/volume: consultados " + termosProcessados + " de "
						+ totalTermos + " termo(s); continuação agendada para o termo " + proximaContinuacao + ".";
			}
		}

		StatusExecucaoColeta status;
		if (limiteAtingido) {
			status = validos.isEmpty() ? StatusExecucaoColeta.LIMITE_ATINGIDO : StatusExecucaoColeta.SUCESSO_PARCIAL;
		} else if (erros > 0) {
			status = validos.isEmpty() ? StatusExecucaoColeta.FALHA : StatusExecucaoColeta.SUCESSO_PARCIAL;
		} else if (proximaContinuacao != null) {
			status = StatusExecucaoColeta.SUCESSO_PARCIAL;
		} else {
			status = StatusExecucaoColeta.SUCESSO;
		}

		return new ResultadoColetaFonte(
				status,
				validos,
				recebidos,
				ignorados,
				erros,
				null,
				null,
				configuracao.getCursorUltimaAtualizacao(),
				erroResumido,
				proximaContinuacao != null,
				proximaContinuacao,
				null,
				falhaAutenticacao
		);
	}

	private List<String> resolverTermosBusca() {
		List<String> configurados = properties.getJooble().getTermosBusca();
		if (configurados != null) {
			List<String> limpos = configurados.stream()
					.filter(s -> s != null && !s.isBlank())
					.map(String::trim)
					.distinct()
					.toList();
			if (!limpos.isEmpty()) {
				return limpos;
			}
		}
		return List.of("desenvolvedor software");
	}

	private String textoOuNulo(JsonNode node, String campo) {
		if (node == null || !node.hasNonNull(campo)) {
			return null;
		}
		String texto = node.get(campo).asText();
		return (texto == null || texto.isBlank() || "null".equalsIgnoreCase(texto)) ? null : texto;
	}
}

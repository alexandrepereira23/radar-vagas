package br.com.radarvagas.coleta;

import br.com.radarvagas.catalogo.ModalidadeTrabalho;
import br.com.radarvagas.catalogo.RegimeContratacao;
import br.com.radarvagas.catalogo.SanitizadorConteudo;
import br.com.radarvagas.config.RadarProperties;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class AdzunaConector implements ConectorFonteVagas {

	public static final String FORNECEDOR = "ADZUNA_API";

	private final RadarProperties properties;
	private final GitHubIssueClassificador classificador;
	private final ObjectMapper objectMapper;
	private final HttpClient httpClient;

	public AdzunaConector(
			RadarProperties properties,
			GitHubIssueClassificador classificador,
			ObjectMapper objectMapper
	) {
		this.properties = properties;
		this.classificador = classificador;
		this.objectMapper = objectMapper;
		int timeoutSeg = Math.max(2, properties.getAdzuna().getTimeoutSegundos());
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
		String appId = properties.getAdzuna().getAppId();
		String appKey = properties.getAdzuna().getAppKey();
		if (appId == null || appId.isBlank() || appKey == null || appKey.isBlank()) {
			return new ResultadoColetaFonte(
					StatusExecucaoColeta.BLOQUEADA_CREDENCIAL,
					List.of(),
					0,
					0,
					0,
					null,
					null,
					configuracao.getCursorUltimaAtualizacao(),
					"Credenciais ausentes: configure ADZUNA_APP_ID e ADZUNA_APP_KEY para habilitar o agregador Adzuna Brasil."
			);
		}

		String base = properties.getAdzuna().getApiBaseUrl();
		if (base.endsWith("/")) {
			base = base.substring(0, base.length() - 1);
		}
		int maxItens = Math.max(1, properties.getAdzuna().getMaxItensPorBusca());
		int maxTermosExecucao = Math.max(1, properties.getAdzuna().getMaxTermosPorExecucao());
		List<String> termos = resolverTermosBusca();
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
		Instant maiorPublicacao = configuracao.getCursorUltimaAtualizacao();

		for (int i = indiceInicial; i < totalTermos && termosProcessados < maxTermosExecucao; i++) {
			if (validosPorId.size() >= maxItens) {
				recorteVolume = true;
				break;
			}
			String termo = URLEncoder.encode(termos.get(i), StandardCharsets.UTF_8);
			StringBuilder urlBuilder = new StringBuilder(base)
					.append("/v1/api/jobs/br/search/1?app_id=")
					.append(URLEncoder.encode(appId.trim(), StandardCharsets.UTF_8))
					.append("&app_key=")
					.append(URLEncoder.encode(appKey.trim(), StandardCharsets.UTF_8))
					.append("&results_per_page=")
					.append(maxItens)
					.append("&what=")
					.append(termo);
			if (properties.getAdzuna().isOrdenarPorData()) {
				urlBuilder.append("&sort_by=date");
			}
			urlBuilder.append("&content-type=application/json");
			termosProcessados++;

			try {
				HttpRequest request = HttpRequest.newBuilder()
						.uri(URI.create(urlBuilder.toString()))
						.timeout(Duration.ofSeconds(Math.max(2, properties.getAdzuna().getTimeoutSegundos())))
						.header("Accept", "application/json")
						.header("User-Agent", properties.getGithub().getUserAgent())
						.GET()
						.build();

				HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
				int statusHttp = response.statusCode();

				if (statusHttp == 401 || statusHttp == 403) {
					erros++;
					falhaAutenticacao = true;
					erroResumido = "Falha de autenticação/autorização (HTTP " + statusHttp
							+ ") ao consultar Adzuna API. Verifique ADZUNA_APP_ID e ADZUNA_APP_KEY.";
					break;
				}
				if (statusHttp == 429) {
					erros++;
					limiteAtingido = true;
					erroResumido = "Limite de requisições atingido na Adzuna API (HTTP 429).";
					break;
				}
				if (statusHttp < 200 || statusHttp >= 300) {
					erros++;
					erroResumido = "Resposta HTTP " + statusHttp + " ao consultar Adzuna API.";
					break;
				}

				JsonNode raiz = objectMapper.readTree(response.body());
				JsonNode results = (raiz != null && raiz.isObject()) ? raiz.get("results") : null;
				if (results == null || !results.isArray()) {
					erros++;
					erroResumido = "Payload inválido da Adzuna API (campo 'results' ausente ou não é uma lista JSON).";
					break;
				}

				for (JsonNode item : results) {
					if (validosPorId.size() >= maxItens) {
						recorteVolume = true;
						break;
					}
					recebidos++;
					String idExterno = textoOuNulo(item, "id");
					String titulo = textoOuNulo(item, "title");
					String descricao = textoOuNulo(item, "description");
					String redirectUrl = textoOuNulo(item, "redirect_url");
					String empresa = item.hasNonNull("company") ? textoOuNulo(item.get("company"), "display_name") : null;
					String localizacao = item.hasNonNull("location") ? textoOuNulo(item.get("location"), "display_name") : null;

					Instant publicadoEm = null;
					String createdStr = textoOuNulo(item, "created");
					if (createdStr != null) {
						try {
							publicadoEm = Instant.parse(createdStr);
						} catch (Exception ignored) {
						}
					}
					if (publicadoEm != null && (maiorPublicacao == null || publicadoEm.isAfter(maiorPublicacao))) {
						maiorPublicacao = publicadoEm;
					}

					Optional<AnuncioExtraido> extraido = classificador.classificarAnuncioExterno(
							FORNECEDOR,
							configuracao.getIdentificadorMural(),
							idExterno,
							titulo,
							empresa,
							descricao,
							localizacao,
							ModalidadeTrabalho.NAO_INFORMADA,
							RegimeContratacao.NAO_INFORMADO,
							null,
							List.of(),
							redirectUrl,
							redirectUrl,
							publicadoEm,
							publicadoEm,
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
						"Erro ao consultar Adzuna API: " + ex.getMessage(),
						appId,
						appKey
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
				maiorPublicacao,
				erroResumido,
				proximaContinuacao != null,
				proximaContinuacao,
				null,
				falhaAutenticacao
		);
	}

	private List<String> resolverTermosBusca() {
		List<String> configurados = properties.getAdzuna().getTermosBusca();
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

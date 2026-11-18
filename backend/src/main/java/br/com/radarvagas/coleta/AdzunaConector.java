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
import java.util.List;
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
					"Credenciais ausentes: configure ADZUNA_APP_ID e ADZUNA_APP_KEY para ativar o agregador Adzuna Brasil."
			);
		}

		String base = properties.getAdzuna().getApiBaseUrl();
		if (base.endsWith("/")) {
			base = base.substring(0, base.length() - 1);
		}
		int maxItens = Math.max(1, properties.getAdzuna().getMaxItensPorBusca());
		String termo = URLEncoder.encode("desenvolvedor software", StandardCharsets.UTF_8);
		String url = base + "/v1/api/jobs/br/search/1?app_id=" + URLEncoder.encode(appId.trim(), StandardCharsets.UTF_8)
				+ "&app_key=" + URLEncoder.encode(appKey.trim(), StandardCharsets.UTF_8)
				+ "&results_per_page=" + maxItens
				+ "&what=" + termo
				+ "&content-type=application/json";

		List<AnuncioExtraido> validos = new ArrayList<>();
		int recebidos = 0;
		int ignorados = 0;
		int erros = 0;
		String erroResumido = null;

		try {
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(url))
					.timeout(Duration.ofSeconds(Math.max(2, properties.getAdzuna().getTimeoutSegundos())))
					.header("Accept", "application/json")
					.header("User-Agent", properties.getGithub().getUserAgent())
					.GET()
					.build();

			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				return new ResultadoColetaFonte(
						StatusExecucaoColeta.FALHA,
						List.of(),
						0,
						0,
						1,
						null,
						null,
						configuracao.getCursorUltimaAtualizacao(),
						"Resposta HTTP " + response.statusCode() + " ao consultar Adzuna API."
				);
			}

			JsonNode raiz = objectMapper.readTree(response.body());
			JsonNode results = raiz != null ? raiz.get("results") : null;
			if (results != null && results.isArray()) {
				for (JsonNode item : results) {
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
						validos.add(extraido.get());
					} else {
						ignorados++;
					}
				}
			}
		} catch (Exception ex) {
			erros++;
			erroResumido = SanitizadorConteudo.sanitizarMensagemErro(
					"Erro ao consultar Adzuna API: " + ex.getMessage(),
					appKey
			);
		}

		StatusExecucaoColeta status = erros > 0
				? (recebidos > 0 ? StatusExecucaoColeta.SUCESSO_PARCIAL : StatusExecucaoColeta.FALHA)
				: StatusExecucaoColeta.SUCESSO;

		return new ResultadoColetaFonte(
				status,
				validos,
				recebidos,
				ignorados,
				erros,
				null,
				null,
				configuracao.getCursorUltimaAtualizacao(),
				erroResumido
		);
	}

	private String textoOuNulo(JsonNode node, String campo) {
		if (node == null || !node.hasNonNull(campo)) {
			return null;
		}
		String texto = node.get(campo).asText();
		return (texto == null || texto.isBlank() || "null".equalsIgnoreCase(texto)) ? null : texto;
	}
}

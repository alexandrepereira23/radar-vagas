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
import java.time.Instant;
import java.util.ArrayList;
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
					"Credencial ausente: configure a variável de ambiente JOOBLE_API_KEY para ativar o agregador Jooble Brasil."
			);
		}

		String base = properties.getJooble().getApiBaseUrl();
		if (base.endsWith("/")) {
			base = base.substring(0, base.length() - 1);
		}
		String url = base + "/api/" + apiKey.trim();
		String keywords = configuracao.getIdentificadorMural().replace("-", " ");
		int maxItens = Math.max(1, properties.getJooble().getMaxItensPorBusca());

		List<AnuncioExtraido> validos = new ArrayList<>();
		int recebidos = 0;
		int ignorados = 0;
		int erros = 0;
		String erroResumido = null;

		try {
			String payloadJson = objectMapper.writeValueAsString(Map.of(
					"keywords", keywords,
					"location", "Brasil",
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
						"Resposta HTTP " + response.statusCode() + " ao consultar Jooble API."
				);
			}

			JsonNode raiz = objectMapper.readTree(response.body());
			JsonNode jobs = raiz != null ? raiz.get("jobs") : null;
			if (jobs != null && jobs.isArray()) {
				for (JsonNode job : jobs) {
					if (validos.size() >= maxItens) {
						break;
					}
					recebidos++;
					String idExterno = textoOuNulo(job, "id");
					String titulo = textoOuNulo(job, "title");
					String empresa = textoOuNulo(job, "company");
					String localizacao = textoOuNulo(job, "location");
					String snippet = textoOuNulo(job, "snippet");
					String link = textoOuNulo(job, "link");

					// Jooble retorna 'updated' como string relativa/data de atualização; não assumir como data de publicação original se não for ISO confiável
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
						validos.add(extraido.get());
					} else {
						ignorados++;
					}
				}
			}
		} catch (Exception ex) {
			erros++;
			erroResumido = SanitizadorConteudo.sanitizarMensagemErro(
					"Erro ao consultar Jooble API: " + ex.getMessage(),
					apiKey
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

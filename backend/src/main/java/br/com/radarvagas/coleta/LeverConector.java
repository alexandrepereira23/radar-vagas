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
import java.util.Optional;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class LeverConector implements ConectorFonteVagas {

	public static final String FORNECEDOR = "LEVER_BOARD";

	private final RadarProperties properties;
	private final GitHubIssueClassificador classificador;
	private final ObjectMapper objectMapper;
	private final HttpClient httpClient;

	public LeverConector(
			RadarProperties properties,
			GitHubIssueClassificador classificador,
			ObjectMapper objectMapper
	) {
		this.properties = properties;
		this.classificador = classificador;
		this.objectMapper = objectMapper;
		int timeoutSeg = Math.max(2, properties.getLever().getTimeoutSegundos());
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
		String site = configuracao.getIdentificadorMural();
		String base = properties.getLever().getApiBaseUrl();
		if (base.endsWith("/")) {
			base = base.substring(0, base.length() - 1);
		}
		String url = base + "/v0/postings/" + site + "?mode=json";
		int maxItens = Math.max(1, properties.getLever().getMaxItensPorMural());

		List<AnuncioExtraido> validos = new ArrayList<>();
		int recebidos = 0;
		int ignorados = 0;
		int erros = 0;
		String erroResumido = null;
		Instant maiorPublicacao = configuracao.getCursorUltimaAtualizacao();

		try {
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(url))
					.timeout(Duration.ofSeconds(Math.max(2, properties.getLever().getTimeoutSegundos())))
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
						maiorPublicacao,
						"Resposta HTTP " + response.statusCode() + " ao consultar mural Lever: " + site
				);
			}

			JsonNode raiz = objectMapper.readTree(response.body());
			if (raiz == null || !raiz.isArray()) {
				return new ResultadoColetaFonte(
						StatusExecucaoColeta.FALHA,
						List.of(),
						0,
						0,
						1,
						null,
						null,
						maiorPublicacao,
						"Resposta inesperada do Lever (não é uma lista JSON)."
				);
			}

			for (JsonNode item : raiz) {
				if (validos.size() >= maxItens) {
					break;
				}
				recebidos++;
				try {
					String idExterno = textoOuNulo(item, "id");
					String titulo = textoOuNulo(item, "text");
					String descricao = textoOuNulo(item, "descriptionPlain");
					if (descricao == null) {
						descricao = textoOuNulo(item, "description");
					}
					String hostedUrl = textoOuNulo(item, "hostedUrl");
					String applyUrl = textoOuNulo(item, "applyUrl");
					if (applyUrl == null) {
						applyUrl = hostedUrl;
					}

					String localizacao = null;
					List<String> labels = new ArrayList<>();
					JsonNode categories = item.get("categories");
					if (categories != null) {
						localizacao = textoOuNulo(categories, "location");
						String team = textoOuNulo(categories, "team");
						if (team != null) {
							labels.add(team);
						}
						String department = textoOuNulo(categories, "department");
						if (department != null) {
							labels.add(department);
						}
					}

					Instant publicadoEm = null;
					if (item.hasNonNull("createdAt") && item.get("createdAt").isNumber()) {
						publicadoEm = Instant.ofEpochMilli(item.get("createdAt").asLong());
					}
					if (publicadoEm != null && (maiorPublicacao == null || publicadoEm.isAfter(maiorPublicacao))) {
						maiorPublicacao = publicadoEm;
					}

					Optional<AnuncioExtraido> extraido = classificador.classificarAnuncioExterno(
							FORNECEDOR,
							site,
							idExterno,
							titulo,
							site,
							descricao,
							localizacao,
							ModalidadeTrabalho.NAO_INFORMADA,
							RegimeContratacao.NAO_INFORMADO,
							null,
							labels,
							hostedUrl,
							applyUrl,
							publicadoEm,
							publicadoEm,
							true,
							true,
							idExterno
					);

					if (extraido.isPresent()) {
						validos.add(extraido.get());
					} else {
						ignorados++;
					}
				} catch (Exception itemEx) {
					erros++;
					erroResumido = SanitizadorConteudo.sanitizarMensagemErro(itemEx.getMessage(), null);
				}
			}
		} catch (Exception ex) {
			erros++;
			erroResumido = SanitizadorConteudo.sanitizarMensagemErro(
					"Erro ao consultar Lever (" + site + "): " + ex.getMessage(),
					null
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
				maiorPublicacao,
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

package br.com.radarvagas.coleta;

import br.com.radarvagas.catalogo.ModalidadeTrabalho;
import br.com.radarvagas.catalogo.RegimeContratacao;
import br.com.radarvagas.catalogo.SanitizadorConteudo;
import br.com.radarvagas.catalogo.TipoOportunidade;
import br.com.radarvagas.config.RadarProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class GreenhouseConector implements ConectorFonteVagas {

	public static final String FORNECEDOR = "GREENHOUSE_BOARD";

	private static final Map<String, String> NOMES_EMPRESAS_CONHECIDAS = Map.of(
			"quintoandar", "QuintoAndar",
			"ebanx", "EBANX",
			"vtex", "VTEX",
			"wildlifestudios", "Wildlife Studios",
			"brex", "Brex",
			"gitlab", "GitLab"
	);

	private final RadarProperties properties;
	private final GitHubIssueClassificador classificador;
	private final ObjectMapper objectMapper;
	private final HttpClient httpClient;

	public GreenhouseConector(
			RadarProperties properties,
			GitHubIssueClassificador classificador,
			ObjectMapper objectMapper
	) {
		this.properties = properties;
		this.classificador = classificador;
		this.objectMapper = objectMapper;
		int timeoutSeg = Math.max(2, properties.getGreenhouse().getTimeoutSegundos());
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
		String boardToken = configuracao.getIdentificadorMural();
		String base = properties.getGreenhouse().getApiBaseUrl();
		if (base.endsWith("/")) {
			base = base.substring(0, base.length() - 1);
		}
		String url = base + "/v1/boards/" + boardToken + "/jobs?content=true";
		int maxItens = Math.max(1, properties.getGreenhouse().getMaxItensPorMural());
		String nomeEmpresa = resolverNomeEmpresa(boardToken, configuracao.getNomeExibicao());

		List<AnuncioExtraido> validos = new ArrayList<>();
		int recebidos = 0;
		int ignorados = 0;
		int erros = 0;
		String erroResumido = null;
		Instant maiorAtualizacao = configuracao.getCursorUltimaAtualizacao();

		try {
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(url))
					.timeout(Duration.ofSeconds(Math.max(2, properties.getGreenhouse().getTimeoutSegundos())))
					.header("Accept", "application/json")
					.header("User-Agent", properties.getGithub().getUserAgent())
					.GET()
					.build();

			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() == 429) {
				return new ResultadoColetaFonte(
						StatusExecucaoColeta.LIMITE_ATINGIDO,
						List.of(),
						0,
						0,
						1,
						0,
						null,
						maiorAtualizacao,
						"Limite de requisições atingido no mural Greenhouse (" + boardToken + ")."
				);
			}
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				return new ResultadoColetaFonte(
						StatusExecucaoColeta.FALHA,
						List.of(),
						0,
						0,
						1,
						null,
						null,
						maiorAtualizacao,
						"Resposta HTTP " + response.statusCode() + " ao consultar mural Greenhouse: " + boardToken
				);
			}

			JsonNode raiz = objectMapper.readTree(response.body());
			JsonNode jobs = raiz != null ? raiz.get("jobs") : null;
			if (jobs == null || !jobs.isArray()) {
				return new ResultadoColetaFonte(
						StatusExecucaoColeta.FALHA,
						List.of(),
						0,
						0,
						1,
						null,
						null,
						maiorAtualizacao,
						"Payload inválido do Greenhouse (campo 'jobs' ausente)."
				);
			}

			for (JsonNode job : jobs) {
				if (validos.size() >= maxItens) {
					break;
				}
				recebidos++;
				try {
					String idExterno = textoOuNulo(job, "id");
					String titulo = textoOuNulo(job, "title");
					String conteudo = textoOuNulo(job, "content");
					String absoluteUrl = textoOuNulo(job, "absolute_url");
					String reqId = textoOuNulo(job, "requisition_id");

					String localizacao = null;
					if (job.hasNonNull("location") && job.get("location").hasNonNull("name")) {
						localizacao = job.get("location").get("name").asText();
					}

					List<String> departamentos = new ArrayList<>();
					JsonNode deptsNode = job.get("departments");
					if (deptsNode != null && deptsNode.isArray()) {
						for (JsonNode d : deptsNode) {
							String nomeDept = textoOuNulo(d, "name");
							if (nomeDept != null) {
								departamentos.add(nomeDept);
							}
						}
					}

					// Usa first_published como data de publicação; nunca usa updated_at como publicação!
					Instant publicadoEm = parseDataIsoComOffset(textoOuNulo(job, "first_published"));
					Instant atualizadoEm = parseDataIsoComOffset(textoOuNulo(job, "updated_at"));
					if (atualizadoEm != null && (maiorAtualizacao == null || atualizadoEm.isAfter(maiorAtualizacao))) {
						maiorAtualizacao = atualizadoEm;
					}

					Optional<AnuncioExtraido> extraido = classificador.classificarAnuncioExterno(
							FORNECEDOR,
							boardToken,
							idExterno,
							titulo,
							nomeEmpresa,
							conteudo,
							localizacao,
							ModalidadeTrabalho.NAO_INFORMADA,
							RegimeContratacao.NAO_INFORMADO,
							null,
							departamentos,
							absoluteUrl,
							absoluteUrl,
							publicadoEm,
							atualizadoEm,
							true,
							true,
							reqId
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
					"Erro ao consultar Greenhouse (" + boardToken + "): " + ex.getMessage(),
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
				maiorAtualizacao,
				erroResumido
		);
	}

	private String resolverNomeEmpresa(String boardToken, String nomeExibicao) {
		if (boardToken != null && NOMES_EMPRESAS_CONHECIDAS.containsKey(boardToken.toLowerCase())) {
			return NOMES_EMPRESAS_CONHECIDAS.get(boardToken.toLowerCase());
		}
		if (nomeExibicao != null && nomeExibicao.contains("•")) {
			return nomeExibicao.substring(nomeExibicao.indexOf('•') + 1).trim();
		}
		return boardToken;
	}

	private String textoOuNulo(JsonNode node, String campo) {
		if (node == null || !node.hasNonNull(campo)) {
			return null;
		}
		String texto = node.get(campo).asText();
		return (texto == null || texto.isBlank() || "null".equalsIgnoreCase(texto)) ? null : texto;
	}

	private Instant parseDataIsoComOffset(String valor) {
		if (valor == null || valor.isBlank()) {
			return null;
		}
		try {
			return Instant.parse(valor.trim());
		} catch (Exception e) {
			try {
				return OffsetDateTime.parse(valor.trim()).toInstant();
			} catch (Exception ex) {
				return null;
			}
		}
	}
}

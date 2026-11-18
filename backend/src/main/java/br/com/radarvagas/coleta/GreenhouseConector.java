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
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
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

		List<AnuncioExtraido> todosValidos = new ArrayList<>();
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
			JsonNode jobs = (raiz != null && raiz.isObject()) ? raiz.get("jobs") : null;
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

			List<JsonNode> jobsOrdenados = new ArrayList<>();
			jobs.forEach(jobsOrdenados::add);
			jobsOrdenados.sort(Comparator
					.comparing(
							(JsonNode j) -> parseDataIsoComOffset(textoOuNulo(j, "first_published")),
							Comparator.nullsLast(Comparator.reverseOrder())
					)
					.thenComparing(
							(JsonNode j) -> parseDataIsoComOffset(textoOuNulo(j, "updated_at")),
							Comparator.nullsLast(Comparator.reverseOrder())
					));

			for (JsonNode job : jobsOrdenados) {
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
						todosValidos.add(extraido.get());
					} else {
						ignorados++;
					}
				} catch (Exception itemEx) {
					erros++;
					erroResumido = SanitizadorConteudo.sanitizarMensagemErro(itemEx.getMessage());
				}
			}
		} catch (Exception ex) {
			erros++;
			erroResumido = SanitizadorConteudo.sanitizarMensagemErro(
					"Erro ao consultar Greenhouse (" + boardToken + "): " + ex.getMessage()
			);
		}

		RecorteMural recorte = aplicarRecorteComRotacao(todosValidos, maxItens, configuracao.getPaginaContinuacao());
		if (recorte.observacaoRecorte() != null && erroResumido == null) {
			erroResumido = recorte.observacaoRecorte();
		}

		StatusExecucaoColeta status;
		if (erros > 0) {
			status = recorte.itens().isEmpty() ? StatusExecucaoColeta.FALHA : StatusExecucaoColeta.SUCESSO_PARCIAL;
		} else if (recorte.coletaIncompleta()) {
			status = StatusExecucaoColeta.SUCESSO_PARCIAL;
		} else {
			status = StatusExecucaoColeta.SUCESSO;
		}

		return new ResultadoColetaFonte(
				status,
				recorte.itens(),
				recebidos,
				ignorados,
				erros,
				null,
				null,
				maiorAtualizacao,
				erroResumido,
				recorte.coletaIncompleta(),
				recorte.proximaPagina(),
				null,
				false
		);
	}

	private record RecorteMural(
			List<AnuncioExtraido> itens,
			boolean coletaIncompleta,
			Integer proximaPagina,
			String observacaoRecorte
	) {
	}

	private RecorteMural aplicarRecorteComRotacao(
			List<AnuncioExtraido> todosValidos,
			int maxItens,
			Integer paginaContinuacaoAtual
	) {
		if (todosValidos.size() <= maxItens) {
			return new RecorteMural(todosValidos, false, null, null);
		}
		int bloco = (paginaContinuacaoAtual != null && paginaContinuacaoAtual > 1) ? paginaContinuacaoAtual : 1;
		int inicio = (bloco - 1) * maxItens;
		if (inicio >= todosValidos.size()) {
			bloco = 1;
			inicio = 0;
		}
		int fim = Math.min(inicio + maxItens, todosValidos.size());
		List<AnuncioExtraido> fatia = new ArrayList<>(todosValidos.subList(inicio, fim));
		if (fim < todosValidos.size()) {
			int proximoBloco = bloco + 1;
			String obs = "Recorte por limite de volume: importadas " + fatia.size() + " de "
					+ todosValidos.size() + " vagas válidas (priorizadas por recência, bloco " + bloco
					+ "); continuação agendada para o bloco " + proximoBloco + ".";
			return new RecorteMural(fatia, true, proximoBloco, obs);
		}
		return new RecorteMural(fatia, false, null, null);
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

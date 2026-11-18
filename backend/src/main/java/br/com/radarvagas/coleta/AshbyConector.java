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
import java.util.Optional;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class AshbyConector implements ConectorFonteVagas {

	public static final String FORNECEDOR = "ASHBY_BOARD";

	private final RadarProperties properties;
	private final GitHubIssueClassificador classificador;
	private final ObjectMapper objectMapper;
	private final HttpClient httpClient;

	public AshbyConector(
			RadarProperties properties,
			GitHubIssueClassificador classificador,
			ObjectMapper objectMapper
	) {
		this.properties = properties;
		this.classificador = classificador;
		this.objectMapper = objectMapper;
		int timeoutSeg = Math.max(2, properties.getAshby().getTimeoutSegundos());
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
		String boardName = configuracao.getIdentificadorMural();
		String base = properties.getAshby().getApiBaseUrl();
		if (base.endsWith("/")) {
			base = base.substring(0, base.length() - 1);
		}
		String url = base + "/posting-api/job-board/" + boardName;
		int maxItens = Math.max(1, properties.getAshby().getMaxItensPorMural());
		String nomeEmpresa = resolverNomeEmpresa(boardName, configuracao.getNomeExibicao());

		List<AnuncioExtraido> todosValidos = new ArrayList<>();
		int recebidos = 0;
		int ignorados = 0;
		int erros = 0;
		String erroResumido = null;
		Instant maiorPublicacao = configuracao.getCursorUltimaAtualizacao();

		try {
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(url))
					.timeout(Duration.ofSeconds(Math.max(2, properties.getAshby().getTimeoutSegundos())))
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
						"Resposta HTTP " + response.statusCode() + " ao consultar mural Ashby: " + boardName
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
						maiorPublicacao,
						"Payload inválido da Ashby (campo 'jobs' ausente)."
				);
			}

			List<JsonNode> jobsOrdenados = new ArrayList<>();
			jobs.forEach(jobsOrdenados::add);
			jobsOrdenados.sort(Comparator.comparing(
					(JsonNode j) -> parseDataIso(textoOuNulo(j, "publishedAt")),
					Comparator.nullsLast(Comparator.reverseOrder())
			));

			for (JsonNode job : jobsOrdenados) {
				recebidos++;
				try {
					if (job.hasNonNull("isListed") && !job.get("isListed").asBoolean(true)) {
						ignorados++;
						continue;
					}
					String idExterno = textoOuNulo(job, "id");
					String titulo = textoOuNulo(job, "title");
					String descricao = textoOuNulo(job, "descriptionPlain");
					if (descricao == null) {
						descricao = textoOuNulo(job, "descriptionHtml");
					}
					String localizacao = textoOuNulo(job, "location");
					String workplaceType = textoOuNulo(job, "workplaceType");
					String jobUrl = textoOuNulo(job, "jobUrl");
					String applyUrl = textoOuNulo(job, "applyUrl");
					if (applyUrl == null) {
						applyUrl = jobUrl;
					}

					List<String> labels = new ArrayList<>();
					String dept = textoOuNulo(job, "department");
					if (dept != null) {
						labels.add(dept);
					}
					String team = textoOuNulo(job, "team");
					if (team != null) {
						labels.add(team);
					}

					Instant publicadoEm = parseDataIso(textoOuNulo(job, "publishedAt"));
					if (publicadoEm != null && (maiorPublicacao == null || publicadoEm.isAfter(maiorPublicacao))) {
						maiorPublicacao = publicadoEm;
					}

					ModalidadeTrabalho modalidade = mapearModalidade(workplaceType, job.hasNonNull("isRemote") && job.get("isRemote").asBoolean());

					Optional<AnuncioExtraido> extraido = classificador.classificarAnuncioExterno(
							FORNECEDOR,
							boardName,
							idExterno,
							titulo,
							nomeEmpresa,
							descricao,
							localizacao,
							modalidade,
							RegimeContratacao.NAO_INFORMADO,
							null,
							labels,
							jobUrl,
							applyUrl,
							publicadoEm,
							publicadoEm,
							true,
							true,
							idExterno
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
					"Erro ao consultar Ashby (" + boardName + "): " + ex.getMessage()
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
				maiorPublicacao,
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

	private ModalidadeTrabalho mapearModalidade(String workplaceType, boolean isRemote) {
		if (workplaceType != null) {
			String lower = workplaceType.toLowerCase();
			if (lower.contains("remote")) {
				return ModalidadeTrabalho.REMOTO;
			}
			if (lower.contains("hybrid")) {
				return ModalidadeTrabalho.HIBRIDO;
			}
			if (lower.contains("onsite") || lower.contains("on-site")) {
				return ModalidadeTrabalho.PRESENCIAL;
			}
		}
		return isRemote ? ModalidadeTrabalho.REMOTO : ModalidadeTrabalho.NAO_INFORMADA;
	}

	private String resolverNomeEmpresa(String boardName, String nomeExibicao) {
		if (nomeExibicao != null && nomeExibicao.contains("•")) {
			return nomeExibicao.substring(nomeExibicao.indexOf('•') + 1).trim();
		}
		return boardName;
	}

	private String textoOuNulo(JsonNode node, String campo) {
		if (node == null || !node.hasNonNull(campo)) {
			return null;
		}
		String texto = node.get(campo).asText();
		return (texto == null || texto.isBlank() || "null".equalsIgnoreCase(texto)) ? null : texto;
	}

	private Instant parseDataIso(String valor) {
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

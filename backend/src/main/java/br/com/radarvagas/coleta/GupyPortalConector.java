package br.com.radarvagas.coleta;

import br.com.radarvagas.catalogo.ModalidadeTrabalho;
import br.com.radarvagas.catalogo.RegimeContratacao;
import br.com.radarvagas.catalogo.SanitizadorConteudo;
import br.com.radarvagas.catalogo.TipoOportunidade;
import br.com.radarvagas.config.RadarProperties;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Conector para o portal público de busca da Gupy (portal.gupy.io).
 * Extrai dados estruturados do payload JSON SSR (__NEXT_DATA__) via HTTP simples,
 * sem utilizar navegador automatizado e sem contornar controles de acesso.
 */
@Component
public class GupyPortalConector implements ConectorFonteVagas {

	public static final String FORNECEDOR = "GUPY_PORTAL";

	private static final Pattern PADRAO_NEXT_DATA = Pattern.compile(
			"(?is)<script\\s+id=\"__NEXT_DATA__\"[^>]*>(.*?)</script>"
	);

	private final RadarProperties properties;
	private final GitHubIssueClassificador classificador;
	private final ObjectMapper objectMapper;
	private final HttpClient httpClient;

	public GupyPortalConector(
			RadarProperties properties,
			GitHubIssueClassificador classificador,
			ObjectMapper objectMapper
	) {
		this.properties = properties;
		this.classificador = classificador;
		this.objectMapper = objectMapper;
		int timeoutSeg = Math.max(2, properties.getGupy().getTimeoutSegundos());
		this.httpClient = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(timeoutSeg))
				.followRedirects(HttpClient.Redirect.NORMAL)
				.build();
	}

	@Override
	public String fornecedor() {
		return FORNECEDOR;
	}

	@Override
	public ResultadoColetaFonte coletar(ConfiguracaoFonte configuracao) {
		String termoBusca = configuracao.getIdentificadorMural();
		String base = properties.getGupy().getPortalBaseUrl();
		if (base.endsWith("/")) {
			base = base.substring(0, base.length() - 1);
		}
		String termoCodificado = URLEncoder.encode(termoBusca, StandardCharsets.UTF_8);
		String url = base + "/job-search/term=" + termoCodificado;
		int maxItens = Math.max(1, properties.getGupy().getMaxItensPorBusca());

		List<AnuncioExtraido> validos = new ArrayList<>();
		int recebidos = 0;
		int ignorados = 0;
		int erros = 0;
		String erroResumido = null;
		Instant maiorPublicacao = configuracao.getCursorUltimaAtualizacao();

		try {
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(url))
					.timeout(Duration.ofSeconds(Math.max(2, properties.getGupy().getTimeoutSegundos())))
					.header("Accept", "text/html,application/xhtml+xml")
					.header("User-Agent", properties.getGithub().getUserAgent())
					.GET()
					.build();

			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() == 429 || response.statusCode() == 403) {
				return new ResultadoColetaFonte(
						StatusExecucaoColeta.LIMITE_ATINGIDO,
						List.of(),
						0,
						0,
						1,
						null,
						null,
						maiorPublicacao,
						"Acesso bloqueado ou limite atingido no portal Gupy (HTTP " + response.statusCode() + ")."
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
						maiorPublicacao,
						"Resposta HTTP " + response.statusCode() + " ao consultar portal Gupy."
				);
			}

			Matcher matcher = PADRAO_NEXT_DATA.matcher(response.body());
			if (!matcher.find()) {
				return new ResultadoColetaFonte(
						StatusExecucaoColeta.FALHA,
						List.of(),
						0,
						0,
						1,
						null,
						null,
						maiorPublicacao,
						"Estrutura da página da Gupy mudou: bloco estruturado __NEXT_DATA__ não encontrado."
				);
			}

			String jsonNextData = matcher.group(1);
			JsonNode raiz = objectMapper.readTree(jsonNextData);
			JsonNode dataNode = raiz.at("/props/pageProps/initialJobList/data");
			if (dataNode == null || !dataNode.isArray()) {
				return new ResultadoColetaFonte(
						StatusExecucaoColeta.FALHA,
						List.of(),
						0,
						0,
						1,
						null,
						null,
						maiorPublicacao,
						"Lista estruturada 'initialJobList.data' não encontrada no payload da Gupy."
				);
			}

			for (JsonNode item : dataNode) {
				if (validos.size() >= maxItens) {
					break;
				}
				recebidos++;
				try {
					String idExterno = textoOuNulo(item, "id");
					String titulo = textoOuNulo(item, "name");
					String descricao = textoOuNulo(item, "description");
					String empresa = textoOuNulo(item, "careerPageName");
					String jobUrl = textoOuNulo(item, "jobUrl");
					String workplaceType = textoOuNulo(item, "workplaceType");
					String jobType = textoOuNulo(item, "type");
					String cidade = textoOuNulo(item, "city");
					String estado = textoOuNulo(item, "state");

					Instant publicadoEm = parseDataIso(textoOuNulo(item, "publishedDate"));
					if (publicadoEm != null && (maiorPublicacao == null || publicadoEm.isAfter(maiorPublicacao))) {
						maiorPublicacao = publicadoEm;
					}

					ModalidadeTrabalho modalidade = mapearModalidadeGupy(workplaceType);
					RegimeContratacao regime = mapearRegimeGupy(jobType);
					TipoOportunidade tipoOp = "vacancy_type_talent_pool".equalsIgnoreCase(jobType)
							? TipoOportunidade.BANCO_DE_TALENTOS
							: null;

					String localizacao = montarLocalizacao(cidade, estado, modalidade);

					Optional<AnuncioExtraido> extraido = classificador.classificarAnuncioExterno(
							FORNECEDOR,
							termoBusca,
							idExterno,
							titulo,
							empresa,
							descricao,
							localizacao,
							modalidade,
							regime,
							tipoOp,
							List.of(),
							jobUrl,
							jobUrl,
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
				} catch (Exception itemEx) {
					erros++;
					erroResumido = SanitizadorConteudo.sanitizarMensagemErro(itemEx.getMessage(), null);
				}
			}
		} catch (Exception ex) {
			erros++;
			erroResumido = SanitizadorConteudo.sanitizarMensagemErro(
					"Erro ao consultar portal público Gupy: " + ex.getMessage(),
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

	private ModalidadeTrabalho mapearModalidadeGupy(String workplaceType) {
		if (workplaceType == null) {
			return ModalidadeTrabalho.NAO_INFORMADA;
		}
		return switch (workplaceType.trim().toLowerCase()) {
			case "remote" -> ModalidadeTrabalho.REMOTO;
			case "hybrid" -> ModalidadeTrabalho.HIBRIDO;
			case "on-site", "onsite" -> ModalidadeTrabalho.PRESENCIAL;
			default -> ModalidadeTrabalho.NAO_INFORMADA;
		};
	}

	private RegimeContratacao mapearRegimeGupy(String type) {
		if (type == null) {
			return RegimeContratacao.NAO_INFORMADO;
		}
		return switch (type.trim().toLowerCase()) {
			case "vacancy_type_effective" -> RegimeContratacao.CLT;
			case "vacancy_legal_entity" -> RegimeContratacao.PJ;
			case "vacancy_type_internship" -> RegimeContratacao.ESTAGIO;
			default -> RegimeContratacao.NAO_INFORMADO;
		};
	}

	private String montarLocalizacao(String cidade, String estado, ModalidadeTrabalho modalidade) {
		if (cidade != null && estado != null) {
			return cidade.equalsIgnoreCase(estado) ? cidade : cidade + " - " + estado;
		}
		if (cidade != null) {
			return cidade;
		}
		if (estado != null) {
			return estado;
		}
		if (modalidade == ModalidadeTrabalho.REMOTO) {
			return "Remoto (Brasil)";
		}
		return null;
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

package br.com.radarvagas.coleta;

import br.com.radarvagas.catalogo.ModalidadeTrabalho;
import br.com.radarvagas.catalogo.RegimeContratacao;
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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Recorte SSR público. Rotação de termos não é paginação validada do portal. */
@Component
public class GupyPortalConector implements ConectorFonteVagas {
	public static final String FORNECEDOR = "GUPY_PORTAL";
	private static final Pattern NEXT_DATA = Pattern.compile("(?is)<script\\s+id=\"__NEXT_DATA__\"[^>]*>(.*?)</script>");
	private static final String LIMITACAO = "Recorte Gupy: somente lista SSR inicial por termo; paginação pública permitida não validada. "
			+ "Ordenação local não comprova as vagas mais recentes de toda a busca.";
	private final RadarProperties properties;
	private final GitHubIssueClassificador classificador;
	private final ObjectMapper mapper;
	private final HttpClient http;

	public GupyPortalConector(RadarProperties properties, GitHubIssueClassificador classificador, ObjectMapper mapper) {
		this.properties = properties;
		this.classificador = classificador;
		this.mapper = mapper;
		this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(Math.max(2,
				properties.getGupy().getTimeoutSegundos()))).followRedirects(HttpClient.Redirect.NEVER).build();
	}

	@Override public String fornecedor() { return FORNECEDOR; }

	@Override public ResultadoColetaFonte coletar(ConfiguracaoFonte cfg) {
		var p = properties.getGupy();
		String padrao = cfg.getIdentificadorMural();
		if (padrao == null || padrao.isBlank() || "tecnologia-brasil".equals(padrao)) padrao = "desenvolvedor";
		return BuscaPaginada.coletar(cfg, mapper, BuscaPaginada.termos(p.getTermosBusca(), padrao),
				p.getMaxItensPorBusca(), p.getMaxTermosPorExecucao(), p.getMaxTermosPorExecucao(), 1, 20,
				(termo, pagina, tamanho, inicio) -> {
					String url = p.getPortalBaseUrl().replaceAll("/+$", "") + "/job-search/term="
							+ URLEncoder.encode(termo, StandardCharsets.UTF_8);
					var request = HttpRequest.newBuilder(URI.create(url))
							.timeout(Duration.ofSeconds(Math.max(2, p.getTimeoutSegundos())))
							.header("Accept", "text/html,application/xhtml+xml")
							.header("User-Agent", properties.getGithub().getUserAgent()).GET().build();
					var response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
					if (response.statusCode() < 200 || response.statusCode() >= 300)
						throw new BuscaPaginada.Falha(response.statusCode() == 403 ? 423 : response.statusCode(),
								"Acesso bloqueado ou resposta HTTP " + response.statusCode() + " do portal Gupy.");
					var matcher = NEXT_DATA.matcher(response.body());
					if (!matcher.find()) throw new BuscaPaginada.Falha(0, "Bloco estruturado __NEXT_DATA__ não encontrado na Gupy.");
					JsonNode data = mapper.readTree(matcher.group(1)).at("/props/pageProps/initialJobList/data");
					if (!data.isArray()) throw new BuscaPaginada.Falha(0, "Lista initialJobList.data ausente na Gupy.");
					List<JsonNode> itens = new ArrayList<>();
					data.forEach(itens::add);
					itens.sort(Comparator.comparing((JsonNode n) -> parseData(BuscaPaginada.texto(n, "publishedDate")),
							Comparator.nullsLast(Comparator.reverseOrder())));
					return new BuscaPaginada.Pagina(itens, false, true);
				}, item -> classificar(cfg, item), LIMITACAO);
	}

	private Optional<AnuncioExtraido> classificar(ConfiguracaoFonte cfg, JsonNode item) {
		String id = BuscaPaginada.texto(item, "id"), url = BuscaPaginada.texto(item, "jobUrl");
		String workplace = BuscaPaginada.texto(item, "workplaceType"), tipo = BuscaPaginada.texto(item, "type");
		ModalidadeTrabalho modalidade = switch (workplace == null ? "" : workplace.toLowerCase()) {
			case "remote" -> ModalidadeTrabalho.REMOTO;
			case "hybrid" -> ModalidadeTrabalho.HIBRIDO;
			case "on-site", "onsite" -> ModalidadeTrabalho.PRESENCIAL;
			default -> ModalidadeTrabalho.NAO_INFORMADA;
		};
		RegimeContratacao regime = switch (tipo == null ? "" : tipo.toLowerCase()) {
			case "vacancy_type_effective" -> RegimeContratacao.CLT;
			case "vacancy_legal_entity" -> RegimeContratacao.PJ;
			case "vacancy_type_internship" -> RegimeContratacao.ESTAGIO;
			default -> RegimeContratacao.NAO_INFORMADO;
		};
		String cidade = BuscaPaginada.texto(item, "city"), estado = BuscaPaginada.texto(item, "state");
		String local = cidade != null && estado != null ? (cidade.equalsIgnoreCase(estado) ? cidade : cidade + " - " + estado)
				: cidade != null ? cidade : estado != null ? estado : modalidade == ModalidadeTrabalho.REMOTO ? "Remoto (Brasil)" : null;
		Instant data = parseData(BuscaPaginada.texto(item, "publishedDate"));
		return classificador.classificarAnuncioExterno(FORNECEDOR, cfg.getIdentificadorMural(), id,
				BuscaPaginada.texto(item, "name"), BuscaPaginada.texto(item, "careerPageName"),
				BuscaPaginada.texto(item, "description"), local, modalidade, regime,
				"vacancy_type_talent_pool".equalsIgnoreCase(tipo) ? TipoOportunidade.BANCO_DE_TALENTOS : null,
				List.of(), url, url, data, data, false, true, id);
	}

	private Instant parseData(String valor) {
		if (valor == null) return null;
		try { return Instant.parse(valor); }
		catch (Exception ex) {
			try { return OffsetDateTime.parse(valor).toInstant(); } catch (Exception ignored) { return null; }
		}
	}
}

package br.com.radarvagas.coleta;

import br.com.radarvagas.catalogo.ModalidadeTrabalho;
import br.com.radarvagas.catalogo.RegimeContratacao;
import br.com.radarvagas.config.RadarProperties;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
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
	private final ObjectMapper mapper;
	private final HttpClient http;

	public AdzunaConector(RadarProperties properties, GitHubIssueClassificador classificador, ObjectMapper mapper) {
		this.properties = properties;
		this.classificador = classificador;
		this.mapper = mapper;
		this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(Math.max(2,
				properties.getAdzuna().getTimeoutSegundos()))).followRedirects(HttpClient.Redirect.NEVER).build();
	}

	@Override public String fornecedor() { return FORNECEDOR; }

	@Override public ResultadoColetaFonte coletar(ConfiguracaoFonte cfg) {
		var p = properties.getAdzuna();
		if (p.getAppId() == null || p.getAppId().isBlank() || p.getAppKey() == null || p.getAppKey().isBlank()) {
			return new ResultadoColetaFonte(StatusExecucaoColeta.BLOQUEADA_CREDENCIAL, List.of(), 0, 0, 0,
					null, null, cfg.getCursorUltimaAtualizacao(), "Credenciais ausentes: ADZUNA_APP_ID e ADZUNA_APP_KEY.");
		}
		return BuscaPaginada.coletar(cfg, mapper, BuscaPaginada.termos(p.getTermosBusca(), "desenvolvedor software"),
				p.getMaxItensPorBusca(), p.getMaxTermosPorExecucao(), p.getMaxPaginasPorExecucao(),
				p.getMaxPaginasPorTermo(), Math.min(50, p.getItensPorPagina()),
				(termo, pagina, tamanho, inicio) -> {
					// A idade consultada inclui os dias de espera entre execuções da mesma busca.
					long dias = Math.max(1, p.getJanelaDias()) + Math.max(0, Duration.between(inicio, Instant.now()).toDays());
					String url = p.getApiBaseUrl().replaceAll("/+$", "") + "/v1/api/jobs/br/search/" + pagina
							+ "?app_id=" + encode(p.getAppId().trim()) + "&app_key=" + encode(p.getAppKey().trim())
							+ "&what=" + encode(termo) + "&results_per_page=" + tamanho + "&max_days_old=" + dias
							+ (p.isOrdenarPorData() ? "&sort_by=date" : "") + "&content-type=application/json";
					var request = HttpRequest.newBuilder(URI.create(url))
							.timeout(Duration.ofSeconds(Math.max(2, p.getTimeoutSegundos())))
							.header("Accept", "application/json").header("User-Agent", properties.getGithub().getUserAgent()).GET().build();
					var response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
					if (response.statusCode() < 200 || response.statusCode() >= 300)
						throw new BuscaPaginada.Falha(response.statusCode(), "Resposta HTTP " + response.statusCode() + " da Adzuna API.");
					return BuscaPaginada.pagina(mapper.readTree(response.body()), "results", "count", pagina, tamanho);
				}, item -> classificar(cfg, item),
				"Recorte Adzuna: teto de páginas por termo ou metadados de paginação insuficientes; completude não comprovada.");
	}

	private Optional<AnuncioExtraido> classificar(ConfiguracaoFonte cfg, JsonNode item) {
		Instant data = null;
		try { data = Instant.parse(BuscaPaginada.texto(item, "created")); } catch (Exception ignored) {}
		String id = BuscaPaginada.texto(item, "id"), url = BuscaPaginada.texto(item, "redirect_url");
		return classificador.classificarAnuncioExterno(FORNECEDOR, cfg.getIdentificadorMural(), id,
				BuscaPaginada.texto(item, "title"), BuscaPaginada.texto(item.get("company"), "display_name"),
				BuscaPaginada.texto(item, "description"), BuscaPaginada.texto(item.get("location"), "display_name"),
				ModalidadeTrabalho.NAO_INFORMADA, RegimeContratacao.NAO_INFORMADO, null, List.of(),
				url, url, data, data, false, true, id);
	}

	private String encode(String valor) { return URLEncoder.encode(valor, StandardCharsets.UTF_8); }
}

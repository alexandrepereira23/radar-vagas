package br.com.radarvagas.coleta;

import br.com.radarvagas.catalogo.ModalidadeTrabalho;
import br.com.radarvagas.catalogo.RegimeContratacao;
import br.com.radarvagas.config.RadarProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class JoobleConector implements ConectorFonteVagas {
	public static final String FORNECEDOR = "JOOBLE_API";
	private final RadarProperties properties;
	private final GitHubIssueClassificador classificador;
	private final ObjectMapper mapper;
	private final HttpClient http;

	public JoobleConector(RadarProperties properties, GitHubIssueClassificador classificador, ObjectMapper mapper) {
		this.properties = properties;
		this.classificador = classificador;
		this.mapper = mapper;
		this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(Math.max(2,
				properties.getJooble().getTimeoutSegundos()))).followRedirects(HttpClient.Redirect.NEVER).build();
	}

	@Override public String fornecedor() { return FORNECEDOR; }

	@Override public ResultadoColetaFonte coletar(ConfiguracaoFonte cfg) {
		var p = properties.getJooble();
		if (p.getApiKey() == null || p.getApiKey().isBlank()) {
			return new ResultadoColetaFonte(StatusExecucaoColeta.BLOQUEADA_CREDENCIAL, List.of(), 0, 0, 0,
					null, null, cfg.getCursorUltimaAtualizacao(), "Credencial ausente: JOOBLE_API_KEY.");
		}
		return BuscaPaginada.coletar(cfg, mapper, BuscaPaginada.termos(p.getTermosBusca(), "desenvolvedor software"),
				p.getMaxItensPorBusca(), p.getMaxTermosPorExecucao(), p.getMaxPaginasPorExecucao(),
				p.getMaxPaginasPorTermo(), Math.min(100, p.getItensPorPagina()),
				(termo, pagina, tamanho, inicio) -> {
					String payload = mapper.writeValueAsString(Map.of("keywords", termo,
							"location", p.getLocalizacao() == null || p.getLocalizacao().isBlank() ? "Brasil" : p.getLocalizacao(),
							"page", String.valueOf(pagina), "ResultOnPage", tamanho));
					var request = HttpRequest.newBuilder(URI.create(p.getApiBaseUrl().replaceAll("/+$", "") + "/api/" + p.getApiKey().trim()))
							.timeout(Duration.ofSeconds(Math.max(2, p.getTimeoutSegundos())))
							.header("Content-Type", "application/json").header("Accept", "application/json")
							.header("User-Agent", properties.getGithub().getUserAgent())
							.POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8)).build();
					var response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
					if (response.statusCode() < 200 || response.statusCode() >= 300)
						throw new BuscaPaginada.Falha(response.statusCode(), "Resposta HTTP " + response.statusCode() + " da Jooble API.");
					return BuscaPaginada.pagina(mapper.readTree(response.body()), "jobs", "totalCount", pagina, tamanho);
				}, item -> {
					String id = BuscaPaginada.texto(item, "id"), url = BuscaPaginada.texto(item, "link");
					return classificador.classificarAnuncioExterno(FORNECEDOR, cfg.getIdentificadorMural(), id,
							BuscaPaginada.texto(item, "title"), BuscaPaginada.texto(item, "company"),
							BuscaPaginada.texto(item, "snippet"), BuscaPaginada.texto(item, "location"),
							ModalidadeTrabalho.NAO_INFORMADA, RegimeContratacao.NAO_INFORMADO, null, List.of(),
							url, url, null, null, false, true, id);
				}, "Recorte Jooble: teto de páginas por termo ou metadados insuficientes; sem janela recente/ordenação validada.");
	}
}

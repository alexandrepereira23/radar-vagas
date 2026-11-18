package br.com.radarvagas;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.radarvagas.catalogo.AnuncioOrigemRepository;
import br.com.radarvagas.catalogo.OportunidadeRepository;
import br.com.radarvagas.coleta.*;
import br.com.radarvagas.config.RadarProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@ActiveProfiles("test")
class AgregadoresRetomadaTest extends ColetaTestFixture {
	@Autowired RadarProperties p;
	@Autowired OrquestradorColetaService orquestrador;
	@Autowired ConfiguracaoFonteRepository fontes;
	@Autowired AnuncioOrigemRepository anuncios;
	@Autowired OportunidadeRepository oportunidades;
	HttpServer server;
	String base;
	ObjectMapper mapper = new ObjectMapper();

	@BeforeEach void preparar() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		base = "http://127.0.0.1:" + server.getAddress().getPort();
		p.getAdzuna().setApiBaseUrl(base);
		p.getAdzuna().setAppId("id-ficticio");
		p.getAdzuna().setAppKey("chave-ficticia");
		p.getAdzuna().setTermosBusca(List.of("backend"));
		p.getJooble().setApiBaseUrl(base);
		p.getJooble().setApiKey("chave-ficticia");
		p.getJooble().setTermosBusca(List.of("backend"));
		p.getGupy().setPortalBaseUrl(base);
		p.getGupy().setTermosBusca(List.of("backend"));
	}

	@AfterEach void parar() { server.stop(0); }

	@Test void deveRecuperarQuartoAnuncioAdzunaAposReinicioMesmoSeRespostaMudar() {
		var mudou = new AtomicBoolean();
		var chamadas = new CopyOnWriteArrayList<String>();
		server.createContext("/v1/api/jobs/br/search/", ex -> {
			String query = ex.getRequestURI().getRawQuery();
			chamadas.add(query);
			boolean qa = query.contains("what=qa");
			responder(ex, 200, "{\"count\":2,\"results\":[" + adzuna(qa ? 3 : 1) + ","
					+ adzuna(mudou.get() ? 99 : qa ? 4 : 2) + "]}");
		});
		server.start();
		p.getAdzuna().setTermosBusca(List.of("backend", "qa"));
		p.getAdzuna().setMaxTermosPorExecucao(2);
		p.getAdzuna().setMaxItensPorBusca(3);
		var fonte = fonte(AdzunaConector.FORNECEDOR);
		var primeira = orquestrador.executarFonte(fonte);
		assertThat(primeira.statusCodigo()).isEqualTo("SUCESSO_PARCIAL");
		assertThat(primeira.anunciosValidos()).isEqualTo(3);
		assertThat(reler(fonte).getEstadoBusca()).contains("\"id\":\"4\"");
		assertThat(reler(fonte).getCursorUltimaAtualizacao()).isNull();
		mudou.set(true);
		orquestrador.inicializarFontesERecuperarInterrompidas();
		// Nova instância do conector, sem memória da primeira execução.
		var reiniciado = reiniciar(new AdzunaConector(p, new GitHubIssueClassificador(), mapper));
		assertThat(reiniciado.executarFonte(reler(fonte)).statusCodigo()).isEqualTo("SUCESSO");
		assertThat(anuncios.findAll()).extracting(a -> a.getIdentificadorExterno()).containsExactlyInAnyOrder("1", "2", "3", "4");
		assertThat(oportunidades.count()).isEqualTo(4);
		assertThat(chamadas).hasSize(2); // A sobra é recuperada do banco, não de resposta mutável.
		assertThat(reler(fonte).getEstadoBusca()).isNull();
	}

	@Test void deveInformarRecorteERetomarTerceiroAnuncioDoUnicoTermoJooble() {
		server.createContext("/api/", ex -> responder(ex, 200,
				"{\"totalCount\":3,\"jobs\":[" + jooble(1) + "," + jooble(2) + "," + jooble(3) + "]}"));
		server.start();
		p.getJooble().setMaxItensPorBusca(2);
		var fonte = fonte(JoobleConector.FORNECEDOR);
		var resultado = new JoobleConector(p, new GitHubIssueClassificador(), mapper).coletar(fonte);
		assertThat(resultado.anunciosValidos()).hasSize(2);
		assertThat(resultado.status()).isEqualTo(StatusExecucaoColeta.SUCESSO_PARCIAL);
		assertThat(resultado.coletaIncompleta()).isTrue();
		orquestrador.executarFonte(fonte);
		assertThat(reler(fonte).getPaginaContinuacao()).isEqualTo(1);
		assertThat(orquestrador.consultarStatusGlobal().fontes()).filteredOn(f -> f.id().equals(fonte.getId()))
				.allSatisfy(f -> assertThat(f.situacaoAtualizacao()).isEqualTo("ATUALIZACAO_PARCIAL"));
		orquestrador.executarFonte(reler(fonte));
		assertThat(anuncios.count()).isEqualTo(3);
	}

	@Test void deveRetomarSobraGupySemDeclararListaInicialComoCompleta() {
		server.createContext("/job-search/", ex -> responder(ex, 200,
				"<script id=\"__NEXT_DATA__\">{\"props\":{\"pageProps\":{\"initialJobList\":{\"data\":["
						+ gupy(1) + "," + gupy(2) + "," + gupy(3) + "]}}}}</script>"));
		server.start();
		p.getGupy().setMaxItensPorBusca(2);
		var fonte = fonte(GupyPortalConector.FORNECEDOR);
		assertThat(orquestrador.executarFonte(fonte).statusCodigo()).isEqualTo("SUCESSO_PARCIAL");
		var segunda = orquestrador.executarFonte(reler(fonte));
		assertThat(segunda.statusCodigo()).isEqualTo("SUCESSO_PARCIAL");
		assertThat(segunda.erroResumido()).contains("somente lista SSR inicial", "paginação pública permitida não validada");
		assertThat(anuncios.count()).isEqualTo(3);
		assertThat(reler(fonte).getEstadoBusca()).isNull();
	}

	@Test void devePaginarAdzunaComTamanhoEstavelEJanelaRecenteAposReinicio() {
		var urls = new CopyOnWriteArrayList<String>();
		server.createContext("/v1/api/jobs/br/search/", ex -> {
			urls.add(ex.getRequestURI().toString());
			boolean pagina2 = ex.getRequestURI().getPath().endsWith("/2");
			responder(ex, 200, "{\"count\":3,\"results\":[" + (pagina2 ? adzuna(3) : adzuna(1) + "," + adzuna(2)) + "]}");
		});
		server.start();
		p.getAdzuna().setItensPorPagina(2);
		p.getAdzuna().setMaxPaginasPorExecucao(1);
		var fonte = fonte(AdzunaConector.FORNECEDOR);
		assertThat(orquestrador.executarFonte(fonte).statusCodigo()).isEqualTo("SUCESSO_PARCIAL");
		assertThat(reler(fonte).getPaginaContinuacao()).isEqualTo(2);
		p.getAdzuna().setItensPorPagina(10); // Não muda o deslocamento durante uma janela em andamento.
		assertThat(orquestrador.executarFonte(reler(fonte)).statusCodigo()).isEqualTo("SUCESSO");
		assertThat(urls).hasSize(2).allSatisfy(url -> assertThat(url).contains("results_per_page=2", "max_days_old=7", "sort_by=date"));
		assertThat(urls.get(1)).contains("/search/2?");
		assertThat(anuncios.count()).isEqualTo(3);
	}

	@Test void devePaginarJoobleEnviandoResultOnPageEUsandoTotalCount() {
		var corpos = new CopyOnWriteArrayList<String>();
		server.createContext("/api/", ex -> {
			String corpo = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			corpos.add(corpo);
			boolean pagina2 = mapper.readTree(corpo).get("page").asText().equals("2");
			responder(ex, 200, "{\"totalCount\":3,\"jobs\":[" + (pagina2 ? jooble(3) : jooble(1) + "," + jooble(2)) + "]}");
		});
		server.start();
		p.getJooble().setItensPorPagina(2);
		p.getJooble().setMaxPaginasPorExecucao(1);
		var fonte = fonte(JoobleConector.FORNECEDOR);
		assertThat(orquestrador.executarFonte(fonte).statusCodigo()).isEqualTo("SUCESSO_PARCIAL");
		assertThat(orquestrador.executarFonte(reler(fonte)).statusCodigo()).isEqualTo("SUCESSO");
		assertThat(corpos).hasSize(2).allSatisfy(c -> assertThat(c).contains("\"ResultOnPage\":2"));
		assertThat(corpos.get(1)).contains("\"page\":\"2\"");
		assertThat(anuncios.count()).isEqualTo(3);
	}

	@Test void devePreservarPaginaQueFalhouAposReceberPaginaAnterior() {
		var falhar = new AtomicBoolean(true);
		var paginas = new CopyOnWriteArrayList<String>();
		server.createContext("/v1/api/jobs/br/search/", ex -> {
			String path = ex.getRequestURI().getPath();
			paginas.add(path);
			if (path.endsWith("/2") && falhar.get()) responder(ex, 429, "{}");
			else responder(ex, 200, "{\"count\":2,\"results\":[" + adzuna(path.endsWith("/2") ? 2 : 1) + "]}");
		});
		server.start();
		p.getAdzuna().setItensPorPagina(1);
		var fonte = fonte(AdzunaConector.FORNECEDOR);
		assertThat(orquestrador.executarFonte(fonte).statusCodigo()).isEqualTo("SUCESSO_PARCIAL");
		assertThat(reler(fonte).getPaginaContinuacao()).isEqualTo(2);
		falhar.set(false);
		assertThat(orquestrador.executarFonte(reler(fonte)).statusCodigo()).isEqualTo("SUCESSO");
		assertThat(paginas).containsExactly("/v1/api/jobs/br/search/1", "/v1/api/jobs/br/search/2", "/v1/api/jobs/br/search/2");
		assertThat(anuncios.count()).isEqualTo(2);
	}

	@Test void deveDeduplicarEntreTermosERespostasSemRecriarOportunidades() {
		server.createContext("/v1/api/jobs/br/search/", ex -> responder(ex, 200,
				"{\"count\":2,\"results\":[" + adzuna(1) + "," + adzuna(2) + "]}"));
		server.start();
		p.getAdzuna().setTermosBusca(List.of("backend", "qa"));
		var fonte = fonte(AdzunaConector.FORNECEDOR);
		assertThat(orquestrador.executarFonte(fonte).anunciosNovos()).isEqualTo(2);
		assertThat(orquestrador.executarFonte(reler(fonte)).anunciosNovos()).isZero();
		assertThat(anuncios.count()).isEqualTo(2);
		assertThat(oportunidades.count()).isEqualTo(2);
	}

	@Test void naoDeveAfirmarCompletudeSemContagemOuAoAtingirTetoDeProfundidade() {
		server.createContext("/v1/api/jobs/br/search/", ex -> responder(ex, 200,
				"{\"count\":100,\"results\":[" + adzuna(1) + "]}"));
		server.createContext("/api/", ex -> responder(ex, 200, "{\"jobs\":[" + jooble(1) + "]}"));
		server.start();
		p.getAdzuna().setItensPorPagina(1);
		p.getAdzuna().setMaxPaginasPorTermo(1);
		var resultado = orquestrador.executarFonte(fonte(AdzunaConector.FORNECEDOR));
		assertThat(resultado.statusCodigo()).isEqualTo("SUCESSO_PARCIAL");
		assertThat(resultado.erroResumido()).contains("teto de páginas");
		assertThat(orquestrador.executarFonte(fonte(JoobleConector.FORNECEDOR)).statusCodigo()).isEqualTo("SUCESSO_PARCIAL");
	}

	@Test void deveManterItensAdiadosDuranteAusenciaTemporariaDaCredencial() {
		server.createContext("/api/", ex -> responder(ex, 200,
				"{\"totalCount\":2,\"jobs\":[" + jooble(1) + "," + jooble(2) + "]}"));
		server.start();
		p.getJooble().setMaxItensPorBusca(1);
		var fonte = fonte(JoobleConector.FORNECEDOR);
		orquestrador.executarFonte(fonte);
		String estado = reler(fonte).getEstadoBusca();
		p.getJooble().setApiKey("");
		assertThat(orquestrador.executarFonte(reler(fonte)).statusCodigo()).isEqualTo("BLOQUEADA_CREDENCIAL");
		assertThat(reler(fonte).getEstadoBusca()).isEqualTo(estado);
		p.getJooble().setApiKey("chave-ficticia");
		assertThat(orquestrador.executarFonte(reler(fonte)).anunciosNovos()).isEqualTo(1);
		assertThat(anuncios.count()).isEqualTo(2);
	}

	@Test void devePreservarEstadoIlegivelSemReiniciarBuscaSilenciosamente() {
		var fonte = fonte(AdzunaConector.FORNECEDOR);
		fonte.setEstadoBusca("{estado-invalido}");
		fonte = fontes.save(fonte);
		assertThat(orquestrador.executarFonte(fonte).statusCodigo()).isEqualTo("FALHA");
		assertThat(reler(fonte).getEstadoBusca()).isEqualTo("{estado-invalido}");
	}

	private OrquestradorColetaService reiniciar(ConectorFonteVagas conector) {
		return new OrquestradorColetaService(fontes, execucoes, catalogo, p, java.time.Clock.systemUTC(), List.of(conector));
	}
	@Autowired ExecucaoColetaRepository execucoes;
	@Autowired br.com.radarvagas.catalogo.CatalogoVagasService catalogo;
	private ConfiguracaoFonte reler(ConfiguracaoFonte fonte) { return fontes.findById(fonte.getId()).orElseThrow(); }
	private ConfiguracaoFonte fonte(String fornecedor) {
		var f = new ConfiguracaoFonte();
		f.setFornecedor(fornecedor); f.setIdentificadorMural("review-brasil"); f.setNomeExibicao(fornecedor);
		f.setCriadoEm(Instant.now()); f.setAtualizadoEm(Instant.now());
		return fontes.save(f);
	}
	private static void responder(HttpExchange ex, int status, String corpo) throws IOException {
		byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
		ex.sendResponseHeaders(status, bytes.length);
		try (var out = ex.getResponseBody()) { out.write(bytes); }
	}
	private static String adzuna(int id) {
		return "{\"id\":\"" + id + "\",\"title\":\"Backend Developer Java\",\"description\":\"Desenvolvimento no Brasil\","
				+ "\"company\":{\"display_name\":\"Empresa Review\"},\"location\":{\"display_name\":\"Brasil\"},"
				+ "\"created\":\"2026-11-18T10:00:00Z\",\"redirect_url\":\"https://empresa.example/jobs/" + id + "\"}";
	}
	private static String jooble(int id) {
		return "{\"id\":\"" + id + "\",\"title\":\"Backend Developer Java\",\"company\":\"Empresa Review\","
				+ "\"location\":\"Brasil\",\"snippet\":\"Desenvolvimento no Brasil\",\"link\":\"https://empresa.example/jobs/" + id + "\"}";
	}
	private static String gupy(int id) {
		return "{\"id\":\"" + id + "\",\"name\":\"Backend Developer Java\",\"careerPageName\":\"Empresa Review\","
				+ "\"city\":\"São Paulo\",\"state\":\"SP\",\"description\":\"Java no Brasil\","
				+ "\"publishedDate\":\"2026-11-18T10:00:00Z\",\"jobUrl\":\"https://empresa.example/jobs/" + id + "\"}";
	}
}

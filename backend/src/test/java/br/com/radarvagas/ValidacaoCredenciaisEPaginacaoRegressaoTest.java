package br.com.radarvagas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.radarvagas.acoes.AcaoPessoalRepository;
import br.com.radarvagas.catalogo.AnuncioOrigemRepository;
import br.com.radarvagas.catalogo.CatalogoVagasService;
import br.com.radarvagas.catalogo.Oportunidade;
import br.com.radarvagas.catalogo.OportunidadeRepository;
import br.com.radarvagas.catalogo.PaginaVagasDto;
import br.com.radarvagas.coleta.AdzunaConector;
import br.com.radarvagas.coleta.ConfiguracaoFonte;
import br.com.radarvagas.coleta.ConfiguracaoFonteRepository;
import br.com.radarvagas.coleta.ExecucaoColetaDto;
import br.com.radarvagas.coleta.ExecucaoColetaRepository;
import br.com.radarvagas.coleta.FonteConfiguradaDto;
import br.com.radarvagas.coleta.GreenhouseConector;
import br.com.radarvagas.coleta.JoobleConector;
import br.com.radarvagas.coleta.OrquestradorColetaService;
import br.com.radarvagas.coleta.StatusColetaGlobalDto;
import br.com.radarvagas.coleta.StatusExecucaoColeta;
import br.com.radarvagas.config.RadarProperties;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.env.PropertiesPropertySourceLoader;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ValidacaoCredenciaisEPaginacaoRegressaoTest extends ColetaTestFixture {

	@Autowired
	private OrquestradorColetaService orquestradorColetaService;

	@Autowired
	private CatalogoVagasService catalogoVagasService;

	@Autowired
	private ConfiguracaoFonteRepository configuracaoFonteRepository;

	@Autowired
	private ExecucaoColetaRepository execucaoColetaRepository;

	@Autowired
	private OportunidadeRepository oportunidadeRepository;

	@Autowired
	private AnuncioOrigemRepository anuncioOrigemRepository;

	@Autowired
	private AcaoPessoalRepository acaoPessoalRepository;

	@Autowired
	private RadarProperties radarProperties;

	@Autowired
	private MockMvc mockMvc;

	private HttpServer servidorTeste;

	@BeforeEach
	void setUp() throws IOException {
		servidorTeste = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
	}

	@AfterEach
	void tearDown() {
		if (servidorTeste != null) {
			servidorTeste.stop(0);
		}
	}

	@Test
	void deveRetomarPaginacaoTruncadaDoGitHubSemPerderVagaDaPagina2ESemRepetirPagina1() {
		// Configura 20 itens por página e limite de 1 página por execução (cenário exato do achado P1)
		radarProperties.getGithub().setItensPorPagina(20);
		radarProperties.getGithub().setMaxPaginasPorExecucao(1);
		radarProperties.getGithub().setSobreposicaoIncrementalMinutos(60);

		List<String> queriesRecebidas = new CopyOnWriteArrayList<>();

		// Página 1: 20 vagas atualizadas em 18/11/2026
		StringBuilder jsonPag1 = new StringBuilder("[");
		for (int i = 1; i <= 20; i++) {
			if (i > 1) {
				jsonPag1.append(",");
			}
			int num = 2000 + i;
			jsonPag1.append("""
					{
					  "number": %d,
					  "title": "[Remoto] Pessoa Desenvolvedora Java #%d @ EmpresaPag1",
					  "body": "Vaga Java Spring Boot na página 1 (#%d).",
					  "state": "open",
					  "html_url": "https://github.com/backend-br/vagas/issues/%d",
					  "created_at": "2026-11-18T10:00:00Z",
					  "updated_at": "2026-11-18T12:00:00Z",
					  "labels": [{ "name": "Remoto" }]
					}
					""".formatted(num, i, i, num));
		}
		jsonPag1.append("]");

		// Página 2: 1 vaga relevante atualizada em 17/11/2026 (anterior a 18/11 menos 60 min!)
		String jsonPag2 = """
				[
				  {
				    "number": 1701,
				    "title": "[Remoto] Engenheiro de Dados Python @ EmpresaPag2",
				    "body": "Vaga relevante da página 2 atualizada em 17/11 que não pode ser descartada.",
				    "state": "open",
				    "html_url": "https://github.com/backend-br/vagas/issues/1701",
				    "created_at": "2026-11-16T08:30:00Z",
				    "updated_at": "2026-11-17T09:00:00Z",
				    "labels": [{ "name": "Remoto" }]
				  }
				]
				""";

		servidorTeste.createContext("/repos/backend-br/vagas/issues", exchange -> {
			String query = exchange.getRequestURI().getRawQuery();
			queriesRecebidas.add(query);
			exchange.getResponseHeaders().add("X-RateLimit-Remaining", "50");
			if (query != null && query.contains("&page=2")) {
				responder(exchange, 200, "application/json", jsonPag2);
			} else if (query != null && query.contains("&page=1")) {
				responder(exchange, 200, "application/json", jsonPag1.toString());
			} else {
				responder(exchange, 200, "application/json", "[]");
			}
		});
		servidorTeste.start();
		radarProperties.getGithub().setApiBaseUrl("http://127.0.0.1:" + servidorTeste.getAddress().getPort());

		ConfiguracaoFonte fonte = configuracaoFonteRepository
				.findByFornecedorAndIdentificadorMural("GITHUB_ISSUES", "backend-br/vagas")
				.orElseThrow();

		// 1ª execução: lê apenas a página 1 (20 itens), reconhece truncamento (SUCESSO_PARCIAL) e agenda continuação na página 2
		ExecucaoColetaDto exec1 = orquestradorColetaService.executarFonte(fonte);
		assertThat(exec1.statusCodigo()).isEqualTo(StatusExecucaoColeta.SUCESSO_PARCIAL.name());
		assertThat(exec1.anunciosRecebidos()).isEqualTo(20);
		assertThat(exec1.anunciosNovos()).isEqualTo(20);
		assertThat(exec1.erroResumido()).contains("página 2");

		ConfiguracaoFonte fonteAposExec1 = configuracaoFonteRepository.findById(fonte.getId()).orElseThrow();
		// O cursor definitivo NÃO avançou para 18/11 enquanto a página 2 não foi lida!
		assertThat(fonteAposExec1.getCursorUltimaAtualizacao()).isNull();
		assertThat(fonteAposExec1.getPaginaContinuacao()).isEqualTo(2);
		assertThat(fonteAposExec1.getMaiorAtualizacaoJanela()).isEqualTo(Instant.parse("2026-11-18T12:00:00Z"));

		// 2ª execução: retoma diretamente da página 2 sem repetir a página 1 e importa a vaga de 17/11
		ExecucaoColetaDto exec2 = orquestradorColetaService.executarFonte(fonteAposExec1);
		assertThat(exec2.statusCodigo()).isEqualTo(StatusExecucaoColeta.SUCESSO.name());
		assertThat(exec2.anunciosRecebidos()).isEqualTo(1);
		assertThat(exec2.anunciosNovos()).isEqualTo(1);

		assertThat(queriesRecebidas).hasSize(2);
		assertThat(queriesRecebidas.get(0)).contains("page=1").doesNotContain("since=");
		assertThat(queriesRecebidas.get(1)).contains("page=2").doesNotContain("since=");

		// Confirma que a vaga 1701 da página 2 foi salva preservando seu publicadoEm original (16/11)
		Oportunidade vagaPag2 = oportunidadeRepository.findAll().stream()
				.filter(o -> "1701".equals(o.getIdentificadorExternoPrincipal()))
				.findFirst()
				.orElseThrow();
		assertThat(vagaPag2.getPublicadoEm()).isEqualTo(Instant.parse("2026-11-16T08:30:00Z"));
		assertThat(vagaPag2.getAtualizadoNaFonteEm()).isEqualTo(Instant.parse("2026-11-17T09:00:00Z"));
		assertThat(oportunidadeRepository.count()).isEqualTo(21);

		// Após concluir a janela na página 2, o cursor consolidado avança para 18/11 e paginaContinuacao é limpa
		ConfiguracaoFonte fonteAposExec2 = configuracaoFonteRepository.findById(fonte.getId()).orElseThrow();
		assertThat(fonteAposExec2.getPaginaContinuacao()).isNull();
		assertThat(fonteAposExec2.getCursorUltimaAtualizacao()).isEqualTo(Instant.parse("2026-11-18T12:00:00Z"));
	}

	@Test
	void naoDeveMarcarFonteComoValidadaApenasPelaPresencaDaChaveEDevePreservarErroDeAutenticacaoNoReinicio() throws Exception {
		AtomicInteger statusJooble = new AtomicInteger(403);
		AtomicInteger statusAdzuna = new AtomicInteger(401);

		servidorTeste.createContext("/api/", exchange ->
				responder(exchange, statusJooble.get(), "application/json", "{\"error\":\"Unauthorized key\"}"));
		servidorTeste.createContext("/v1/api/jobs/br/search/1", exchange ->
				responder(exchange, statusAdzuna.get(), "application/json", "{\"error\":\"Invalid app_id or app_key\"}"));
		servidorTeste.start();

		String baseLocal = "http://127.0.0.1:" + servidorTeste.getAddress().getPort();
		radarProperties.getJooble().setApiBaseUrl(baseLocal);
		radarProperties.getAdzuna().setApiBaseUrl(baseLocal);

		String segredoJoobleTeste = "jooble_chave_ficticia_teste_999";
		String segredoAdzunaIdTeste = "adzuna_id_ficticio_888";
		String segredoAdzunaKeyTeste = "adzuna_key_ficticia_777";
		radarProperties.getJooble().setApiKey(segredoJoobleTeste);
		radarProperties.getAdzuna().setAppId(segredoAdzunaIdTeste);
		radarProperties.getAdzuna().setAppKey(segredoAdzunaKeyTeste);

		RadarProperties.FonteCatalogoConfig cfgJooble = new RadarProperties.FonteCatalogoConfig();
		cfgJooble.setFornecedor(JoobleConector.FORNECEDOR);
		cfgJooble.setIdentificador("tecnologia-brasil");
		cfgJooble.setNomeExibicao("Jooble Brasil");
		cfgJooble.setCategoria("AGREGADOR");

		RadarProperties.FonteCatalogoConfig cfgAdzuna = new RadarProperties.FonteCatalogoConfig();
		cfgAdzuna.setFornecedor(AdzunaConector.FORNECEDOR);
		cfgAdzuna.setIdentificador("tecnologia-brasil");
		cfgAdzuna.setNomeExibicao("Adzuna Brasil");
		cfgAdzuna.setCategoria("AGREGADOR");

		radarProperties.setFontesRegistradas(List.of(cfgJooble, cfgAdzuna));
		orquestradorColetaService.inicializarFontesERecuperarInterrompidas();

		// 1. Apenas ter chave no ambiente habilita tentativa (ativo=true), mas NÃO marca como ATIVA_VALIDADA!
		ConfiguracaoFonte joobleAntes = configuracaoFonteRepository
				.findByFornecedorAndIdentificadorMural(JoobleConector.FORNECEDOR, "tecnologia-brasil")
				.orElseThrow();
		assertThat(joobleAntes.isAtivo()).isTrue();
		assertThat(joobleAntes.getEstadoValidacao()).isEqualTo("CONFIGURADA_NAO_VALIDADA");
		assertThat(joobleAntes.getUltimaColetaSucessoEm()).isNull();

		ConfiguracaoFonte adzunaAntes = configuracaoFonteRepository
				.findByFornecedorAndIdentificadorMural(AdzunaConector.FORNECEDOR, "tecnologia-brasil")
				.orElseThrow();
		assertThat(adzunaAntes.isAtivo()).isTrue();
		assertThat(adzunaAntes.getEstadoValidacao()).isEqualTo("CONFIGURADA_NAO_VALIDADA");
		assertThat(adzunaAntes.getUltimaColetaSucessoEm()).isNull();

		// 2. Execução com chave inválida falha com FALHA_AUTENTICACAO e não define ultimaColetaSucessoEm
		ExecucaoColetaDto execJooble = orquestradorColetaService.executarFonte(joobleAntes);
		assertThat(execJooble.statusCodigo()).isEqualTo(StatusExecucaoColeta.FALHA.name());

		ExecucaoColetaDto execAdzuna = orquestradorColetaService.executarFonte(adzunaAntes);
		assertThat(execAdzuna.statusCodigo()).isEqualTo(StatusExecucaoColeta.FALHA.name());

		ConfiguracaoFonte joobleAposFalha = configuracaoFonteRepository.findById(joobleAntes.getId()).orElseThrow();
		assertThat(joobleAposFalha.getEstadoValidacao()).isEqualTo("FALHA_AUTENTICACAO");
		assertThat(joobleAposFalha.getUltimaColetaSucessoEm()).isNull();

		ConfiguracaoFonte adzunaAposFalha = configuracaoFonteRepository.findById(adzunaAntes.getId()).orElseThrow();
		assertThat(adzunaAposFalha.getEstadoValidacao()).isEqualTo("FALHA_AUTENTICACAO");
		assertThat(adzunaAposFalha.getUltimaColetaSucessoEm()).isNull();

		// 3. Reiniciar o serviço NÃO reseta o erro de autenticação para validado nem apaga a evidência!
		orquestradorColetaService.inicializarFontesERecuperarInterrompidas();
		ConfiguracaoFonte joobleAposReinicio = configuracaoFonteRepository.findById(joobleAntes.getId()).orElseThrow();
		assertThat(joobleAposReinicio.getEstadoValidacao()).isEqualTo("FALHA_AUTENTICACAO");

		// 4. Verifica que o endpoint /api/coletas/status nunca expõe os valores dos segredos
		String jsonStatus = mockMvc.perform(get("/api/coletas/status"))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString(StandardCharsets.UTF_8);

		assertThat(jsonStatus).doesNotContain(segredoJoobleTeste);
		assertThat(jsonStatus).doesNotContain(segredoAdzunaIdTeste);
		assertThat(jsonStatus).doesNotContain(segredoAdzunaKeyTeste);
	}

	@Test
	void deveRejeitarRespostaHttp200MalformadaSemArrayEsperadoNosAgregadoresEUsarConsultasReaisConfiguraveis() {
		AtomicReference<String> corpoJooble = new AtomicReference<>("{\"error\": \"Key quota or malformed 200\"}");
		AtomicReference<String> corpoAdzuna = new AtomicReference<>("{\"exception\": \"Malformed 200 without results\"}");
		List<String> payloadsRecebidosJooble = new CopyOnWriteArrayList<>();
		List<String> queriesRecebidasAdzuna = new CopyOnWriteArrayList<>();

		servidorTeste.createContext("/api/", exchange -> {
			String bodyReq = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			payloadsRecebidosJooble.add(bodyReq);
			responder(exchange, 200, "application/json", corpoJooble.get());
		});
		servidorTeste.createContext("/v1/api/jobs/br/search/1", exchange -> {
			queriesRecebidasAdzuna.add(exchange.getRequestURI().getRawQuery());
			responder(exchange, 200, "application/json", corpoAdzuna.get());
		});
		servidorTeste.start();

		String baseLocal = "http://127.0.0.1:" + servidorTeste.getAddress().getPort();
		radarProperties.getJooble().setApiBaseUrl(baseLocal);
		radarProperties.getJooble().setApiKey("chave_teste_jooble");
		radarProperties.getJooble().setTermosBusca(List.of("desenvolvedor software", "qa testes"));
		radarProperties.getJooble().setMaxTermosPorExecucao(1);

		radarProperties.getAdzuna().setApiBaseUrl(baseLocal);
		radarProperties.getAdzuna().setAppId("id_teste_adzuna");
		radarProperties.getAdzuna().setAppKey("key_teste_adzuna");
		radarProperties.getAdzuna().setTermosBusca(List.of("engenharia dados"));
		radarProperties.getAdzuna().setMaxTermosPorExecucao(1);
		radarProperties.getAdzuna().setOrdenarPorData(true);

		ConfiguracaoFonte fonteJooble = salvarFonte(JoobleConector.FORNECEDOR, "tecnologia-brasil", "Jooble Brasil", true, "CONFIGURADA_NAO_VALIDADA");
		ConfiguracaoFonte fonteAdzuna = salvarFonte(AdzunaConector.FORNECEDOR, "tecnologia-brasil", "Adzuna Brasil", true, "CONFIGURADA_NAO_VALIDADA");

		// 1. HTTP 200 sem o array esperado ('jobs' ou 'results') DEVE falhar e NÃO validar a fonte!
		ExecucaoColetaDto execJoobleMalformado = orquestradorColetaService.executarFonte(fonteJooble);
		assertThat(execJoobleMalformado.statusCodigo()).isEqualTo(StatusExecucaoColeta.FALHA.name());
		assertThat(execJoobleMalformado.errosContagem()).isEqualTo(1);
		assertThat(execJoobleMalformado.erroResumido()).contains("campo 'jobs' ausente");

		ExecucaoColetaDto execAdzunaMalformado = orquestradorColetaService.executarFonte(fonteAdzuna);
		assertThat(execAdzunaMalformado.statusCodigo()).isEqualTo(StatusExecucaoColeta.FALHA.name());
		assertThat(execAdzunaMalformado.errosContagem()).isEqualTo(1);
		assertThat(execAdzunaMalformado.erroResumido()).contains("campo 'results' ausente");

		assertThat(configuracaoFonteRepository.findById(fonteJooble.getId()).orElseThrow().getEstadoValidacao())
				.isEqualTo("FALHA_EXECUCAO");
		assertThat(configuracaoFonteRepository.findById(fonteAdzuna.getId()).orElseThrow().getEstadoValidacao())
				.isEqualTo("FALHA_EXECUCAO");

		// Confirma que o identificador administrativo 'tecnologia-brasil' / 'tecnologia brasil' NÃO foi usado como keyword!
		assertThat(payloadsRecebidosJooble.get(0))
				.contains("desenvolvedor software")
				.doesNotContain("tecnologia brasil")
				.doesNotContain("tecnologia-brasil");
		assertThat(queriesRecebidasAdzuna.get(0))
				.contains("what=engenharia+dados")
				.contains("sort_by=date")
				.doesNotContain("tecnologia");

		// 2. Quando o fornecedor responde HTTP 200 com o array válido, a coleta funciona e valida a fonte
		corpoJooble.set("""
				{
				  "totalCount": 1, "jobs": [
				    {
				      "id": "jb-101",
				      "title": "Pessoa Desenvolvedora Backend Java Pleno",
				      "company": "Alfa Sistemas",
				      "location": "São Paulo, SP",
				      "snippet": "Desenvolvimento Java e PostgreSQL no Brasil.",
				      "link": "https://br.jooble.org/desc/jb-101?redirect=a%2Fb&utm_source=jooble"
				    }
				  ]
				}
				""");
		corpoAdzuna.set("""
				{
				  "count": 1, "results": [
				    {
				      "id": "adz-202",
				      "title": "Engenheiro de Dados Python / SQL",
				      "description": "Pipelines de dados com Python e PostgreSQL em Curitiba.",
				      "created": "2026-11-17T14:00:00Z",
				      "company": { "display_name": "Beta Analytics" },
				      "location": { "display_name": "Curitiba, Paraná" },
				      "redirect_url": "https://www.adzuna.com.br/details/adz-202?utm_medium=api"
				    }
				  ]
				}
				""");

		ExecucaoColetaDto execJoobleValido = orquestradorColetaService.executarFonte(
				configuracaoFonteRepository.findById(fonteJooble.getId()).orElseThrow()
		);
		// Um termo por execução: termo 2 / página 1 fica no estado de busca persistido.
		assertThat(execJoobleValido.statusCodigo()).isEqualTo(StatusExecucaoColeta.SUCESSO_PARCIAL.name());
		assertThat(execJoobleValido.anunciosNovos()).isEqualTo(1);

		ConfiguracaoFonte joobleValidada = configuracaoFonteRepository.findById(fonteJooble.getId()).orElseThrow();
		assertThat(joobleValidada.getEstadoValidacao()).isEqualTo("ATIVA_VALIDADA");
		assertThat(joobleValidada.getUltimaColetaSucessoEm()).isNotNull();
		assertThat(joobleValidada.getPaginaContinuacao()).isEqualTo(1);
		assertThat(joobleValidada.getEstadoBusca()).contains("qa testes");

		// Na 2ª execução consulta 'qa testes' e conclui o ciclo indicado por totalCount.
		ExecucaoColetaDto execJoobleTermo2 = orquestradorColetaService.executarFonte(joobleValidada);
		assertThat(execJoobleTermo2.statusCodigo()).isEqualTo(StatusExecucaoColeta.SUCESSO.name());
		assertThat(payloadsRecebidosJooble.get(2)).contains("qa testes");

		ExecucaoColetaDto execAdzunaValido = orquestradorColetaService.executarFonte(
				configuracaoFonteRepository.findById(fonteAdzuna.getId()).orElseThrow()
		);
		assertThat(execAdzunaValido.statusCodigo()).isEqualTo(StatusExecucaoColeta.SUCESSO.name());
		assertThat(execAdzunaValido.anunciosNovos()).isEqualTo(1);
		assertThat(configuracaoFonteRepository.findById(fonteAdzuna.getId()).orElseThrow().getEstadoValidacao())
				.isEqualTo("ATIVA_VALIDADA");
	}

	@Test
	void deveIndicarAtualizacaoParcialEDegradadaQuandoFonteSaudavelCoexistirComOutraDesatualizadaOuComFalha() {
		Instant agora = Instant.now();

		ConfiguracaoFonte backendBr = configuracaoFonteRepository
				.findByFornecedorAndIdentificadorMural("GITHUB_ISSUES", "backend-br/vagas")
				.orElseThrow();
		backendBr.setAtivo(true);
		backendBr.setEstadoValidacao("ATIVA_VALIDADA");
		backendBr.setUltimaColetaSucessoEm(agora.minus(15, ChronoUnit.MINUTES));
		configuracaoFonteRepository.save(backendBr);

		ConfiguracaoFonte frontendBr = configuracaoFonteRepository
				.findByFornecedorAndIdentificadorMural("GITHUB_ISSUES", "frontendbr/vagas")
				.orElseThrow();
		frontendBr.setAtivo(true);
		frontendBr.setEstadoValidacao("ATIVA_VALIDADA");
		// Fonte desatualizada: última coleta há 16 horas (acima do limite de 12h)
		frontendBr.setUltimaColetaSucessoEm(agora.minus(16, ChronoUnit.HOURS));
		configuracaoFonteRepository.save(frontendBr);

		StatusColetaGlobalDto status = orquestradorColetaService.consultarStatusGlobal();

		// Mesmo com backend-br coletada há 15 minutos, o estado global NÃO esconde que frontendbr está desatualizada!
		assertThat(status.dadosDesatualizados()).isTrue();
		assertThat(status.atualizacaoParcial()).isTrue();
		assertThat(status.fontesAtivasTotal()).isEqualTo(2);
		assertThat(status.fontesAtualizadas()).isEqualTo(1);
		assertThat(status.fontesDesatualizadas()).isEqualTo(1);
		assertThat(status.motivoEstadoDados())
				.contains("Atualização parcial/degradada")
				.contains("Frontend Brasil");

		FonteConfiguradaDto dtoBackend = status.fontes().stream()
				.filter(f -> "backend-br/vagas".equals(f.identificadorMural()))
				.findFirst()
				.orElseThrow();
		assertThat(dtoBackend.situacaoAtualizacao()).isEqualTo("ATUALIZADA");
		assertThat(dtoBackend.desatualizada()).isFalse();

		FonteConfiguradaDto dtoFrontend = status.fontes().stream()
				.filter(f -> "frontendbr/vagas".equals(f.identificadorMural()))
				.findFirst()
				.orElseThrow();
		assertThat(dtoFrontend.situacaoAtualizacao()).isEqualTo("DESATUALIZADA");
		assertThat(dtoFrontend.desatualizada()).isTrue();
	}

	@Test
	void devePriorizarVagasMaisRecentesNosMuraisERotacionarBlocosQuandoHouverRecortePorLimiteECobrirJSearchNaoImplementado() {
		// Mural Greenhouse retorna 3 vagas técnicas fora de ordem cronológica; limite por execução configurado para 2
		radarProperties.getGreenhouse().setMaxItensPorMural(2);

		servidorTeste.createContext("/v1/boards/quintoandar/jobs", exchange -> {
			String json = """
					{
					  "jobs": [
					    {
					      "id": 10,
					      "title": "Backend Engineer Antiga (10/11)",
					      "first_published": "2026-11-10T10:00:00Z",
					      "updated_at": "2026-11-10T10:00:00Z",
					      "location": { "name": "São Paulo, Brasil" },
					      "departments": [{ "name": "Engineering" }],
					      "absolute_url": "https://job-boards.greenhouse.io/quintoandar/jobs/10",
					      "content": "Vaga Java antiga."
					    },
					    {
					      "id": 30,
					      "title": "Software Engineer Mais Recente (18/11)",
					      "first_published": "2026-11-18T10:00:00Z",
					      "updated_at": "2026-11-18T10:00:00Z",
					      "location": { "name": "Remoto, Brasil" },
					      "departments": [{ "name": "Engineering" }],
					      "absolute_url": "https://job-boards.greenhouse.io/quintoandar/jobs/30",
					      "content": "Vaga Java mais recente."
					    },
					    {
					      "id": 20,
					      "title": "Data Engineer Intermediária (15/11)",
					      "first_published": "2026-11-15T10:00:00Z",
					      "updated_at": "2026-11-15T10:00:00Z",
					      "location": { "name": "São Paulo, Brasil" },
					      "departments": [{ "name": "Data" }],
					      "absolute_url": "https://job-boards.greenhouse.io/quintoandar/jobs/20",
					      "content": "Vaga Python dados."
					    }
					  ]
					}
					""";
			responder(exchange, 200, "application/json", json);
		});
		servidorTeste.start();
		radarProperties.getGreenhouse().setApiBaseUrl("http://127.0.0.1:" + servidorTeste.getAddress().getPort());

		ConfiguracaoFonte muralGh = salvarFonte(GreenhouseConector.FORNECEDOR, "quintoandar", "Greenhouse • QuintoAndar", true, "CONFIGURADA_NAO_VALIDADA");

		// 1ª execução: prioriza as 2 mais recentes (IDs 30 e 20) e sinaliza recorte por limite com continuação no bloco 2
		ExecucaoColetaDto exec1 = orquestradorColetaService.executarFonte(muralGh);
		assertThat(exec1.statusCodigo()).isEqualTo(StatusExecucaoColeta.SUCESSO_PARCIAL.name());
		assertThat(exec1.anunciosValidos()).isEqualTo(2);
		assertThat(exec1.erroResumido()).contains("Recorte por limite de volume").contains("bloco 2");

		PaginaVagasDto aposExec1 = catalogoVagasService.listarVagas(0, 10, null, "TODAS", false, false);
		assertThat(aposExec1.itens())
				.extracting(v -> v.identificadorExternoPrincipal())
				.containsExactly("30", "20");

		// 2ª execução: avança para o bloco 2 e importa a vaga restante (ID 10), sem ficar preso ao mesmo prefixo!
		ConfiguracaoFonte muralGhBloco2 = configuracaoFonteRepository.findById(muralGh.getId()).orElseThrow();
		assertThat(muralGhBloco2.getPaginaContinuacao()).isEqualTo(2);

		ExecucaoColetaDto exec2 = orquestradorColetaService.executarFonte(muralGhBloco2);
		assertThat(exec2.statusCodigo()).isEqualTo(StatusExecucaoColeta.SUCESSO.name());
		assertThat(exec2.anunciosValidos()).isEqualTo(1);
		assertThat(oportunidadeRepository.count()).isEqualTo(3);

		// Verifica tratamento honesto do JSearch como conector não implementado
		ConfiguracaoFonte fonteJSearch = salvarFonte("JSEARCH_API", "tecnologia-brasil", "JSearch", false, "NAO_IMPLEMENTADA");
		ExecucaoColetaDto execJSearch = orquestradorColetaService.executarFonte(fonteJSearch);
		assertThat(execJSearch.statusCodigo()).isEqualTo(StatusExecucaoColeta.NAO_IMPLEMENTADA.name());
	}

	@Test
	void deveCarregarArquivoEnvComFormatoPropertiesSemExibirValoresDosSegredos(@TempDir Path tempDir) throws Exception {
		Path envTemporario = tempDir.resolve(".env");
		String segredoSimulado = "segredo_env_isolado_nao_exibir_123456";
		Files.writeString(envTemporario, """
				# Arquivo .env temporário de teste
				JOOBLE_API_KEY=%s
				RADAR_COLETA_DESATUALIZADO_APOS_HORAS=8
				""".formatted(segredoSimulado), StandardCharsets.UTF_8);

		PropertiesPropertySourceLoader loader = new PropertiesPropertySourceLoader();
		List<PropertySource<?>> sources = loader.load("dotenv-test", new FileSystemResource(envTemporario));
		assertThat(sources).isNotEmpty();
		assertThat(sources.get(0).getProperty("JOOBLE_API_KEY")).isEqualTo(segredoSimulado);
		assertThat(sources.get(0).getProperty("RADAR_COLETA_DESATUALIZADO_APOS_HORAS")).isEqualTo("8");

		// Simula aplicação da chave carregada no bean de propriedades e verifica que o status nunca vaza o valor
		radarProperties.getJooble().setApiKey(String.valueOf(sources.get(0).getProperty("JOOBLE_API_KEY")));
		salvarFonte(JoobleConector.FORNECEDOR, "tecnologia-brasil", "Jooble Brasil", true, "CONFIGURADA_NAO_VALIDADA");

		String respostaJson = mockMvc.perform(get("/api/coletas/status"))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString(StandardCharsets.UTF_8);

		assertThat(respostaJson).contains("\"credencialConfigurada\":true");
		assertThat(respostaJson).doesNotContain(segredoSimulado);
	}

	private ConfiguracaoFonte salvarFonte(String fornecedor, String idMural, String nome, boolean ativo, String estado) {
		ConfiguracaoFonte c = configuracaoFonteRepository
				.findByFornecedorAndIdentificadorMural(fornecedor, idMural)
				.orElseGet(ConfiguracaoFonte::new);
		c.setFornecedor(fornecedor);
		c.setIdentificadorMural(idMural);
		c.setNomeExibicao(nome);
		c.setAtivo(ativo);
		c.setCadenciaMinutos(360);
		c.setCategoria("TESTE");
		c.setEstadoValidacao(estado);
		if (c.getCriadoEm() == null) {
			c.setCriadoEm(Instant.now());
		}
		c.setAtualizadoEm(Instant.now());
		return configuracaoFonteRepository.save(c);
	}

	private void responder(com.sun.net.httpserver.HttpExchange exchange, int status, String contentType, String corpo) throws IOException {
		exchange.getResponseHeaders().add("Content-Type", contentType + "; charset=utf-8");
		byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
		exchange.sendResponseHeaders(status, bytes.length);
		try (OutputStream os = exchange.getResponseBody()) {
			os.write(bytes);
		}
	}
}

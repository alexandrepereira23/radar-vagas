package br.com.radarvagas;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.radarvagas.acoes.AcaoPessoalRepository;
import br.com.radarvagas.catalogo.AnuncioOrigemRepository;
import br.com.radarvagas.catalogo.CatalogoVagasService;
import br.com.radarvagas.catalogo.OportunidadeRepository;
import br.com.radarvagas.catalogo.PaginaVagasDto;
import br.com.radarvagas.catalogo.VagaDetalheDto;
import br.com.radarvagas.coleta.AdzunaConector;
import br.com.radarvagas.coleta.AshbyConector;
import br.com.radarvagas.coleta.ConfiguracaoFonte;
import br.com.radarvagas.coleta.ConfiguracaoFonteRepository;
import br.com.radarvagas.coleta.ExecucaoColetaDto;
import br.com.radarvagas.coleta.ExecucaoColetaRepository;
import br.com.radarvagas.coleta.GreenhouseConector;
import br.com.radarvagas.coleta.GupyPortalConector;
import br.com.radarvagas.coleta.JoobleConector;
import br.com.radarvagas.coleta.OrquestradorColetaService;
import br.com.radarvagas.coleta.StatusExecucaoColeta;
import br.com.radarvagas.config.RadarProperties;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class MultiplasFontesEAgrupamentoTest extends ColetaTestFixture {

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

	private HttpServer servidorMultiFonte;

	@BeforeEach
	void setUp() throws IOException {
		servidorMultiFonte = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

		// 1. Endpoint simulado Greenhouse
		servidorMultiFonte.createContext("/v1/boards/quintoandar/jobs", exchange -> {
			String json = """
					{
					  "jobs": [
					    {
					      "id": 44001,
					      "title": "Senior Backend Software Engineer (Java/Kotlin)",
					      "requisition_id": "REQ-QA-901",
					      "first_published": "2026-11-14T10:00:00-03:00",
					      "updated_at": "2026-11-16T15:00:00-03:00",
					      "location": { "name": "Remoto - Brasil" },
					      "departments": [{ "name": "Engineering" }],
					      "absolute_url": "https://job-boards.greenhouse.io/quintoandar/jobs/44001",
					      "content": "&lt;p&gt;Atuação com Java, Spring Boot e microsserviços no Brasil.&lt;/p&gt;"
					    },
					    {
					      "id": 44002,
					      "title": "Consultor de Vendas Imobiliárias (Account Executive)",
					      "requisition_id": "REQ-QA-902",
					      "first_published": "2026-11-14T11:00:00-03:00",
					      "updated_at": "2026-11-16T15:00:00-03:00",
					      "location": { "name": "São Paulo, Brasil" },
					      "departments": [{ "name": "Sales" }],
					      "absolute_url": "https://job-boards.greenhouse.io/quintoandar/jobs/44002",
					      "content": "Vaga comercial de vendas."
					    },
					    {
					      "id": 44003,
					      "title": "Staff Infrastructure Engineer",
					      "requisition_id": "REQ-QA-903",
					      "first_published": null,
					      "updated_at": "2026-11-17T12:00:00Z",
					      "location": { "name": "San Francisco - US Only" },
					      "departments": [{ "name": "Engineering" }],
					      "absolute_url": "https://job-boards.greenhouse.io/quintoandar/jobs/44003",
					      "content": "Must reside in the US. US Only."
					    }
					  ]
					}
					""";
			responder(exchange, 200, "application/json", json);
		});

		// 2. Endpoint simulado Gupy Portal (__NEXT_DATA__ estruturado)
		servidorMultiFonte.createContext("/job-search/", exchange -> {
			String html = """
					<!DOCTYPE html>
					<html>
					<head></head>
					<body>
					<script id="__NEXT_DATA__" type="application/json">
					{
					  "props": {
					    "pageProps": {
					      "initialJobList": {
					        "data": [
					          {
					            "id": 99001,
					            "name": "Pessoa Desenvolvedora Full Stack Pleno (Angular / Java)",
					            "description": "Desenvolvimento com Java, Spring Boot e Angular em São Paulo.",
					            "careerPageName": "TechBrasil S.A.",
					            "publishedDate": "2026-11-16T14:30:00.000Z",
					            "workplaceType": "hybrid",
					            "type": "vacancy_type_effective",
					            "city": "São Paulo",
					            "state": "SP",
					            "jobUrl": "https://techbrasilsa.gupy.io/job/99001?jobBoardSource=gupy_portal"
					          },
					          {
					            "id": 99002,
					            "name": "Banco de Talentos - Engenharia de Dados",
					            "description": "Banco de talentos para engenharia de dados com Python.",
					            "careerPageName": "TechBrasil S.A.",
					            "publishedDate": "2026-11-15T09:00:00.000Z",
					            "workplaceType": "remote",
					            "type": "vacancy_type_talent_pool",
					            "city": "",
					            "state": "",
					            "jobUrl": "https://techbrasilsa.gupy.io/job/99002?jobBoardSource=gupy_portal"
					          }
					        ]
					      }
					    }
					  }
					}
					</script>
					</body>
					</html>
					""";
			responder(exchange, 200, "text/html", html);
		});

		// 3. Endpoint simulado Ashby
		servidorMultiFonte.createContext("/posting-api/job-board/supabase", exchange -> {
			String json = """
					{
					  "jobs": [
					    {
					      "id": "ashby-br-1",
					      "title": "Database Reliability Engineer (PostgreSQL)",
					      "department": "Engineering",
					      "team": "Database",
					      "location": "Remote, AMER (Brazil eligible)",
					      "publishedAt": "2026-11-15T18:00:00Z",
					      "isListed": true,
					      "isRemote": true,
					      "workplaceType": "Remote",
					      "descriptionPlain": "Work with PostgreSQL, Go and Cloud infrastructure from Brazil or Latin America.",
					      "jobUrl": "https://jobs.ashbyhq.com/supabase/ashby-br-1",
					      "applyUrl": "https://jobs.ashbyhq.com/supabase/ashby-br-1/application"
					    }
					  ]
					}
					""";
			responder(exchange, 200, "application/json", json);
		});

		// 4. Endpoint simulado GitHub Issues trazendo as MESMAS vagas da Gupy e do Greenhouse com parâmetros utm_*
		servidorMultiFonte.createContext("/repos/backend-br/vagas/issues", exchange -> {
			String json = """
					[
					  {
					    "number": 8801,
					    "title": "[Remoto] Senior Backend Software Engineer @ QuintoAndar",
					    "body": "Candidate-se no mural oficial: https://job-boards.greenhouse.io/quintoandar/jobs/44001?utm_source=github&gh_src=comunidade",
					    "state": "open",
					    "html_url": "https://github.com/backend-br/vagas/issues/8801",
					    "created_at": "2026-11-15T12:00:00Z",
					    "updated_at": "2026-11-15T12:00:00Z",
					    "labels": [{ "name": "Remoto" }, { "name": "Sênior" }]
					  },
					  {
					    "number": 8802,
					    "title": "[Híbrido - SP] Pessoa Desenvolvedora Full Stack Pleno @ TechBrasil S.A.",
					    "body": "Inscreva-se pela Gupy: https://techbrasilsa.gupy.io/job/99001?utm_medium=social&fbclid=xyz987",
					    "state": "open",
					    "html_url": "https://github.com/backend-br/vagas/issues/8802",
					    "created_at": "2026-11-16T16:00:00Z",
					    "updated_at": "2026-11-16T16:00:00Z",
					    "labels": [{ "name": "Híbrido" }]
					  }
					]
					""";
			exchange.getResponseHeaders().add("X-RateLimit-Remaining", "55");
			responder(exchange, 200, "application/json", json);
		});

		servidorMultiFonte.start();
		int porta = servidorMultiFonte.getAddress().getPort();
		String baseLocal = "http://127.0.0.1:" + porta;
		radarProperties.getGithub().setApiBaseUrl(baseLocal);
		radarProperties.getGreenhouse().setApiBaseUrl(baseLocal);
		radarProperties.getGupy().setPortalBaseUrl(baseLocal);
		radarProperties.getAshby().setApiBaseUrl(baseLocal);
		radarProperties.getJooble().setApiKey("");
		radarProperties.getAdzuna().setAppId("");
		radarProperties.getAdzuna().setAppKey("");
	}

	@AfterEach
	void tearDown() {
		if (servidorMultiFonte != null) {
			servidorMultiFonte.stop(0);
		}
	}

	@Test
	void deveColetarMultiplasFontesFiltrarCargosTecnicosEAgruparConservadoramenteEntreFontesDistintas() {
		ConfiguracaoFonte fonteGreenhouse = criarFonte(GreenhouseConector.FORNECEDOR, "quintoandar", "Greenhouse • QuintoAndar", true, "ATIVA_VALIDADA");
		ConfiguracaoFonte fonteGupy = criarFonte(GupyPortalConector.FORNECEDOR, "desenvolvedor", "Gupy Portal", true, "ATIVA_VALIDADA");
		ConfiguracaoFonte fonteAshby = criarFonte(AshbyConector.FORNECEDOR, "supabase", "Ashby • Supabase", true, "ATIVA_VALIDADA");
		ConfiguracaoFonte fonteGithub = configuracaoFonteRepository
				.findByFornecedorAndIdentificadorMural("GITHUB_ISSUES", "backend-br/vagas")
				.orElseGet(() -> criarFonte("GITHUB_ISSUES", "backend-br/vagas", "Backend Brasil", true, "ATIVA_VALIDADA"));

		// 1. Coleta Greenhouse: 3 vagas no payload (1 técnica BR válida, 1 de vendas ignorada, 1 US Only ignorada)
		ExecucaoColetaDto execGh = orquestradorColetaService.executarFonte(fonteGreenhouse);
		assertThat(execGh.statusCodigo()).isEqualTo(StatusExecucaoColeta.SUCESSO.name());
		assertThat(execGh.anunciosRecebidos()).isEqualTo(3);
		assertThat(execGh.anunciosValidos()).isEqualTo(1);
		assertThat(execGh.anunciosIgnorados()).isEqualTo(2);
		assertThat(execGh.anunciosNovos()).isEqualTo(1);

		// 2. Coleta Gupy Portal: 2 vagas no __NEXT_DATA__ (1 vaga aberta + 1 banco de talentos)
		ExecucaoColetaDto execGupy = orquestradorColetaService.executarFonte(fonteGupy);
		assertThat(execGupy.statusCodigo()).isEqualTo(StatusExecucaoColeta.SUCESSO_PARCIAL.name());
		assertThat(execGupy.anunciosRecebidos()).isEqualTo(2);
		assertThat(execGupy.anunciosValidos()).isEqualTo(2);
		assertThat(execGupy.anunciosNovos()).isEqualTo(2);

		// 3. Coleta Ashby: 1 vaga técnica elegível no Brasil
		ExecucaoColetaDto execAshby = orquestradorColetaService.executarFonte(fonteAshby);
		assertThat(execAshby.statusCodigo()).isEqualTo(StatusExecucaoColeta.SUCESSO.name());
		assertThat(execAshby.anunciosValidos()).isEqualTo(1);
		assertThat(execAshby.anunciosNovos()).isEqualTo(1);

		// Até aqui temos 4 oportunidades (3 VAGA_ABERTA + 1 BANCO_DE_TALENTOS) e 4 anúncios de origem
		assertThat(oportunidadeRepository.count()).isEqualTo(4);
		assertThat(anuncioOrigemRepository.count()).isEqualTo(4);

		// 4. Coleta GitHub Issues com 2 anúncios que apontam para os MESMOS links da vaga do Greenhouse e da Gupy
		ExecucaoColetaDto execGithub = orquestradorColetaService.executarFonte(fonteGithub);
		assertThat(execGithub.statusCodigo()).isEqualTo(StatusExecucaoColeta.SUCESSO.name());
		assertThat(execGithub.anunciosValidos()).isEqualTo(2);
		assertThat(execGithub.anunciosNovos()).isEqualTo(2);

		// Os 2 novos anúncios do GitHub foram unidos conservadoramente às oportunidades já criadas pelo Greenhouse e pela Gupy!
		assertThat(anuncioOrigemRepository.count()).isEqualTo(6);
		assertThat(oportunidadeRepository.count()).isEqualTo(4);

		// Verifica filtro por fonte e contagem de anúncios consolidados
		PaginaVagasDto vagasGreenhouse = catalogoVagasService.listarVagas(0, 10, null, "GREENHOUSE_BOARD", "TODAS", false, false);
		assertThat(vagasGreenhouse.totalElementos()).isEqualTo(1);
		assertThat(vagasGreenhouse.itens().get(0).quantidadeAnunciosOrigem()).isEqualTo(2);

		VagaDetalheDto detalheConsolidado = catalogoVagasService.buscarDetalhe(vagasGreenhouse.itens().get(0).id()).orElseThrow();
		assertThat(detalheConsolidado.anunciosOrigem()).hasSize(2);
		assertThat(detalheConsolidado.anunciosOrigem())
				.extracting(a -> a.fornecedor())
				.containsExactlyInAnyOrder("GREENHOUSE_BOARD", "GITHUB_ISSUES");
	}

	@Test
	void deveRegistrarBloqueioExplicitoQuandoAgregadoresNaoPossuiremCredencialConfigurada() {
		ConfiguracaoFonte fonteJooble = criarFonte(JoobleConector.FORNECEDOR, "tecnologia-brasil", "Jooble Brasil", false, "BLOQUEADA_CREDENCIAL");
		ConfiguracaoFonte fonteAdzuna = criarFonte(AdzunaConector.FORNECEDOR, "tecnologia-brasil", "Adzuna Brasil", false, "BLOQUEADA_CREDENCIAL");
		ConfiguracaoFonte fonteLinkedin = criarFonte("LINKEDIN", "linkedin-jobs", "LinkedIn", false, "BLOQUEADA_RESTRICAO_ACESSO");

		ExecucaoColetaDto execJooble = orquestradorColetaService.executarFonte(fonteJooble);
		assertThat(execJooble.statusCodigo()).isEqualTo(StatusExecucaoColeta.BLOQUEADA_CREDENCIAL.name());
		assertThat(execJooble.erroResumido()).contains("JOOBLE_API_KEY");

		ExecucaoColetaDto execAdzuna = orquestradorColetaService.executarFonte(fonteAdzuna);
		assertThat(execAdzuna.statusCodigo()).isEqualTo(StatusExecucaoColeta.BLOQUEADA_CREDENCIAL.name());
		assertThat(execAdzuna.erroResumido()).contains("ADZUNA_APP_ID");

		ExecucaoColetaDto execLinkedin = orquestradorColetaService.executarFonte(fonteLinkedin);
		assertThat(execLinkedin.statusCodigo()).isEqualTo(StatusExecucaoColeta.BLOQUEADA_ACESSO.name());
	}

	private ConfiguracaoFonte criarFonte(String fornecedor, String idMural, String nome, boolean ativo, String estado) {
		return configuracaoFonteRepository.findByFornecedorAndIdentificadorMural(fornecedor, idMural)
				.orElseGet(() -> {
					ConfiguracaoFonte c = new ConfiguracaoFonte();
					c.setFornecedor(fornecedor);
					c.setIdentificadorMural(idMural);
					c.setNomeExibicao(nome);
					c.setAtivo(ativo);
					c.setCadenciaMinutos(360);
					c.setCategoria("TESTE");
					c.setEstadoValidacao(estado);
					c.setObservacaoValidacao("Configuração de teste");
					c.setCriadoEm(Instant.now());
					c.setAtualizadoEm(Instant.now());
					return configuracaoFonteRepository.save(c);
				});
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

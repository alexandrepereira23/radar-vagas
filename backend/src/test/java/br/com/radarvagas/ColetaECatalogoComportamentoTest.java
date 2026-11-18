package br.com.radarvagas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.radarvagas.acoes.AcaoPessoal;
import br.com.radarvagas.acoes.AcaoPessoalRepository;
import br.com.radarvagas.catalogo.AnuncioOrigemRepository;
import br.com.radarvagas.catalogo.CatalogoVagasService;
import br.com.radarvagas.catalogo.DisponibilidadeVaga;
import br.com.radarvagas.catalogo.Oportunidade;
import br.com.radarvagas.catalogo.OportunidadeRepository;
import br.com.radarvagas.catalogo.PaginaVagasDto;
import br.com.radarvagas.coleta.ConfiguracaoFonte;
import br.com.radarvagas.coleta.ConfiguracaoFonteRepository;
import br.com.radarvagas.coleta.ExecucaoColetaDto;
import br.com.radarvagas.coleta.ExecucaoColetaRepository;
import br.com.radarvagas.coleta.OrquestradorColetaService;
import br.com.radarvagas.coleta.StatusExecucaoColeta;
import br.com.radarvagas.config.RadarProperties;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ColetaECatalogoComportamentoTest {

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

	private HttpServer servidorGitHubSimulado;
	private final AtomicInteger chamadasHttp = new AtomicInteger(0);
	private final AtomicInteger statusCodeResposta = new AtomicInteger(200);
	private final AtomicReference<String> rateLimitRemainingHeader = new AtomicReference<>("58");
	private final AtomicReference<String> payloadBackendBr = new AtomicReference<>("[]");
	private final AtomicReference<String> payloadFrontendBr = new AtomicReference<>("[]");

	@BeforeEach
	void setUp() throws IOException {
		acaoPessoalRepository.deleteAll();
		anuncioOrigemRepository.deleteAll();
		oportunidadeRepository.deleteAll();
		execucaoColetaRepository.deleteAll();

		for (ConfiguracaoFonte cfg : configuracaoFonteRepository.findAll()) {
			cfg.setUltimaColetaSucessoEm(null);
			cfg.setCursorUltimaAtualizacao(null);
			configuracaoFonteRepository.save(cfg);
		}

		chamadasHttp.set(0);
		statusCodeResposta.set(200);
		rateLimitRemainingHeader.set("58");
		payloadBackendBr.set(carregarFixture("/fixtures/github_issues_pagina1.json"));
		payloadFrontendBr.set("[]");

		servidorGitHubSimulado = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		servidorGitHubSimulado.createContext("/repos/", exchange -> {
			chamadasHttp.incrementAndGet();
			String path = exchange.getRequestURI().getPath();
			String query = exchange.getRequestURI().getQuery();
			int page = (query != null && query.contains("page=2")) ? 2 : 1;

			exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
			exchange.getResponseHeaders().add("X-RateLimit-Remaining", rateLimitRemainingHeader.get());
			exchange.getResponseHeaders().add("X-RateLimit-Reset", "1795000000");

			int status = statusCodeResposta.get();
			String corpo;
			if (status != 200) {
				corpo = "{\"message\": \"API rate limit exceeded\"}";
			} else if (page > 1) {
				corpo = "[]";
			} else if (path.contains("backend-br/vagas")) {
				corpo = payloadBackendBr.get();
			} else {
				corpo = payloadFrontendBr.get();
			}

			byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(status, bytes.length);
			try (OutputStream os = exchange.getResponseBody()) {
				os.write(bytes);
			}
		});
		servidorGitHubSimulado.start();

		int porta = servidorGitHubSimulado.getAddress().getPort();
		radarProperties.getGithub().setApiBaseUrl("http://127.0.0.1:" + porta);
	}

	@AfterEach
	void tearDown() {
		if (servidorGitHubSimulado != null) {
			servidorGitHubSimulado.stop(0);
		}
	}

	@Test
	void deveExecutarColetaIdempotenteRespeitandoDatasDesconhecidasEFiltrandoItensInvalidos() {
		ConfiguracaoFonte backendBr = configuracaoFonteRepository
				.findByFornecedorAndIdentificadorMural("GITHUB_ISSUES", "backend-br/vagas")
				.orElseThrow();

		// 1ª execução: recebe 6 itens na fixture (2 vagas abertas + 1 banco de talentos + 3 ignorados: PR, regras e currículo)
		ExecucaoColetaDto exec1 = orquestradorColetaService.executarFonte(backendBr);

		assertThat(exec1.statusCodigo()).isEqualTo(StatusExecucaoColeta.SUCESSO.name());
		assertThat(exec1.anunciosRecebidos()).isEqualTo(6);
		assertThat(exec1.anunciosIgnorados()).isEqualTo(3);
		assertThat(exec1.anunciosValidos()).isEqualTo(3);
		assertThat(exec1.anunciosNovos()).isEqualTo(3);
		assertThat(exec1.anunciosAtualizados()).isZero();
		assertThat(exec1.anunciosRepetidos()).isZero();
		assertThat(exec1.rateLimitRestante()).isEqualTo(58);

		assertThat(anuncioOrigemRepository.count()).isEqualTo(3);
		assertThat(oportunidadeRepository.count()).isEqualTo(3);

		// 2ª execução com os mesmos dados: não pode duplicar anúncios nem oportunidades
		ExecucaoColetaDto exec2 = orquestradorColetaService.executarFonte(backendBr);
		assertThat(exec2.statusCodigo()).isEqualTo(StatusExecucaoColeta.SUCESSO.name());
		assertThat(exec2.anunciosNovos()).isZero();
		assertThat(exec2.anunciosAtualizados()).isZero();
		assertThat(exec2.anunciosRepetidos()).isEqualTo(3);
		assertThat(anuncioOrigemRepository.count()).isEqualTo(3);
		assertThat(oportunidadeRepository.count()).isEqualTo(3);

		// Listagem padrão traz apenas VAGA_ABERTA (2 itens), excluindo BANCO_DE_TALENTOS
		PaginaVagasDto listagemPadrao = catalogoVagasService.listarVagas(0, 10, null, "TODAS", false, false);
		assertThat(listagemPadrao.totalElementos()).isEqualTo(2);

		// Ordenação: vaga com publicação conhecida (#1001) vem antes da vaga sem publicação conhecida (#1002)
		assertThat(listagemPadrao.itens().get(0).identificadorExternoPrincipal()).isEqualTo("1001");
		assertThat(listagemPadrao.itens().get(0).publicacaoConhecida()).isTrue();
		assertThat(listagemPadrao.itens().get(0).publicadoEm()).isEqualTo(Instant.parse("2026-11-15T10:00:00Z"));

		assertThat(listagemPadrao.itens().get(1).identificadorExternoPrincipal()).isEqualTo("1002");
		assertThat(listagemPadrao.itens().get(1).publicacaoConhecida()).isFalse();
		assertThat(listagemPadrao.itens().get(1).publicadoEm()).isNull();

		// Filtro estrito de publicação conhecida exclui a de publicação desconhecida
		PaginaVagasDto apenasConhecidas = catalogoVagasService.listarVagas(0, 10, null, "CONHECIDA", false, false);
		assertThat(apenasConhecidas.totalElementos()).isEqualTo(1);
		assertThat(apenasConhecidas.itens().get(0).identificadorExternoPrincipal()).isEqualTo("1001");

		// Permite consultar separadamente as de publicação desconhecida
		PaginaVagasDto apenasDesconhecidas = catalogoVagasService.listarVagas(0, 10, null, "DESCONHECIDA", false, false);
		assertThat(apenasDesconhecidas.totalElementos()).isEqualTo(1);
		assertThat(apenasDesconhecidas.itens().get(0).identificadorExternoPrincipal()).isEqualTo("1002");

		// Permite consultar bancos de talentos separadamente
		PaginaVagasDto bancosTalentos = catalogoVagasService.listarVagas(0, 10, null, "TODAS", false, true);
		assertThat(bancosTalentos.totalElementos()).isEqualTo(1);
		assertThat(bancosTalentos.itens().get(0).identificadorExternoPrincipal()).isEqualTo("1006");
	}

	@Test
	void deveAtualizarSemRecriarPreservarAcaoPessoalENaoEncerrarVagaAusenteEmColetaParcial() {
		ConfiguracaoFonte backendBr = configuracaoFonteRepository
				.findByFornecedorAndIdentificadorMural("GITHUB_ISSUES", "backend-br/vagas")
				.orElseThrow();

		orquestradorColetaService.executarFonte(backendBr);

		Oportunidade vaga1001 = oportunidadeRepository.findAll().stream()
				.filter(o -> "1001".equals(o.getIdentificadorExternoPrincipal()))
				.findFirst()
				.orElseThrow();
		Long idOriginal1001 = vaga1001.getId();
		Instant publicacaoOriginal1001 = vaga1001.getPublicadoEm();

		// Registra ação pessoal na oportunidade #1001
		AcaoPessoal acao = new AcaoPessoal();
		acao.setOportunidade(vaga1001);
		acao.setJaMeCandidatei(true);
		acao.setOculta(false);
		acao.setCandidatadoEm(Instant.parse("2026-11-17T12:00:00Z"));
		acao.setAtualizadoEm(Instant.parse("2026-11-17T12:00:00Z"));
		acaoPessoalRepository.save(acao);

		// Simula coleta parcial onde a #1001 foi editada (novo updated_at e novo texto) e a #1002 não apareceu na página parcial
		payloadBackendBr.set("""
				[
				  {
				    "number": 1001,
				    "title": "[Remoto] Pessoa Desenvolvedora Java Sênior @ Acme Tech",
				    "body": "Descrição atualizada com requisitos adicionais de Kafka e Spring Boot.",
				    "state": "open",
				    "html_url": "https://github.com/backend-br/vagas/issues/1001",
				    "created_at": "2026-11-15T10:00:00Z",
				    "updated_at": "2026-11-17T15:30:00Z",
				    "labels": [{ "name": "Remoto" }, { "name": "Sênior" }]
				  }
				]
				""");

		ExecucaoColetaDto execAtualizacao = orquestradorColetaService.executarFonte(backendBr);
		assertThat(execAtualizacao.anunciosNovos()).isZero();
		assertThat(execAtualizacao.anunciosAtualizados()).isEqualTo(1);

		Oportunidade vaga1001Depois = oportunidadeRepository.findById(idOriginal1001).orElseThrow();
		// A atualização não mudou a data de publicação original nem criou outro registro
		assertThat(vaga1001Depois.getPublicadoEm()).isEqualTo(publicacaoOriginal1001);
		assertThat(vaga1001Depois.getAtualizadoNaFonteEm()).isEqualTo(Instant.parse("2026-11-17T15:30:00Z"));
		assertThat(vaga1001Depois.getDescricao()).contains("requisitos adicionais");

		// A ação pessoal foi preservada intacta
		AcaoPessoal acaoDepois = acaoPessoalRepository.findByOportunidadeId(idOriginal1001).orElseThrow();
		assertThat(acaoDepois.isJaMeCandidatei()).isTrue();

		// A vaga #1002 ausente na página parcial continua DISPONIVEL (ausência parcial não é encerramento!)
		Oportunidade vaga1002Depois = oportunidadeRepository.findAll().stream()
				.filter(o -> "1002".equals(o.getIdentificadorExternoPrincipal()))
				.findFirst()
				.orElseThrow();
		assertThat(vaga1002Depois.getDisponibilidadeConsolidada()).isEqualTo(DisponibilidadeVaga.DISPONIVEL);
	}

	@Test
	void deveAgruparConservadoramenteApenasQuandoHouverMesmoLinkEspecificoDeCandidatura() {
		// Anúncio em backend-br e anúncio em frontendbr apontando para o MESMO link específico de candidatura (com rastreamento diferente)
		payloadBackendBr.set("""
				[
				  {
				    "number": 501,
				    "title": "[Remoto] Engenheiro Full Stack @ Zeta Sistemas",
				    "body": "Candidatura em https://zetasistemas.gupy.io/jobs/888999?utm_source=backendbr",
				    "state": "open",
				    "html_url": "https://github.com/backend-br/vagas/issues/501",
				    "created_at": "2026-11-16T10:00:00Z",
				    "updated_at": "2026-11-16T10:00:00Z",
				    "labels": []
				  },
				  {
				    "number": 502,
				    "title": "[Remoto] Pessoa Desenvolvedora @ Zeta Sistemas",
				    "body": "Vaga sem link externo compartilhado (equipe A).",
				    "state": "open",
				    "html_url": "https://github.com/backend-br/vagas/issues/502",
				    "created_at": "2026-11-16T11:00:00Z",
				    "updated_at": "2026-11-16T11:00:00Z",
				    "labels": []
				  }
				]
				""");
		payloadFrontendBr.set("""
				[
				  {
				    "number": 701,
				    "title": "[Remoto] Full Stack Developer @ Zeta Sistemas",
				    "body": "Candidate-se pelo link https://zetasistemas.gupy.io/jobs/888999?utm_source=frontendbr&fbclid=abc",
				    "state": "open",
				    "html_url": "https://github.com/frontendbr/vagas/issues/701",
				    "created_at": "2026-11-16T10:05:00Z",
				    "updated_at": "2026-11-16T10:05:00Z",
				    "labels": []
				  },
				  {
				    "number": 702,
				    "title": "[Remoto] Pessoa Desenvolvedora @ Zeta Sistemas",
				    "body": "Vaga com mesmo cargo e empresa mas em outro repositório sem link compartilhado (equipe B).",
				    "state": "open",
				    "html_url": "https://github.com/frontendbr/vagas/issues/702",
				    "created_at": "2026-11-16T11:05:00Z",
				    "updated_at": "2026-11-16T11:05:00Z",
				    "labels": []
				  }
				]
				""");

		List<ExecucaoColetaDto> execs = orquestradorColetaService.executarFontesAtivas();
		assertThat(execs).hasSize(2);

		// Foram salvos 4 anúncios de origem, mas 3 oportunidades consolidadas (501 e 701 unidas; 502 e 702 separadas!)
		assertThat(anuncioOrigemRepository.count()).isEqualTo(4);
		assertThat(oportunidadeRepository.count()).isEqualTo(3);
	}

	@Test
	void deveRegistrarLimiteAtingidoQuandoGitHubRetornarRateLimit() {
		statusCodeResposta.set(403);
		rateLimitRemainingHeader.set("0");

		ConfiguracaoFonte backendBr = configuracaoFonteRepository
				.findByFornecedorAndIdentificadorMural("GITHUB_ISSUES", "backend-br/vagas")
				.orElseThrow();

		ExecucaoColetaDto exec = orquestradorColetaService.executarFonte(backendBr);
		assertThat(exec.statusCodigo()).isEqualTo(StatusExecucaoColeta.LIMITE_ATINGIDO.name());
		assertThat(exec.rateLimitRestante()).isZero();
		assertThat(exec.rateLimitResetEm()).isEqualTo(Instant.ofEpochSecond(1795000000L));
		assertThat(exec.erroResumido()).contains("Limite da API do GitHub atingido");
	}

	@Test
	void devePaginarVagasPelaApiSemDispararColetaExternaNaListagem() throws Exception {
		ConfiguracaoFonte backendBr = configuracaoFonteRepository
				.findByFornecedorAndIdentificadorMural("GITHUB_ISSUES", "backend-br/vagas")
				.orElseThrow();
		orquestradorColetaService.executarFonte(backendBr);

		int chamadasAntesDaListagem = chamadasHttp.get();

		// Consulta página 0 com tamanho 1 via HTTP REST
		mockMvc.perform(get("/api/vagas").param("page", "0").param("size", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.pagina").value(0))
				.andExpect(jsonPath("$.tamanhoPagina").value(1))
				.andExpect(jsonPath("$.totalElementos").value(2))
				.andExpect(jsonPath("$.totalPaginas").value(2))
				.andExpect(jsonPath("$.temProxima").value(true))
				.andExpect(jsonPath("$.itens[0].identificadorExternoPrincipal").value("1001"));

		// Consulta página 1 com tamanho 1 via HTTP REST
		mockMvc.perform(get("/api/vagas").param("page", "1").param("size", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.pagina").value(1))
				.andExpect(jsonPath("$.temAnterior").value(true))
				.andExpect(jsonPath("$.temProxima").value(false))
				.andExpect(jsonPath("$.itens[0].identificadorExternoPrincipal").value("1002"));

		// Garante que consultar /api/vagas nunca acionou requisição externa ao GitHub
		assertThat(chamadasHttp.get()).isEqualTo(chamadasAntesDaListagem);
	}

	@Test
	void deveRecuperarExecucaoInterrompidaNoStartup() {
		br.com.radarvagas.coleta.ExecucaoColeta interrompida = new br.com.radarvagas.coleta.ExecucaoColeta();
		interrompida.setFornecedor("GITHUB_ISSUES");
		interrompida.setIdentificadorMural("backend-br/vagas");
		interrompida.setStatus(StatusExecucaoColeta.EM_ANDAMENTO);
		interrompida.setIniciadoEm(Instant.parse("2026-11-17T08:00:00Z"));
		interrompida = execucaoColetaRepository.save(interrompida);

		orquestradorColetaService.inicializarFontesERecuperarInterrompidas();

		br.com.radarvagas.coleta.ExecucaoColeta recuperada = execucaoColetaRepository.findById(interrompida.getId()).orElseThrow();
		assertThat(recuperada.getStatus()).isEqualTo(StatusExecucaoColeta.FALHA);
		assertThat(recuperada.getFinalizadoEm()).isNotNull();
		assertThat(recuperada.getErroResumido()).contains("interrompida");
	}

	private String carregarFixture(String caminho) throws IOException {
		try (InputStream is = getClass().getResourceAsStream(caminho)) {
			if (is == null) {
				throw new IllegalStateException("Fixture não encontrada: " + caminho);
			}
			return new String(is.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}

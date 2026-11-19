package br.com.radarvagas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.radarvagas.acoes.AcaoPessoalRepository;
import br.com.radarvagas.catalogo.*;
import br.com.radarvagas.coleta.AnuncioExtraido;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CatalogoEtapa3Test {
	private static final Instant AGORA = Instant.parse("2026-11-19T15:12:30Z");
	@Autowired MockMvc api;
	@Autowired CatalogoVagasService catalogo;
	@Autowired OportunidadeRepository vagas;
	@Autowired AnuncioOrigemRepository anuncios;
	@Autowired AcaoPessoalRepository acoes;
	@Autowired Relogio relogio;

	@BeforeEach
	void preparar() {
		acoes.deleteAllInBatch();
		anuncios.deleteAllInBatch();
		vagas.deleteAllInBatch();
		relogio.agora = AGORA;
	}

	@Test
	void filtrosCombinamNoCatalogoInteiroSemDuplicarPorFonteSecundaria() throws Exception {
		for (int n = 1; n <= 3; n++) {
			gravar(n, "GITHUB_ISSUES", "principal", "Backend", "São Paulo/SP", List.of("Java", "Spring"), AGORA);
			gravar(n, "ADZUNA_API", "secundaria", "Backend", "São Paulo/SP", List.of("Java"), AGORA);
			gravar(n, "ADZUNA_API", "outra", "Backend", "São Paulo/SP", List.of("Java"), AGORA);
		}
		gravar(4, "ADZUNA_API", "principal", "Backend", "Recife", List.of("Java"), AGORA);
		gravar(5, "ADZUNA_API", "principal", "Backend", "São Paulo/SP", List.of("JavaScript"), AGORA);
		gravar(6, "GITHUB_ISSUES", "principal", "Backend", "São Paulo/SP", List.of("Java"), AGORA);
		long primeiro = id(3);
		long ultimo = id(1);
		for (int pagina = 0; pagina < 2; pagina++) {
			api.perform(get("/api/vagas").param("page", "" + pagina).param("size", "2")
					.param("q", "back").param("area", "DESENVOLVIMENTO").param("senioridade", "JUNIOR")
					.param("modalidade", "REMOTO").param("localizacao", "são PAULO").param("tecnologia", "java")
					.param("publicacao", "ULTIMOS_7_DIAS").param("fonte", "ADZUNA_API"))
					.andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(3))
					.andExpect(jsonPath("$.totalPaginas").value(2))
					.andExpect(jsonPath("$.itens[0].id").value(pagina == 0 ? primeiro : ultimo))
					.andExpect(jsonPath("$.itens[0].quantidadeAnunciosOrigem").value(3));
		}
		api.perform(get("/api/vagas/opcoes")).andExpect(status().isOk())
				.andExpect(jsonPath("$.catalogoVazio").value(false))
				.andExpect(jsonPath("$.tecnologias[1]").value("JavaScript"))
				.andExpect(jsonPath("$.areas[?(@.codigo == 'NAO_INFORMADA')].rotulo").isNotEmpty());
	}

	@Test
	void janelasCorridasIncluemAmbasAsBordasMasNaoFuturasNemDesconhecidas() throws Exception {
		for (int horas : List.of(24, 72, 168)) {
			Instant limite = AGORA.minus(horas, ChronoUnit.HOURS);
			gravar(horas, "GITHUB_ISSUES", "principal", "Backend " + horas, null, List.of(), limite);
			gravar(horas + 1, "GITHUB_ISSUES", "principal", "Backend " + horas, null, List.of(), limite.minusMillis(1));
			gravar(horas + 2, "GITHUB_ISSUES", "principal", "Backend " + horas, null, List.of(), limite.plusMillis(1));
			gravar(horas + 3, "GITHUB_ISSUES", "principal", "Backend " + horas, null, List.of(), AGORA);
			gravar(horas + 4, "GITHUB_ISSUES", "principal", "Backend " + horas, null, List.of(), AGORA.plusMillis(1));
			gravar(horas + 5, "GITHUB_ISSUES", "principal", "Backend " + horas, null, List.of(), null);
			String modo = switch (horas) {
				case 24 -> "ULTIMAS_24H";
				case 72 -> "ULTIMOS_3_DIAS";
				default -> "ULTIMOS_7_DIAS";
			};
			api.perform(get("/api/vagas").param("publicacao", modo).param("q", "Backend " + horas))
					.andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(3))
					.andExpect(jsonPath("$.itens[0].id").value(id(horas + 3)))
					.andExpect(jsonPath("$.itens[1].id").value(id(horas + 2)))
					.andExpect(jsonPath("$.itens[2].id").value(id(horas)));
		}
		api.perform(get("/api/vagas").param("publicacao", "DESCONHECIDA"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(3))
				.andExpect(jsonPath("$.itens[0].publicacaoConhecida").value(false));
	}

	@Test
	void tecnologiasSaoTokensLiteraisInclusivePontuacao() throws Exception {
		gravar(1, "GITHUB_ISSUES", "principal", "Backend", null, List.of("Java", "C++", "C#", ".NET"), AGORA);
		gravar(2, "GITHUB_ISSUES", "principal", "Backend", null, List.of("JavaScript", "C++17", "C#8", "XNET"), AGORA);
		for (String token : List.of("Java", "C++", "C#", ".NET")) {
			api.perform(get("/api/vagas").param("tecnologia", token)).andExpect(status().isOk())
					.andExpect(jsonPath("$.totalElementos").value(1)).andExpect(jsonPath("$.itens[0].id").value(id(1)));
		}
		api.perform(get("/api/vagas").param("tecnologia", "%")).andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElementos").value(0));
	}

	@Test
	void acoesReversiveisMantemDatasIndependentesEConsultasCoerentes() throws Exception {
		gravar(1, "GITHUB_ISSUES", "principal", "Backend", null, List.of(), null);
		gravar(2, "GITHUB_ISSUES", "principal", "Backend", null, List.of(), null);
		long id = id(1);
		api.perform(get("/api/vagas/{id}", id)).andExpect(jsonPath("$.jaMeCandidatei").value(false));
		contar("NAO_CANDIDATADAS", "EXCLUIR", 2);
		contar("TODAS", "SOMENTE", 0);
		assertThat(acoes.count()).isZero();
		alterar(id, "{\"jaMeCandidatei\":true}").andExpect(jsonPath("$.candidatadoEm").value(AGORA.toString()));
		relogio.agora = AGORA.plusSeconds(1);
		alterar(id, "{\"jaMeCandidatei\":true}").andExpect(jsonPath("$.atualizadoEm").value(AGORA.toString()));
		assertThat(acoes.count()).isEqualTo(1);
		alterar(id, "{\"oculta\":true}").andExpect(jsonPath("$.jaMeCandidatei").value(true))
				.andExpect(jsonPath("$.ocultadoEm").value(relogio.agora.toString()));
		contar("CANDIDATADAS", "EXCLUIR", 0);
		contar("CANDIDATADAS", "INCLUIR", 1);
		contar("CANDIDATADAS", "SOMENTE", 1);
		contar("NAO_CANDIDATADAS", "INCLUIR", 1);
		alterar(id, "{\"jaMeCandidatei\":false}").andExpect(jsonPath("$.candidatadoEm").isEmpty())
				.andExpect(jsonPath("$.oculta").value(true));
		relogio.agora = AGORA.plusSeconds(2);
		alterar(id, "{\"jaMeCandidatei\":true}").andExpect(jsonPath("$.candidatadoEm").value(relogio.agora.toString()));
		alterar(id, "{\"oculta\":false}").andExpect(jsonPath("$.ocultadoEm").isEmpty());
		gravar(1, "GITHUB_ISSUES", "principal", "Backend atualizado", null, List.of(), null);
		gravar(1, "ADZUNA_API", "secundaria", "Backend atualizado", null, List.of(), null);
		api.perform(get("/api/vagas/{id}", id)).andExpect(status().isOk())
				.andExpect(jsonPath("$.cargo").value("Backend atualizado"))
				.andExpect(jsonPath("$.jaMeCandidatei").value(true)).andExpect(jsonPath("$.oculta").value(false))
				.andExpect(jsonPath("$.anunciosOrigem.length()").value(2));
		contar("CANDIDATADAS", "EXCLUIR", 1);
	}

	@Test
	void primeirasMarcacoesConcorrentesConvergemEmUmaLinha() throws Exception {
		gravar(1, "GITHUB_ISSUES", "principal", "Backend", null, List.of(), null);
		long id = id(1);
		var inicio = new CountDownLatch(1);
		try (var executor = Executors.newFixedThreadPool(2)) {
			var candidatura = executor.submit(() -> { inicio.await(); return alterar(id, "{\"jaMeCandidatei\":true}"); });
			var ocultacao = executor.submit(() -> { inicio.await(); return alterar(id, "{\"oculta\":true}"); });
			inicio.countDown();
			candidatura.get();
			ocultacao.get();
		}
		assertThat(acoes.count()).isEqualTo(1);
		contar("CANDIDATADAS", "SOMENTE", 1);
	}

	@Test
	void errosDeEntradaEDeIdSaoLegiveisESemEfeitos() throws Exception {
		gravar(1, "GITHUB_ISSUES", "principal", "Backend", null, List.of(), null);
		for (String campo : List.of("publicacao", "area", "modalidade", "senioridade", "candidatura", "ocultacao", "size")) {
			api.perform(get("/api/vagas").param(campo, "Invalido")).andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.detail").isNotEmpty()).andExpect(jsonPath("$.trace").doesNotExist());
		}
		for (String corpo : List.of("", "{}", "[]", "null", "{", "{\"oculta\":null}", "{\"oculta\":\"true\"}",
				"{\"jaMeCandidatei\":1}", "{\"extra\":false}")) {
			api.perform(patch("/api/vagas/{id}/acao-pessoal", id(1)).contentType("application/json").content(corpo))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").isNotEmpty());
		}
		api.perform(patch("/api/vagas/{id}/acao-pessoal", Long.MAX_VALUE).contentType("application/json")
				.content("{\"oculta\":true}")).andExpect(status().isNotFound());
		assertThat(acoes.count()).isZero();
	}

	private void contar(String candidatura, String ocultacao, int total) throws Exception {
		api.perform(get("/api/vagas").param("candidatura", candidatura).param("ocultacao", ocultacao))
				.andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(total));
	}

	private ResultActions alterar(long id, String corpo) throws Exception {
		return api.perform(patch("/api/vagas/{id}/acao-pessoal", id).contentType("application/json").content(corpo))
				.andExpect(status().isOk());
	}

	private long id(int n) {
		return vagas.findByChaveDeduplicacao("LINK_CAND:https://example.org/vagas/" + n).orElseThrow().getId();
	}

	private void gravar(int n, String fonte, String mural, String cargo, String local, List<String> tech, Instant data) {
		catalogo.registrarOuAtualizarAnuncio(new AnuncioExtraido(fonte, mural, "" + n, cargo, cargo, List.of(),
				cargo, "Empresa Teste", "Descrição", AreaTecnologia.DESENVOLVIMENTO, Senioridade.JUNIOR,
				TipoOportunidade.VAGA_ABERTA, RegimeContratacao.CLT, ModalidadeTrabalho.REMOTO,
				local, true, tech, DisponibilidadeVaga.DISPONIVEL, null, "https://example.org/" + mural + "/" + n,
				"https://example.org/vagas/" + n, data, AGORA));
	}

	@TestConfiguration
	static class Configuracao {
		@Bean @Primary Relogio relogioCatalogo() { return new Relogio(); }
	}

	static final class Relogio extends Clock {
		volatile Instant agora = AGORA;
		@Override public Instant instant() { return agora; }
		@Override public ZoneId getZone() { return ZoneOffset.UTC; }
		@Override public Clock withZone(ZoneId zona) { return Clock.fixed(instant(), zona); }
	}
}

package br.com.radarvagas.coleta;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.radarvagas.catalogo.DisponibilidadeVaga;
import br.com.radarvagas.catalogo.ModalidadeTrabalho;
import br.com.radarvagas.catalogo.RegimeContratacao;
import br.com.radarvagas.catalogo.SanitizadorConteudo;
import br.com.radarvagas.catalogo.Senioridade;
import br.com.radarvagas.catalogo.TipoOportunidade;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GitHubClassificadorESegurancaTest {

	private final GitHubIssueClassificador classificador = new GitHubIssueClassificador();

	@Test
	void deveIgnorarPullRequestsRegrasDoRepositorioEPedidosDeEmprego() {
		var pr = new GitHubIssueClassificador.DadosBrutosIssue(
				"backend-br/vagas", "10", "[Remoto] Dev Java @ Empresa", "Corpo", "open",
				true, List.of(), "https://github.com/backend-br/vagas/pull/10",
				Instant.parse("2026-11-10T10:00:00Z"), Instant.parse("2026-11-10T10:00:00Z")
		);
		assertThat(classificador.classificar(pr)).isEmpty();

		var regras = new GitHubIssueClassificador.DadosBrutosIssue(
				"backend-br/vagas", "1", "Leia antes de postar - Regras do repositório", "Texto", "open",
				false, List.of(), "https://github.com/backend-br/vagas/issues/1",
				Instant.parse("2026-11-10T10:00:00Z"), Instant.parse("2026-11-10T10:00:00Z")
		);
		assertThat(classificador.classificar(regras)).isEmpty();

		var pedidoEmprego = new GitHubIssueClassificador.DadosBrutosIssue(
				"backend-br/vagas", "2", "[Currículo] Procuro oportunidade como Dev React", "Busco vaga jr", "open",
				false, List.of(), "https://github.com/backend-br/vagas/issues/2",
				Instant.parse("2026-11-10T10:00:00Z"), Instant.parse("2026-11-10T10:00:00Z")
		);
		assertThat(classificador.classificar(pedidoEmprego)).isEmpty();
	}

	@Test
	void deveDistinguirJavaDeJavaScriptESanitizarHtmlELinksInternos() {
		var issue = new GitHubIssueClassificador.DadosBrutosIssue(
				"frontendbr/vagas",
				"205",
				"[Remoto] Desenvolvedor Front-end JavaScript Pleno @ Studio Web",
				"Trabalhar com JavaScript, TypeScript e Angular.\n<script>fetch('http://169.254.169.254')</script>\n"
						+ "Link interno malicioso: http://127.0.0.1:8080/admin\n"
						+ "Link real: **https://vagas.studioweb.com.br/apply?jobId=555&utm_source=github&utm_campaign=tech**",
				"open",
				false,
				List.of("Remoto", "Pleno", "PJ"),
				"https://github.com/frontendbr/vagas/issues/205",
				Instant.parse("2026-11-14T12:00:00Z"),
				Instant.parse("2026-11-14T13:00:00Z")
		);

		Optional<AnuncioExtraido> resultado = classificador.classificar(issue);
		assertThat(resultado).isPresent();
		AnuncioExtraido anuncio = resultado.get();

		assertThat(anuncio.cargo()).isEqualTo("Desenvolvedor Front-end JavaScript Pleno");
		assertThat(anuncio.nomeEmpresa()).isEqualTo("Studio Web");
		assertThat(anuncio.modalidade()).isEqualTo(ModalidadeTrabalho.REMOTO);
		assertThat(anuncio.senioridade()).isEqualTo(Senioridade.PLENO);
		assertThat(anuncio.regimeContratacao()).isEqualTo(RegimeContratacao.PJ);
		assertThat(anuncio.tipoOportunidade()).isEqualTo(TipoOportunidade.VAGA_ABERTA);

		// Não deve confundir JavaScript com Java!
		assertThat(anuncio.tecnologias()).contains("JavaScript", "TypeScript", "Angular");
		assertThat(anuncio.tecnologias()).doesNotContain("Java");

		// Descrição sem <script>
		assertThat(anuncio.descricaoLimpa()).doesNotContain("<script>");

		// Link de candidatura deve rejeitar 127.0.0.1, remover utm_* e preservar jobId=555
		assertThat(anuncio.urlCandidatura())
				.isEqualTo("https://vagas.studioweb.com.br/apply?jobId=555");
	}

	@Test
	void deveTratarFechamentoDeIssueComoIndisponivelNaOrigemSemAssumirContratacao() {
		var issueFechada = new GitHubIssueClassificador.DadosBrutosIssue(
				"backend-br/vagas",
				"300",
				"[Presencial] Pessoa Desenvolvedora Python Júnior",
				"Atuação com Python em Curitiba/PR.",
				"closed",
				false,
				List.of("Presencial", "Júnior"),
				"https://github.com/backend-br/vagas/issues/300",
				null,
				Instant.parse("2026-11-16T18:00:00Z")
		);

		AnuncioExtraido anuncio = classificador.classificar(issueFechada).orElseThrow();
		assertThat(anuncio.disponibilidadeObservada()).isEqualTo(DisponibilidadeVaga.INDISPONIVEL_NA_ORIGEM);
		assertThat(anuncio.motivoIndisponibilidade()).contains("moderação");
		// Data de publicação desconhecida permanece nula (nunca substituída por updatedAt nem hoje)
		assertThat(anuncio.publicadoNaFonteEm()).isNull();
		// Empresa não identificada permanece nula
		assertThat(anuncio.nomeEmpresa()).isNull();
	}

	@Test
	void deveOcultarSegredosEmMensagensDeErroERejeitarEnderecosPrivados() {
		String msgSujo = "Falha ao autenticar com header Authorization: Bearer ghp_SegredoSuperConfidencial12345";
		String msgLimpa = SanitizadorConteudo.sanitizarMensagemErro(msgSujo, "ghp_SegredoSuperConfidencial12345");
		assertThat(msgLimpa).doesNotContain("ghp_SegredoSuperConfidencial12345");
		assertThat(msgLimpa).contains("[REDACTED]");

		assertThat(SanitizadorConteudo.validarENormalizarUrlPublica("http://localhost:8080/vaga")).isNull();
		assertThat(SanitizadorConteudo.validarENormalizarUrlPublica("http://192.168.1.50/vaga")).isNull();
		assertThat(SanitizadorConteudo.validarENormalizarUrlPublica("http://10.0.0.1/vaga")).isNull();
		assertThat(SanitizadorConteudo.validarENormalizarUrlPublica("http://172.20.0.5/vaga")).isNull();
		assertThat(SanitizadorConteudo.validarENormalizarUrlPublica("javascript:alert(1)")).isNull();
	}
}

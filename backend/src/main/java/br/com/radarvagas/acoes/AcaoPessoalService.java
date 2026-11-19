package br.com.radarvagas.acoes;

import br.com.radarvagas.catalogo.OportunidadeRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AcaoPessoalService {
	private final OportunidadeRepository oportunidades;
	private final AcaoPessoalRepository acoes;
	private final Clock clock;

	public AcaoPessoalService(OportunidadeRepository oportunidades, AcaoPessoalRepository acoes, Clock clock) {
		this.oportunidades = oportunidades;
		this.acoes = acoes;
		this.clock = clock;
	}

	@Transactional
	public Estado atualizar(Long id, Boolean candidatura, Boolean ocultacao) {
		// Serializa também duas primeiras marcações concorrentes, quando ainda não há ação para bloquear.
		var vaga = oportunidades.bloquearPorId(id).orElseThrow(() ->
				new ResponseStatusException(HttpStatus.NOT_FOUND, "Oportunidade não encontrada."));
		var acao = acoes.findByOportunidadeId(id).orElseGet(() -> {
			var nova = new AcaoPessoal();
			nova.setOportunidade(vaga);
			return nova;
		});
		boolean mudouCandidatura = candidatura != null && candidatura != acao.isJaMeCandidatei();
		boolean mudouOcultacao = ocultacao != null && ocultacao != acao.isOculta();
		if (mudouCandidatura || mudouOcultacao) {
			// Ambos os bancos armazenam timestamps com precisão de microssegundos.
			Instant agora = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
			if (mudouCandidatura) {
				acao.setJaMeCandidatei(candidatura);
				acao.setCandidatadoEm(candidatura ? agora : null);
			}
			if (mudouOcultacao) {
				acao.setOculta(ocultacao);
				acao.setOcultadoEm(ocultacao ? agora : null);
			}
			acao.setAtualizadoEm(agora);
			acoes.save(acao);
		}
		return new Estado(acao.isJaMeCandidatei(), acao.isOculta(), acao.getCandidatadoEm(),
				acao.getOcultadoEm(), acao.getAtualizadoEm());
	}

	public record Estado(boolean jaMeCandidatei, boolean oculta, Instant candidatadoEm, Instant ocultadoEm,
			Instant atualizadoEm) {}
}

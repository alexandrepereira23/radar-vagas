package br.com.radarvagas.catalogo;

import java.time.Instant;
import java.util.List;

public record VagaResumoDto(
		Long id,
		String cargo,
		String empresa,
		String localizacao,
		String modalidadeCodigo,
		String modalidadeRotulo,
		String senioridadeCodigo,
		String senioridadeRotulo,
		String regimeCodigo,
		String regimeRotulo,
		String areaCodigo,
		String areaRotulo,
		String tipoOportunidadeCodigo,
		String tipoOportunidadeRotulo,
		String disponibilidadeCodigo,
		String disponibilidadeRotulo,
		String motivoDisponibilidade,
		List<String> tecnologias,
		Instant publicadoEm,
		boolean publicacaoConhecida,
		Instant primeiraObservacaoEm,
		Instant ultimaObservacaoEm,
		Instant atualizadoNaFonteEm,
		String fontePrincipal,
		String muralPrincipal,
		String identificadorExternoPrincipal,
		String linkCandidatura,
		String linkOrigemPrincipal,
		boolean jaMeCandidatei,
		boolean oculta,
		int quantidadeAnunciosOrigem
) {
}

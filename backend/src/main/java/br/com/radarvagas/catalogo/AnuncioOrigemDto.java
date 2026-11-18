package br.com.radarvagas.catalogo;

import java.time.Instant;
import java.util.List;

public record AnuncioOrigemDto(
		Long id,
		String fornecedor,
		String identificadorMural,
		String identificadorExterno,
		String tituloOriginal,
		List<String> labelsOriginais,
		String urlOrigem,
		String urlCandidatura,
		String disponibilidadeCodigo,
		String disponibilidadeRotulo,
		String motivoIndisponibilidade,
		Instant publicadoNaFonteEm,
		Instant atualizadoNaFonteEm,
		Instant primeiraObservacaoEm,
		Instant ultimaObservacaoEm
) {
}

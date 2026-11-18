package br.com.radarvagas.coleta;

import java.time.Instant;

public record ExecucaoColetaDto(
		Long id,
		String fornecedor,
		String identificadorMural,
		String statusCodigo,
		String statusRotulo,
		Instant iniciadoEm,
		Instant finalizadoEm,
		int anunciosRecebidos,
		int anunciosValidos,
		int anunciosNovos,
		int anunciosAtualizados,
		int anunciosRepetidos,
		int anunciosIgnorados,
		int errosContagem,
		Integer rateLimitRestante,
		Instant rateLimitResetEm,
		String erroResumido
) {
}

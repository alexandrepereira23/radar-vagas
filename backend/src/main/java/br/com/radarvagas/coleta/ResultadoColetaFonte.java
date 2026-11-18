package br.com.radarvagas.coleta;

import java.time.Instant;
import java.util.List;

public record ResultadoColetaFonte(
		StatusExecucaoColeta status,
		List<AnuncioExtraido> anunciosValidos,
		int anunciosRecebidos,
		int anunciosIgnorados,
		int errosContagem,
		Integer rateLimitRestante,
		Instant rateLimitResetEm,
		Instant maiorAtualizacaoObservada,
		String erroResumido,
		boolean coletaIncompleta,
		Integer proximaPaginaContinuacao,
		Instant cursorJanelaInicio,
		boolean falhaAutenticacao
) {

	public ResultadoColetaFonte(
			StatusExecucaoColeta status,
			List<AnuncioExtraido> anunciosValidos,
			int anunciosRecebidos,
			int anunciosIgnorados,
			int errosContagem,
			Integer rateLimitRestante,
			Instant rateLimitResetEm,
			Instant maiorAtualizacaoObservada,
			String erroResumido
	) {
		this(
				status,
				anunciosValidos,
				anunciosRecebidos,
				anunciosIgnorados,
				errosContagem,
				rateLimitRestante,
				rateLimitResetEm,
				maiorAtualizacaoObservada,
				erroResumido,
				false,
				null,
				null,
				false
		);
	}
}

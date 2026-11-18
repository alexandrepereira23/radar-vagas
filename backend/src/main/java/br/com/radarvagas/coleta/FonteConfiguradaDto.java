package br.com.radarvagas.coleta;

import java.time.Instant;

public record FonteConfiguradaDto(
		Long id,
		String fornecedor,
		String identificadorMural,
		String nomeExibicao,
		boolean ativo,
		int cadenciaMinutos,
		String categoria,
		String estadoValidacao,
		String metodoAcesso,
		String credencialNecessaria,
		String observacaoValidacao,
		Instant ultimaColetaSucessoEm,
		Instant cursorUltimaAtualizacao
) {
}

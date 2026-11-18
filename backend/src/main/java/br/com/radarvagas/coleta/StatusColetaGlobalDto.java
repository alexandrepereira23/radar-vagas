package br.com.radarvagas.coleta;

import java.time.Instant;
import java.util.List;

public record StatusColetaGlobalDto(
		Instant ultimaColetaSucessoGlobalEm,
		boolean dadosDesatualizados,
		String motivoEstadoDados,
		boolean coletaEmAndamento,
		List<FonteConfiguradaDto> fontes,
		List<ExecucaoColetaDto> ultimasExecucoes
) {
}

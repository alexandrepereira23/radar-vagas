package br.com.radarvagas.coleta;

import br.com.radarvagas.config.RadarProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AgendadorColeta {

	private final RadarProperties properties;
	private final OrquestradorColetaService orquestradorColetaService;

	public AgendadorColeta(
			RadarProperties properties,
			OrquestradorColetaService orquestradorColetaService
	) {
		this.properties = properties;
		this.orquestradorColetaService = orquestradorColetaService;
	}

	@Scheduled(
			initialDelayString = "${radar.coleta.intervalo-verificacao-ms:300000}",
			fixedDelayString = "${radar.coleta.intervalo-verificacao-ms:300000}"
	)
	public void verificarFontesVencidas() {
		if (!properties.getColeta().isAgendamentoAtivo()) {
			return;
		}
		orquestradorColetaService.executarFontesVencidasPelaCadencia();
	}
}

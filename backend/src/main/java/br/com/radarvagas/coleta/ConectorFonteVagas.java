package br.com.radarvagas.coleta;

/**
 * Contrato enxuto para conectores de fontes de vagas.
 * Cada conector conhece o protocolo da sua fonte e devolve anúncios extraídos + diagnóstico da coleta.
 */
public interface ConectorFonteVagas {

	String fornecedor();

	ResultadoColetaFonte coletar(ConfiguracaoFonte configuracao);
}

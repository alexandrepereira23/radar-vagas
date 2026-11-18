package br.com.radarvagas.catalogo;

public enum RegimeContratacao {
	CLT("CLT"),
	PJ("PJ"),
	ESTAGIO("Estágio"),
	COOPERADO("Cooperado"),
	NAO_INFORMADO("Não informado");

	private final String rotulo;

	RegimeContratacao(String rotulo) {
		this.rotulo = rotulo;
	}

	public String getRotulo() {
		return rotulo;
	}
}

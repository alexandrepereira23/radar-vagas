package br.com.radarvagas.catalogo;

public enum TipoOportunidade {
	VAGA_ABERTA("Vaga aberta"),
	BANCO_DE_TALENTOS("Banco de talentos");

	private final String rotulo;

	TipoOportunidade(String rotulo) {
		this.rotulo = rotulo;
	}

	public String getRotulo() {
		return rotulo;
	}
}

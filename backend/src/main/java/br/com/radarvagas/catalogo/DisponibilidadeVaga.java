package br.com.radarvagas.catalogo;

public enum DisponibilidadeVaga {
	DISPONIVEL("Disponível"),
	INDISPONIVEL_NA_ORIGEM("Indisponível na origem"),
	A_VERIFICAR("A verificar"),
	ENCERRADA("Encerrada");

	private final String rotulo;

	DisponibilidadeVaga(String rotulo) {
		this.rotulo = rotulo;
	}

	public String getRotulo() {
		return rotulo;
	}
}

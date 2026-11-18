package br.com.radarvagas.catalogo;

public enum ModalidadeTrabalho {
	REMOTO("Remoto"),
	HIBRIDO("Híbrido"),
	PRESENCIAL("Presencial"),
	NAO_INFORMADA("Não informada");

	private final String rotulo;

	ModalidadeTrabalho(String rotulo) {
		this.rotulo = rotulo;
	}

	public String getRotulo() {
		return rotulo;
	}
}

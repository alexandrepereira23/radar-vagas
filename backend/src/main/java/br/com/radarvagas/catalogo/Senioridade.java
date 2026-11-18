package br.com.radarvagas.catalogo;

public enum Senioridade {
	ESTAGIO("Estágio"),
	TRAINEE("Trainee"),
	JUNIOR("Júnior"),
	PLENO("Pleno"),
	SENIOR("Sênior"),
	ESPECIALISTA("Especialista / Staff"),
	LIDERANCA("Liderança Técnica"),
	NAO_INFORMADA("Não informada");

	private final String rotulo;

	Senioridade(String rotulo) {
		this.rotulo = rotulo;
	}

	public String getRotulo() {
		return rotulo;
	}
}

package br.com.radarvagas.coleta;

public enum StatusExecucaoColeta {
	EM_ANDAMENTO("Em andamento"),
	SUCESSO("Sucesso"),
	SUCESSO_PARCIAL("Sucesso parcial"),
	FALHA("Falha"),
	LIMITE_ATINGIDO("Limite de requisições atingido"),
	BLOQUEADA_CREDENCIAL("Bloqueada (credencial ausente)"),
	BLOQUEADA_ACESSO("Bloqueada (restrição de acesso/termos)"),
	NAO_IMPLEMENTADA("Conector não implementado"),
	IGNORADA_SOBREPOSICAO("Ignorada (execução já em andamento)");

	private final String rotulo;

	StatusExecucaoColeta(String rotulo) {
		this.rotulo = rotulo;
	}

	public String getRotulo() {
		return rotulo;
	}
}

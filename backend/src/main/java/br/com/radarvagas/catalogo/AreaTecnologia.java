package br.com.radarvagas.catalogo;

public enum AreaTecnologia {
	DESENVOLVIMENTO("Desenvolvimento"),
	QA("Qualidade e Testes (QA)"),
	DADOS("Dados e Analytics"),
	IA("Inteligência Artificial"),
	SEGURANCA("Segurança da Informação"),
	INFRAESTRUTURA("Infraestrutura e DevOps"),
	CLOUD("Cloud Computing"),
	REDES("Redes"),
	SUPORTE("Suporte Técnico"),
	PRODUTO_DESIGN("Produto e Design"),
	NAO_INFORMADA("Não informada");

	private final String rotulo;

	AreaTecnologia(String rotulo) {
		this.rotulo = rotulo;
	}

	public String getRotulo() {
		return rotulo;
	}
}

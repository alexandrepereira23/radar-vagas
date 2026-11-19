package br.com.radarvagas.catalogo;

import java.util.Arrays;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

record FiltrosCatalogo(
		String termo, String fonte, Publicacao publicacao, boolean incluirIndisponiveis, boolean bancoTalentos,
		AreaTecnologia area, Senioridade senioridade, ModalidadeTrabalho modalidade, String tecnologia,
		String localizacao, Candidatura candidatura, Ocultacao ocultacao) {

	enum Publicacao {
		TODAS(0), CONHECIDA(0), DESCONHECIDA(0), ULTIMAS_24H(24), ULTIMOS_3_DIAS(72), ULTIMOS_7_DIAS(168);

		final int horas;

		Publicacao(int horas) {
			this.horas = horas;
		}
	}

	enum Candidatura { TODAS, NAO_CANDIDATADAS, CANDIDATADAS }
	enum Ocultacao { EXCLUIR, INCLUIR, SOMENTE }

	static FiltrosCatalogo de(String termo, String fonte, String publicacao, boolean incluirIndisponiveis,
			boolean bancoTalentos, String area, String senioridade, String modalidade, String tecnologia,
			String localizacao, String candidatura, String ocultacao) {
		String token = limpar(tecnologia);
		if (token != null && token.contains(",")) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe uma única tecnologia, sem vírgulas.");
		}
		return new FiltrosCatalogo(limpar(termo), "TODAS".equalsIgnoreCase(limpar(fonte)) ? null : limpar(fonte),
				codigo("publicacao", publicacao, Publicacao.class, Publicacao.TODAS), incluirIndisponiveis, bancoTalentos,
				codigo("area", area, AreaTecnologia.class, null), codigo("senioridade", senioridade, Senioridade.class, null),
				codigo("modalidade", modalidade, ModalidadeTrabalho.class, null), token, limpar(localizacao),
				codigo("candidatura", candidatura, Candidatura.class, Candidatura.TODAS),
				codigo("ocultacao", ocultacao, Ocultacao.class, Ocultacao.EXCLUIR));
	}

	private static String limpar(String valor) {
		return valor == null || valor.isBlank() ? null : valor.strip();
	}

	private static <E extends Enum<E>> E codigo(String campo, String valor, Class<E> tipo, E padrao) {
		String limpo = limpar(valor);
		if (limpo == null) {
			return padrao;
		}
		try {
			return Enum.valueOf(tipo, limpo.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Valor inválido para " + campo + ". Use: " + Arrays.toString(tipo.getEnumConstants()));
		}
	}
}

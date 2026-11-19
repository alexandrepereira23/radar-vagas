package br.com.radarvagas.catalogo;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

public record OpcoesCatalogoDto(List<Opcao> areas, List<Opcao> senioridades, List<Opcao> modalidades,
		List<String> tecnologias, List<String> fontes, boolean catalogoVazio) {

	public record Opcao(String codigo, String rotulo) {}

	static <E extends Enum<E>> List<Opcao> opcoes(E[] valores, Function<E, String> rotulo) {
		return Arrays.stream(valores).map(v -> new Opcao(v.name(), rotulo.apply(v))).toList();
	}
}

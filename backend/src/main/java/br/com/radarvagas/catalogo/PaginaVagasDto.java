package br.com.radarvagas.catalogo;

import java.util.List;

public record PaginaVagasDto(
		List<VagaResumoDto> itens,
		int pagina,
		int tamanhoPagina,
		long totalElementos,
		int totalPaginas,
		boolean temProxima,
		boolean temAnterior
) {
}

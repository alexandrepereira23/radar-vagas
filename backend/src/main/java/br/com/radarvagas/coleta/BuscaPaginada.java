package br.com.radarvagas.coleta;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Estado transitório de uma busca: guarda somente a página ainda não consumida, nunca credenciais. */
final class BuscaPaginada {
	public record Estado(List<String> termos, int termo, int pagina, int tamanhoPagina,
			Instant inicio, List<JsonNode> pendentes, boolean proxima, boolean limitada) {}
	public record Pagina(List<JsonNode> itens, boolean proxima, boolean limitada) {}
	@FunctionalInterface
	interface Consulta { Pagina buscar(String termo, int pagina, int tamanho, Instant inicio) throws Exception; }
	static final class Falha extends RuntimeException {
		final int http;
		Falha(int http, String mensagem) { super(mensagem); this.http = http; }
	}

	static Pagina pagina(JsonNode raiz, String campo, String campoTotal, int pagina, int tamanho) {
		JsonNode array = raiz == null ? null : raiz.get(campo);
		if (array == null || !array.isArray()) {
			throw new Falha(0, "Payload inválido (campo '" + campo + "' ausente ou não é uma lista JSON).");
		}
		List<JsonNode> itens = new ArrayList<>();
		array.forEach(itens::add);
		JsonNode total = raiz.get(campoTotal);
		boolean temTotal = total != null && total.isNumber() && total.asLong() >= 0;
		// Resposta maior que o tamanho pedido: não presumir o deslocamento solicitado.
		boolean tamanhoInesperado = itens.size() > tamanho;
		long deslocamento = (long) (pagina - 1) * tamanho;
		boolean contagemInconsistente = temTotal && (itens.isEmpty() ? total.asLong() > deslocamento
				: total.asLong() < deslocamento + itens.size());
		boolean proxima = !itens.isEmpty() && (temTotal
				? (long) (pagina - 1) * tamanho + itens.size() < total.asLong()
				: itens.size() >= tamanho);
		return new Pagina(itens, proxima, !temTotal || tamanhoInesperado || contagemInconsistente);
	}

	static ResultadoColetaFonte coletar(ConfiguracaoFonte cfg, ObjectMapper mapper, List<String> termos,
			int maxItens, int maxTermos, int maxConsultas, int maxPaginasTermo, int tamanhoPagina,
			Consulta consulta, Function<JsonNode, Optional<AnuncioExtraido>> classificar,
			String limitacao) {
		Estado estado;
		try {
			estado = cfg.getEstadoBusca() == null ? null : mapper.readValue(cfg.getEstadoBusca(), Estado.class);
			if (estado != null && (estado.termos() == null || estado.termos().isEmpty()
					|| estado.termo() < 0 || estado.termo() >= estado.termos().size() || estado.pagina() < 1
					|| estado.tamanhoPagina() < 1 || estado.inicio() == null || estado.pendentes() == null)) {
				throw new IllegalArgumentException("Estado inválido");
			}
		} catch (Exception ex) {
			// Não reiniciar silenciosamente um estado ilegível e perder sua página pendente.
			return new ResultadoColetaFonte(StatusExecucaoColeta.FALHA, List.of(), 0, 0, 1,
					null, null, cfg.getCursorUltimaAtualizacao(), "Estado de continuação inválido; busca preservada.",
					true, cfg.getPaginaContinuacao(), cfg.getCursorJanelaAtual(), false, cfg.getEstadoBusca());
		}
		if (estado == null) {
			// Compatibilidade com V3: pagina_continuacao representava o índice do termo.
			int termo = cfg.getPaginaContinuacao() == null ? 0 : cfg.getPaginaContinuacao() - 1;
			if (termo < 0 || termo >= termos.size()) termo = 0;
			estado = new Estado(List.copyOf(termos), termo, 1, Math.max(1, tamanhoPagina),
					Instant.now(), List.of(), false, false);
		}
		termos = estado.termos(); // Mudança de configuração não invalida uma resposta já recebida.
		int termo = estado.termo(), pagina = estado.pagina(), consultas = 0, termosVisitados = 0;
		int recebidos = 0, ignorados = 0, erros = 0;
		boolean proxima = estado.proxima(), limitada = estado.limitada();
		List<JsonNode> pendentes = new ArrayList<>(estado.pendentes());
		var validos = new LinkedHashMap<String, AnuncioExtraido>();
		String erro = null;
		int http = 0;
		while (termo < termos.size()) {
			if (validos.size() >= Math.max(1, maxItens)) break;
			if (pendentes.isEmpty()) {
				if (consultas >= Math.max(1, maxConsultas) || termosVisitados >= Math.max(1, maxTermos)) break;
				try {
					Pagina resposta = consulta.buscar(termos.get(termo), pagina, estado.tamanhoPagina(), estado.inicio());
					consultas++;
					pendentes.addAll(resposta.itens());
					recebidos += pendentes.size();
					proxima = resposta.proxima();
					limitada |= resposta.limitada();
				} catch (Exception ex) {
					erros++;
					http = ex instanceof Falha falha ? falha.http : 0;
					// Exceções de transporte podem conter a URL com credenciais. Não persistir a mensagem bruta.
					erro = ex instanceof Falha ? ex.getMessage() : "Falha de transporte/parsing ao consultar fonte.";
					break;
				}
			}
			int consumidos = 0;
			try {
				for (JsonNode item : pendentes) {
					if (validos.size() >= Math.max(1, maxItens)) break;
					Optional<AnuncioExtraido> anuncio = classificar.apply(item);
					if (anuncio.isPresent()) validos.putIfAbsent(anuncio.get().identificadorExterno(), anuncio.get());
					else ignorados++;
					consumidos++;
				}
			} catch (Exception ex) {
				erros++;
				erro = "Falha ao interpretar anúncio; item preservado para retomada.";
			}
			pendentes = new ArrayList<>(pendentes.subList(consumidos, pendentes.size()));
			if (erros > 0 || !pendentes.isEmpty()) break;
			if (proxima && pagina < Math.max(1, maxPaginasTermo)) {
				pagina++;
			} else {
				limitada |= proxima; // Teto de profundidade é recorte, não conclusão integral.
				termo++;
				pagina = 1;
				termosVisitados++;
			}
		}
		boolean temEstado = termo < termos.size();
		boolean incompleta = temEstado || limitada;
		String json = temEstado ? mapper.writeValueAsString(new Estado(termos, termo, pagina,
				estado.tamanhoPagina(), estado.inicio(), pendentes, proxima, limitada)) : null;
		if (erro == null && incompleta) erro = temEstado
				? "Recorte por orçamento: continuação no termo " + (termo + 1) + ", página " + pagina
						+ ", " + pendentes.size() + " item(ns) adiado(s)."
				: limitacao;
		StatusExecucaoColeta status = erros > 0 && validos.isEmpty()
				? (http == 429 || http == 423 ? StatusExecucaoColeta.LIMITE_ATINGIDO : StatusExecucaoColeta.FALHA)
				: incompleta || erros > 0 ? StatusExecucaoColeta.SUCESSO_PARCIAL : StatusExecucaoColeta.SUCESSO;
		Instant maiorPublicacao = validos.values().stream().map(AnuncioExtraido::publicadoNaFonteEm)
				.filter(data -> data != null).max(Instant::compareTo).orElse(cfg.getCursorUltimaAtualizacao());
		return new ResultadoColetaFonte(status, new ArrayList<>(validos.values()), recebidos, ignorados,
				erros, null, null, maiorPublicacao, erro, incompleta,
				temEstado ? pagina : null, estado.inicio(), http == 401 || http == 403, json);
	}

	static List<String> termos(List<String> configurados, String padrao) {
		List<String> termos = configurados == null ? List.of() : configurados.stream()
				.filter(s -> s != null && !s.isBlank()).map(String::trim).distinct().toList();
		return termos.isEmpty() ? List.of(padrao) : termos;
	}

	static String texto(JsonNode item, String campo) {
		JsonNode valor = item == null ? null : item.get(campo);
		return valor == null || valor.isNull() || valor.asText().isBlank() ? null : valor.asText();
	}
}

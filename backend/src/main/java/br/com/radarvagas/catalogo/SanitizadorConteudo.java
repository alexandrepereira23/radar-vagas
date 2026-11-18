package br.com.radarvagas.catalogo;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class SanitizadorConteudo {

	private static final Set<String> PARAMETROS_RASTREAMENTO = Set.of(
			"utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
			"utm_id", "fbclid", "gclid", "mc_cid", "mc_eid", "igshid",
			"jobboardsource", "gh_src", "src"
	);

	private static final Pattern TAGS_PERIGOSAS = Pattern.compile(
			"(?is)<\\s*(script|iframe|object|embed|style|form|input|button|meta|link)[^>]*>.*?<\\s*/\\s*\\1\\s*>|<\\s*(script|iframe|object|embed|style|form|input|button|meta|link)[^>]*/?\\s*>"
	);

	private static final Pattern QUALQUER_TAG_HTML = Pattern.compile("(?is)<[^>]+>");

	private SanitizadorConteudo() {
	}

	/**
	 * Sanitiza texto/markdown externo removendo tags HTML e blocos de script/iframe,
	 * preservando quebras de linha e texto legível.
	 */
	public static String sanitizarTexto(String textoBruto) {
		if (textoBruto == null || textoBruto.isBlank()) {
			return "";
		}
		String decodificado = textoBruto
				.replace("&lt;", "<")
				.replace("&gt;", ">")
				.replace("&nbsp;", " ")
				.replace("&amp;", "&")
				.replace("&quot;", "\"")
				.replace("&#39;", "'")
				.replace("&ndash;", "-")
				.replace("&mdash;", "-");
		String semBlocosPerigosos = TAGS_PERIGOSAS.matcher(decodificado).replaceAll("");
		String semHtml = QUALQUER_TAG_HTML.matcher(semBlocosPerigosos).replaceAll("");
		return semHtml
				.replace("\r\n", "\n")
				.replace("\r", "\n")
				.replaceAll("\n{3,}", "\n\n")
				.trim();
	}

	/**
	 * Valida e normaliza uma URL pública HTTP/HTTPS.
	 * Rejeita esquemas diferentes de http/https e endereços locais/privados/internos.
	 * Remove apenas parâmetros de rastreamento reconhecidos, preservando parâmetros de identificação da vaga.
	 */
	public static String validarENormalizarUrlPublica(String urlCandidate) {
		if (urlCandidate == null || urlCandidate.isBlank()) {
			return null;
		}
		String limpa = urlCandidate.trim();
		try {
			URI uri = URI.create(limpa);
			String scheme = uri.getScheme();
			if (scheme == null) {
				return null;
			}
			String schemeLower = scheme.toLowerCase(Locale.ROOT);
			if (!"http".equals(schemeLower) && !"https".equals(schemeLower)) {
				return null;
			}
			String host = uri.getHost();
			if (host == null || host.isBlank()) {
				return null;
			}
			String hostLower = host.toLowerCase(Locale.ROOT);
			if (ehHostInternoOuPrivado(hostLower)) {
				return null;
			}

			String queryFiltrada = filtrarQueryRastreamento(uri.getRawQuery());
			StringBuilder sb = new StringBuilder();
			sb.append(schemeLower).append("://");
			if (uri.getRawUserInfo() != null && !uri.getRawUserInfo().isBlank()) {
				sb.append(uri.getRawUserInfo()).append('@');
			}
			sb.append(hostLower);
			if (uri.getPort() != -1) {
				sb.append(':').append(uri.getPort());
			}
			String rawPath = uri.getRawPath();
			if (rawPath != null && !rawPath.isEmpty()) {
				sb.append(rawPath);
			}
			if (queryFiltrada != null && !queryFiltrada.isBlank()) {
				sb.append('?').append(queryFiltrada);
			}
			return new URI(sb.toString()).toASCIIString();
		} catch (Exception e) {
			return null;
		}
	}

	private static boolean ehHostInternoOuPrivado(String host) {
		if ("localhost".equals(host)
				|| host.endsWith(".localhost")
				|| host.endsWith(".local")
				|| host.endsWith(".internal")
				|| "0.0.0.0".equals(host)
				|| "[::1]".equals(host)
				|| "::1".equals(host)) {
			return true;
		}
		if (host.startsWith("127.")
				|| host.startsWith("10.")
				|| host.startsWith("192.168.")
				|| host.startsWith("169.254.")) {
			return true;
		}
		if (host.startsWith("172.")) {
			String[] partes = host.split("\\.");
			if (partes.length >= 2) {
				try {
					int segundoOcteto = Integer.parseInt(partes[1]);
					if (segundoOcteto >= 16 && segundoOcteto <= 31) {
						return true;
					}
				} catch (NumberFormatException ignored) {
				}
			}
		}
		return !host.contains(".");
	}

	private static String filtrarQueryRastreamento(String rawQuery) {
		if (rawQuery == null || rawQuery.isBlank()) {
			return null;
		}
		String[] pares = rawQuery.split("&");
		List<String> mantidos = new ArrayList<>();
		for (String par : pares) {
			if (par.isBlank()) {
				continue;
			}
			int idx = par.indexOf('=');
			String chave = (idx >= 0 ? par.substring(0, idx) : par).toLowerCase(Locale.ROOT);
			if (!PARAMETROS_RASTREAMENTO.contains(chave)) {
				mantidos.add(par);
			}
		}
		return mantidos.isEmpty() ? null : String.join("&", mantidos);
	}

	/**
	 * Remove qualquer possível token/segredo de mensagens de erro antes de persistir ou exibir.
	 */
	public static String sanitizarMensagemErro(String mensagem, String... segredosConfigurados) {
		if (mensagem == null || mensagem.isBlank()) {
			return "Erro não especificado na coleta.";
		}
		String resultado = mensagem;
		if (segredosConfigurados != null) {
			for (String segredo : segredosConfigurados) {
				if (segredo != null && !segredo.isBlank()) {
					resultado = resultado.replace(segredo, "[REDACTED]");
					resultado = resultado.replace(segredo.trim(), "[REDACTED]");
				}
			}
		}
		resultado = resultado.replaceAll("(?i)(ghp_[A-Za-z0-9_]+|github_pat_[A-Za-z0-9_]+|Bearer\\s+[A-Za-z0-9._\\-]+)", "[REDACTED]");
		resultado = resultado.replaceAll("(?i)(app_key|app_id|api_key|apikey|token|access_token|secret|password)=([^&\\s\"']+)", "$1=[REDACTED]");
		resultado = resultado.replaceAll("(?i)(/api/)([A-Za-z0-9_\\-]{8,})", "$1[REDACTED]");
		if (resultado.length() > 900) {
			resultado = resultado.substring(0, 900) + "...";
		}
		return resultado;
	}
}

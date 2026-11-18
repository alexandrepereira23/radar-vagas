package br.com.radarvagas.coleta;

import br.com.radarvagas.catalogo.AreaTecnologia;
import br.com.radarvagas.catalogo.DisponibilidadeVaga;
import br.com.radarvagas.catalogo.ModalidadeTrabalho;
import br.com.radarvagas.catalogo.RegimeContratacao;
import br.com.radarvagas.catalogo.SanitizadorConteudo;
import br.com.radarvagas.catalogo.Senioridade;
import br.com.radarvagas.catalogo.TipoOportunidade;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class GitHubIssueClassificador {

	public static final String FORNECEDOR_GITHUB = "GITHUB_ISSUES";

	private static final Pattern PADRAO_PREFIXO_COLCHETES = Pattern.compile("^(\\s*\\[[^\\]]+\\]\\s*)+");
	private static final Pattern PADRAO_ITEM_COLCHETE = Pattern.compile("\\[([^\\]]+)\\]");
	private static final Pattern PADRAO_EMPRESA_ARROBA = Pattern.compile("^(?<cargo>.+?)\\s+@\\s+(?<empresa>.+)$");
	private static final Pattern PADRAO_EMPRESA_NA = Pattern.compile("(?i)^(?<cargo>.+?)\\s+(?:na|no|at)\\s+(?<empresa>[A-ZÀ-Ú0-9][^|\\-\\(\\[]{1,60})$");
	private static final Pattern PADRAO_EMPRESA_HIFEN = Pattern.compile("^(?<cargo>.+?)\\s+[-–|]\\s+(?<empresa>[A-ZÀ-Ú0-9][^|\\-\\(\\[]{1,60})$");

	private static final Pattern PADRAO_REGRAS_OU_META = Pattern.compile(
			"(?i)\\b(regras do reposit[oó]rio|como publicar|leia antes de postar|template de vaga|modelo de vaga|c[oó]digo de conduta|aviso importante|d[uú]vida sobre)\\b"
	);

	private static final Pattern PADRAO_PEDIDO_EMPREGO = Pattern.compile(
			"(?i)(\\[\\s*curr[ií]culo\\s*\\]|\\bprocuro vaga\\b|\\bbusco vaga\\b|\\bprocuro oportunidade\\b|\\bbusco oportunidade\\b|\\bdispon[ií]vel para contrata[cç][aã]o\\b|\\bprocuro emprego\\b|\\bofere[cç]o meus servi[cç]os\\b)"
	);

	private static final Pattern PADRAO_BANCO_TALENTOS = Pattern.compile(
			"(?i)\\b(banco de talentos|talent pool|cadastro de reserva)\\b"
	);

	private static final Pattern PADRAO_INELEGIVEL_BRASIL = Pattern.compile(
			"(?i)\\b(us only|usa only|u\\.s\\. only|eu only|europe only|uk only|canada only|must reside in the us|not hiring in brazil|apenas residentes nos eua|apenas residentes na europa)\\b"
	);

	private static final Pattern PADRAO_INDICACAO_BRASIL_OU_LATAM = Pattern.compile(
			"(?i)\\b(brasil|brazil|s[aã]o paulo|rio de janeiro|belo horizonte|curitiba|porto alegre|recife|florian[oó]polis|bras[ií]lia|campinas|fortaleza|salvador|goi[aâ]nia|joinville|blumenau|maring[aá]|londrina|latam|latin america|am[eé]rica latina|south america|americas|remote\\s*[-,/]\\s*amer)\\b"
	);

	private static final Pattern PADRAO_CARGO_TECNICO = Pattern.compile(
			"(?i)\\b(desenvolvedor|desenvolvedora|developer|software|engineer|engenheiro|engenheira|programador|programadora|front-?end|back-?end|full-?stack|mobile|android|ios|devops|sre|reliability|infraestrutura|infrastructure|cloud|arquitet[oa]|architect|qa|quality assurance|test|tester|sdet|dados|data|analytics|scientist|cientista|bi|business intelligence|machine learning|deep learning|ai|artificial intelligence|intelig[eê]ncia artificial|mlops|llm|nlp|seguran[cç]a|security|cybersecurity|infosec|appsec|devsecops|pentest|soc|redes|network|sysadmin|suporte t[eé]cnico|technical support|help\\s*desk|service\\s*desk|it support|analista de suporte|analista de sistemas|analista de ti|tech lead|team lead|engineering manager|cto|product manager|product owner|scrum master|agile coach|ux|ui|product designer|designer de produto|banco de dados|dba|erp|sap|salesforce|rpa|automa[cç][aã]o)\\b"
	);

	private static final Pattern PADRAO_CARGO_NAO_TECNICO = Pattern.compile(
			"(?i)\\b(account executive|sales representative|vendedor|consultor de vendas|inside sales|sdr|bdr|talent acquisition|recruiter|recrutador|human resources|recursos humanos|people partner|advogad[oa]|legal counsel|jur[ií]dico|contador|accounting|tax analyst|fiscal|collections|cobran[cç]a|real estate agent|corretor|fot[oó]grafo|receptionist|recepcionista|facilities|office manager)\\b"
	);

	private static final Pattern PADRAO_URL = Pattern.compile(
			"https?://[^\\s\\)\\]>\"'`]+"
	);

	private static final Map<String, Pattern> REGRAS_TECNOLOGIAS = criarRegrasTecnologias();

	public record DadosBrutosIssue(
			String repositorio,
			String numeroIssue,
			String titulo,
			String corpo,
			String estado,
			boolean pullRequest,
			List<String> labels,
			String htmlUrl,
			Instant createdAt,
			Instant updatedAt
	) {
	}

	public Optional<AnuncioExtraido> classificar(DadosBrutosIssue bruta) {
		if (bruta == null || bruta.pullRequest()) {
			return Optional.empty();
		}
		String tituloBruto = bruta.titulo() != null ? bruta.titulo().trim() : "";
		if (tituloBruto.isBlank()) {
			return Optional.empty();
		}

		List<String> labels = bruta.labels() != null ? bruta.labels() : List.of();
		String labelsTexto = String.join(" ", labels);
		String corpoBruto = bruta.corpo() != null ? bruta.corpo() : "";

		if (ehRegrasOuPedidoDeEmprego(tituloBruto, labelsTexto, corpoBruto)) {
			return Optional.empty();
		}

		String urlOrigemSegura = SanitizadorConteudo.validarENormalizarUrlPublica(bruta.htmlUrl());
		if (urlOrigemSegura == null) {
			return Optional.empty();
		}

		String descricaoLimpa = SanitizadorConteudo.sanitizarTexto(corpoBruto);
		if (descricaoLimpa.isBlank()) {
			descricaoLimpa = SanitizadorConteudo.sanitizarTexto(tituloBruto);
		}

		List<String> tagsColchetes = extrairTagsColchetes(tituloBruto);
		String tituloSemColchetes = removerPrefixoColchetes(tituloBruto);
		CargoEEmpresa cargoEEmpresa = extrairCargoEEmpresa(tituloSemColchetes, descricaoLimpa);

		String contextoClassificacao = tituloBruto + "\n" + labelsTexto + "\n" + descricaoLimpa;

		TipoOportunidade tipoOportunidade = PADRAO_BANCO_TALENTOS.matcher(contextoClassificacao).find()
				? TipoOportunidade.BANCO_DE_TALENTOS
				: TipoOportunidade.VAGA_ABERTA;

		ModalidadeTrabalho modalidade = classificarModalidade(tagsColchetes, labels, tituloBruto, descricaoLimpa);
		Senioridade senioridade = classificarSenioridade(tagsColchetes, labels, tituloBruto, descricaoLimpa);
		RegimeContratacao regime = classificarRegime(tagsColchetes, labels, tituloBruto, descricaoLimpa, senioridade);
		AreaTecnologia area = classificarArea(bruta.repositorio(), labels, tituloBruto, descricaoLimpa);
		String localizacao = extrairLocalizacao(tagsColchetes, labels, descricaoLimpa, modalidade);
		boolean elegivelBrasil = !PADRAO_INELEGIVEL_BRASIL.matcher(contextoClassificacao).find();
		List<String> tecnologias = detectarTecnologias(contextoClassificacao);

		String urlCandidatura = extrairUrlCandidatura(descricaoLimpa, urlOrigemSegura);

		boolean fechada = "closed".equalsIgnoreCase(bruta.estado());
		DisponibilidadeVaga disponibilidade = fechada
				? DisponibilidadeVaga.INDISPONIVEL_NA_ORIGEM
				: DisponibilidadeVaga.DISPONIVEL;
		String motivoIndisponibilidade = fechada
				? "Issue fechada no GitHub (pode indicar encerramento da vaga ou moderação)."
				: null;

		return Optional.of(new AnuncioExtraido(
				FORNECEDOR_GITHUB,
				bruta.repositorio(),
				bruta.numeroIssue(),
				SanitizadorConteudo.sanitizarTexto(tituloBruto),
				descricaoLimpa,
				labels,
				cargoEEmpresa.cargo(),
				cargoEEmpresa.empresa(),
				descricaoLimpa,
				area,
				senioridade,
				tipoOportunidade,
				regime,
				modalidade,
				localizacao,
				elegivelBrasil,
				tecnologias,
				disponibilidade,
				motivoIndisponibilidade,
				urlOrigemSegura,
				urlCandidatura,
				bruta.createdAt(),
				bruta.updatedAt()
		));
	}

	/**
	 * Constrói e classifica um anúncio vindo de murais de empresas, portais ou agregadores,
	 * aplicando filtro de cargo técnico, validação de elegibilidade no Brasil e sanitização.
	 */
	public Optional<AnuncioExtraido> classificarAnuncioExterno(
			String fornecedor,
			String identificadorMural,
			String identificadorExterno,
			String tituloBruto,
			String nomeEmpresaInformado,
			String descricaoBruta,
			String localizacaoInformada,
			ModalidadeTrabalho modalidadeInformada,
			RegimeContratacao regimeInformado,
			TipoOportunidade tipoOportunidadeInformado,
			List<String> categoriasOuLabels,
			String urlOrigemBruta,
			String urlCandidaturaBruta,
			Instant publicadoEm,
			Instant atualizadoEm,
			boolean exigirIndicacaoExplicitaBrasil,
			boolean exigirCargoTecnico,
			String codigoRequisicao
	) {
		if (tituloBruto == null || tituloBruto.isBlank() || identificadorExterno == null || identificadorExterno.isBlank()) {
			return Optional.empty();
		}
		String tituloLimpo = SanitizadorConteudo.sanitizarTexto(tituloBruto);
		String descricaoLimpa = SanitizadorConteudo.sanitizarTexto(descricaoBruta);
		if (descricaoLimpa.isBlank()) {
			descricaoLimpa = tituloLimpo;
		}

		List<String> labels = categoriasOuLabels != null ? categoriasOuLabels : List.of();
		String labelsTexto = String.join(" ", labels);

		if (exigirCargoTecnico && !ehCargoTecnico(tituloLimpo, labelsTexto)) {
			return Optional.empty();
		}

		String contextoCompleto = tituloLimpo + "\n" + labelsTexto + "\n" + (localizacaoInformada != null ? localizacaoInformada : "") + "\n" + descricaoLimpa;
		if (PADRAO_INELEGIVEL_BRASIL.matcher(contextoCompleto).find()) {
			return Optional.empty();
		}
		if (exigirIndicacaoExplicitaBrasil && !possuiIndicacaoBrasilOuLatam(localizacaoInformada, tituloLimpo, labelsTexto, descricaoLimpa)) {
			return Optional.empty();
		}

		String urlOrigemSegura = SanitizadorConteudo.validarENormalizarUrlPublica(urlOrigemBruta);
		String urlCandSegura = SanitizadorConteudo.validarENormalizarUrlPublica(urlCandidaturaBruta);
		if (urlOrigemSegura == null && urlCandSegura == null) {
			return Optional.empty();
		}
		if (urlOrigemSegura == null) {
			urlOrigemSegura = urlCandSegura;
		}
		if (urlCandSegura == null) {
			urlCandSegura = urlOrigemSegura;
		}

		TipoOportunidade tipo = tipoOportunidadeInformado != null
				? tipoOportunidadeInformado
				: (PADRAO_BANCO_TALENTOS.matcher(contextoCompleto).find()
						? TipoOportunidade.BANCO_DE_TALENTOS
						: TipoOportunidade.VAGA_ABERTA);

		ModalidadeTrabalho modalidade = (modalidadeInformada != null && modalidadeInformada != ModalidadeTrabalho.NAO_INFORMADA)
				? modalidadeInformada
				: classificarModalidade(List.of(), labels, tituloLimpo, (localizacaoInformada != null ? localizacaoInformada + "\n" : "") + descricaoLimpa);

		Senioridade senioridade = classificarSenioridade(List.of(), labels, tituloLimpo, descricaoLimpa);

		RegimeContratacao regime = (regimeInformado != null && regimeInformado != RegimeContratacao.NAO_INFORMADO)
				? regimeInformado
				: classificarRegime(List.of(), labels, tituloLimpo, descricaoLimpa, senioridade);

		AreaTecnologia area = classificarArea(identificadorMural, labels, tituloLimpo, descricaoLimpa);
		List<String> tecnologias = detectarTecnologias(contextoCompleto);

		String localLimpo = (localizacaoInformada != null && !localizacaoInformada.isBlank())
				? SanitizadorConteudo.sanitizarTexto(localizacaoInformada)
				: extrairLocalizacao(List.of(), labels, descricaoLimpa, modalidade);

		String empresaLimpa = limparEmpresa(nomeEmpresaInformado);
		String reqLimpo = (codigoRequisicao != null && !codigoRequisicao.isBlank()) ? codigoRequisicao.trim() : null;

		return Optional.of(new AnuncioExtraido(
				fornecedor,
				identificadorMural,
				identificadorExterno.trim(),
				tituloLimpo,
				descricaoLimpa,
				labels,
				limparCargo(tituloLimpo),
				empresaLimpa,
				descricaoLimpa,
				area,
				senioridade,
				tipo,
				regime,
				modalidade,
				localLimpo,
				true,
				tecnologias,
				DisponibilidadeVaga.DISPONIVEL,
				null,
				urlOrigemSegura,
				urlCandSegura,
				publicadoEm,
				atualizadoEm,
				reqLimpo
		));
	}

	public boolean ehCargoTecnico(String titulo, String categoriasOuDepartamento) {
		String alvo = ((titulo != null ? titulo : "") + " " + (categoriasOuDepartamento != null ? categoriasOuDepartamento : "")).trim();
		if (PADRAO_CARGO_NAO_TECNICO.matcher(alvo).find() && !PADRAO_CARGO_TECNICO.matcher(titulo != null ? titulo : "").find()) {
			return false;
		}
		return PADRAO_CARGO_TECNICO.matcher(alvo).find();
	}

	public boolean possuiIndicacaoBrasilOuLatam(String localizacao, String titulo, String labels, String descricao) {
		String prioritario = ((localizacao != null ? localizacao : "") + " " + (titulo != null ? titulo : "") + " " + (labels != null ? labels : "")).trim();
		if (PADRAO_INDICACAO_BRASIL_OU_LATAM.matcher(prioritario).find()) {
			return true;
		}
		return descricao != null && PADRAO_INDICACAO_BRASIL_OU_LATAM.matcher(descricao).find();
	}

	private boolean ehRegrasOuPedidoDeEmprego(String titulo, String labels, String corpo) {
		if (PADRAO_REGRAS_OU_META.matcher(titulo).find()) {
			return true;
		}
		if (PADRAO_PEDIDO_EMPREGO.matcher(titulo).find() || PADRAO_PEDIDO_EMPREGO.matcher(corpo).find()) {
			return true;
		}
		String labelsLower = labels.toLowerCase(Locale.ROOT);
		return labelsLower.contains("invalid")
				|| labelsLower.contains("duplicada")
				|| labelsLower.contains("duplicate")
				|| labelsLower.contains("spam")
				|| labelsLower.contains("question")
				|| labelsLower.contains("dúvida");
	}

	private List<String> extrairTagsColchetes(String titulo) {
		List<String> tags = new ArrayList<>();
		Matcher m = PADRAO_ITEM_COLCHETE.matcher(titulo);
		while (m.find()) {
			String item = m.group(1).trim();
			if (!item.isBlank()) {
				tags.add(item);
			}
		}
		return tags;
	}

	private String removerPrefixoColchetes(String titulo) {
		String limpo = PADRAO_PREFIXO_COLCHETES.matcher(titulo).replaceFirst("").trim();
		return limpo.isBlank() ? titulo.trim() : limpo;
	}

	private record CargoEEmpresa(String cargo, String empresa) {
	}

	private CargoEEmpresa extrairCargoEEmpresa(String tituloSemColchetes, String descricaoLimpa) {
		String base = SanitizadorConteudo.sanitizarTexto(tituloSemColchetes);
		Matcher mArroba = PADRAO_EMPRESA_ARROBA.matcher(base);
		if (mArroba.matches()) {
			return new CargoEEmpresa(limparCargo(mArroba.group("cargo")), limparEmpresa(mArroba.group("empresa")));
		}
		Matcher mNa = PADRAO_EMPRESA_NA.matcher(base);
		if (mNa.matches()) {
			String empresaCand = limparEmpresa(mNa.group("empresa"));
			if (!ehPalavraDeModalidadeOuLocal(empresaCand)) {
				return new CargoEEmpresa(limparCargo(mNa.group("cargo")), empresaCand);
			}
		}
		Matcher mHifen = PADRAO_EMPRESA_HIFEN.matcher(base);
		if (mHifen.matches()) {
			String empresaCand = limparEmpresa(mHifen.group("empresa"));
			if (!ehPalavraDeModalidadeOuLocal(empresaCand) && !ehPalavraDeNivelOuRegime(empresaCand)) {
				return new CargoEEmpresa(limparCargo(mHifen.group("cargo")), empresaCand);
			}
		}

		// Tenta identificar no corpo quando houver linha explícita "Empresa: Nome"
		Pattern empresaCorpo = Pattern.compile("(?im)^(?:empresa|company|contratante)\\s*:\\s*([^\\n\\r]{2,80})$");
		Matcher mCorpo = empresaCorpo.matcher(descricaoLimpa);
		if (mCorpo.find()) {
			String emp = limparEmpresa(mCorpo.group(1));
			if (emp != null && !ehPalavraDeModalidadeOuLocal(emp)) {
				return new CargoEEmpresa(limparCargo(base), emp);
			}
		}

		return new CargoEEmpresa(limparCargo(base), null);
	}

	private String limparCargo(String cargo) {
		if (cargo == null) {
			return "Vaga em Tecnologia";
		}
		String c = cargo.replaceAll("\\s+", " ").trim();
		if (c.length() > 280) {
			c = c.substring(0, 280);
		}
		return c.isBlank() ? "Vaga em Tecnologia" : c;
	}

	private String limparEmpresa(String empresa) {
		if (empresa == null) {
			return null;
		}
		String e = empresa.replaceAll("[\\[\\]]", "").replaceAll("\\s+", " ").trim();
		if (e.isBlank() || e.length() < 2 || e.length() > 120) {
			return null;
		}
		String lower = e.toLowerCase(Locale.ROOT);
		if (lower.contains("confidencial") || lower.contains("não informad") || lower.equals("remoto") || lower.equals("brasil")) {
			return null;
		}
		return e;
	}

	private boolean ehPalavraDeModalidadeOuLocal(String texto) {
		if (texto == null) {
			return true;
		}
		String t = texto.toLowerCase(Locale.ROOT).trim();
		if (t.equals("remoto") || t.equals("híbrido") || t.equals("hibrido") || t.equals("presencial")
				|| t.equals("home office") || t.equals("brasil") || t.matches("^[a-zà-ú\\s]+/[a-z]{2}$")) {
			return true;
		}
		return t.matches("^(remoto|h[ií]brido|presencial|home\\s*office|clt|pj|s[aã]o paulo|rio de janeiro|belo horizonte|curitiba|porto alegre|recife|florian[oó]polis|bras[ií]lia)(\\s+[-/|]?\\s*(remoto|h[ií]brido|presencial|clt|pj|sp|rj|mg|pr|rs|sc|pe|df|brasil))*$");
	}

	private boolean ehPalavraDeNivelOuRegime(String texto) {
		if (texto == null) {
			return true;
		}
		String t = texto.toLowerCase(Locale.ROOT).trim();
		return t.equals("júnior") || t.equals("junior") || t.equals("pleno") || t.equals("sênior")
				|| t.equals("senior") || t.equals("clt") || t.equals("pj") || t.equals("estágio") || t.equals("estagio");
	}

	private ModalidadeTrabalho classificarModalidade(
			List<String> tagsColchetes,
			List<String> labels,
			String titulo,
			String corpo
	) {
		String prioritario = (String.join(" ", tagsColchetes) + " " + String.join(" ", labels) + " " + titulo)
				.toLowerCase(Locale.ROOT);

		if (contemHibrido(prioritario)) {
			return ModalidadeTrabalho.HIBRIDO;
		}
		if (contemRemoto(prioritario) && !contemPresencial(prioritario)) {
			return ModalidadeTrabalho.REMOTO;
		}
		if (contemPresencial(prioritario) && !contemRemoto(prioritario)) {
			return ModalidadeTrabalho.PRESENCIAL;
		}

		String corpoLower = corpo.toLowerCase(Locale.ROOT);
		boolean remoto = contemRemoto(corpoLower);
		boolean hibrido = contemHibrido(corpoLower);
		boolean presencial = contemPresencial(corpoLower);

		if (hibrido) {
			return ModalidadeTrabalho.HIBRIDO;
		}
		if (remoto && !presencial) {
			return ModalidadeTrabalho.REMOTO;
		}
		if (presencial && !remoto) {
			return ModalidadeTrabalho.PRESENCIAL;
		}
		return ModalidadeTrabalho.NAO_INFORMADA;
	}

	private boolean contemRemoto(String s) {
		return s.contains("remoto") || s.contains("remote") || s.contains("home office") || s.contains("home-office") || s.contains("100% remoto");
	}

	private boolean contemHibrido(String s) {
		return s.contains("híbrido") || s.contains("hibrido") || s.contains("hybrid");
	}

	private boolean contemPresencial(String s) {
		return s.contains("presencial") || s.contains("on-site") || s.contains("onsite");
	}

	private Senioridade classificarSenioridade(
			List<String> tagsColchetes,
			List<String> labels,
			String titulo,
			String corpo
	) {
		String prioritario = (String.join(" ", tagsColchetes) + " " + String.join(" ", labels) + " " + titulo)
				.toLowerCase(Locale.ROOT);
		Senioridade sPrioritario = detectarSenioridadeEmTexto(prioritario);
		if (sPrioritario != Senioridade.NAO_INFORMADA) {
			return sPrioritario;
		}
		return detectarSenioridadeEmTexto(corpo.toLowerCase(Locale.ROOT));
	}

	private Senioridade detectarSenioridadeEmTexto(String texto) {
		if (Pattern.compile("\\b(est[aá]gio|estagi[aá]rio|intern|internship)\\b").matcher(texto).find()) {
			return Senioridade.ESTAGIO;
		}
		if (Pattern.compile("\\b(trainee)\\b").matcher(texto).find()) {
			return Senioridade.TRAINEE;
		}
		if (Pattern.compile("\\b(tech lead|team lead|engineering manager|gerente de engenharia|coordenador|head de| CTO |lideran[cç]a)\\b").matcher(texto).find()) {
			return Senioridade.LIDERANCA;
		}
		if (Pattern.compile("\\b(especialista|staff|principal|architect|arquiteto)\\b").matcher(texto).find()) {
			return Senioridade.ESPECIALISTA;
		}
		boolean jr = Pattern.compile("\\b(j[uú]nior|jr)\\b").matcher(texto).find();
		boolean pl = Pattern.compile("\\b(pleno|mid-level|mid level)\\b").matcher(texto).find();
		boolean sr = Pattern.compile("\\b(s[eê]nior|sr)\\b").matcher(texto).find();

		int count = (jr ? 1 : 0) + (pl ? 1 : 0) + (sr ? 1 : 0);
		if (count > 1) {
			// Múltiplos níveis no mesmo texto — prioriza se apenas um estiver claro, caso contrário mantém o mais abrangente
			if (pl) {
				return Senioridade.PLENO;
			}
		}
		if (sr) {
			return Senioridade.SENIOR;
		}
		if (pl) {
			return Senioridade.PLENO;
		}
		if (jr) {
			return Senioridade.JUNIOR;
		}
		return Senioridade.NAO_INFORMADA;
	}

	private RegimeContratacao classificarRegime(
			List<String> tagsColchetes,
			List<String> labels,
			String titulo,
			String corpo,
			Senioridade senioridade
	) {
		if (senioridade == Senioridade.ESTAGIO) {
			return RegimeContratacao.ESTAGIO;
		}
		String texto = (String.join(" ", tagsColchetes) + " " + String.join(" ", labels) + " " + titulo + "\n" + corpo)
				.toLowerCase(Locale.ROOT);
		boolean clt = Pattern.compile("\\bclt\\b").matcher(texto).find();
		boolean pj = Pattern.compile("\\bpj\\b|\\bpessoa jur[ií]dica\\b").matcher(texto).find();
		boolean coop = Pattern.compile("\\bcooperado\\b|\\bcooperativa\\b").matcher(texto).find();

		if (clt && !pj) {
			return RegimeContratacao.CLT;
		}
		if (pj && !clt) {
			return RegimeContratacao.PJ;
		}
		if (clt) {
			return RegimeContratacao.CLT;
		}
		if (coop) {
			return RegimeContratacao.COOPERADO;
		}
		return RegimeContratacao.NAO_INFORMADO;
	}

	private AreaTecnologia classificarArea(String repositorio, List<String> labels, String titulo, String corpo) {
		String prioritario = (titulo + " " + String.join(" ", labels)).toLowerCase(Locale.ROOT);
		if (Pattern.compile("\\b(qa|quality assurance|testes|tester|autom[aá][cç][aã]o de testes|sdet)\\b").matcher(prioritario).find()) {
			return AreaTecnologia.QA;
		}
		if (Pattern.compile("\\b(machine learning|deep learning|intelig[eê]ncia artificial|llm|nlp|computer vision|ai engineer|mlops)\\b").matcher(prioritario).find()) {
			return AreaTecnologia.IA;
		}
		if (Pattern.compile("\\b(dados|data engineer|data scientist|cientista de dados|analista de dados|analytics|bi|business intelligence)\\b").matcher(prioritario).find()) {
			return AreaTecnologia.DADOS;
		}
		if (Pattern.compile("\\b(seguran[cç]a|cybersecurity|infosec|appsec|devsecops|pentest|soc)\\b").matcher(prioritario).find()) {
			return AreaTecnologia.SEGURANCA;
		}
		if (Pattern.compile("\\b(devops|sre|infraestrutura|sysadmin|platform engineer)\\b").matcher(prioritario).find()) {
			return AreaTecnologia.INFRAESTRUTURA;
		}
		if (Pattern.compile("\\b(cloud|aws|azure|gcp|arquiteto cloud)\\b").matcher(prioritario).find()) {
			return AreaTecnologia.CLOUD;
		}
		if (Pattern.compile("\\b(redes|network engineer|telecom)\\b").matcher(prioritario).find()) {
			return AreaTecnologia.REDES;
		}
		if (Pattern.compile("\\b(suporte|help desk|service desk|n1|n2|n3)\\b").matcher(prioritario).find()) {
			return AreaTecnologia.SUPORTE;
		}
		if (Pattern.compile("\\b(product manager|product owner|ux|ui|product designer)\\b").matcher(prioritario).find()) {
			return AreaTecnologia.PRODUTO_DESIGN;
		}
		if (Pattern.compile("\\b(desenvolvedor|developer|software engineer|engenheiro de software|front-end|frontend|back-end|backend|full-stack|fullstack|mobile|ios|android|programador|dev)\\b").matcher(prioritario).find()) {
			return AreaTecnologia.DESENVOLVIMENTO;
		}

		if (repositorio != null) {
			String repoLower = repositorio.toLowerCase(Locale.ROOT);
			if (repoLower.contains("backend") || repoLower.contains("frontend")) {
				return AreaTecnologia.DESENVOLVIMENTO;
			}
			if (repoLower.contains("qa")) {
				return AreaTecnologia.QA;
			}
			if (repoLower.contains("datascience") || repoLower.contains("dados")) {
				return AreaTecnologia.DADOS;
			}
			if (repoLower.contains("devops")) {
				return AreaTecnologia.INFRAESTRUTURA;
			}
		}
		return AreaTecnologia.NAO_INFORMADA;
	}

	private String extrairLocalizacao(
			List<String> tagsColchetes,
			List<String> labels,
			String descricaoLimpa,
			ModalidadeTrabalho modalidade
	) {
		for (String tag : tagsColchetes) {
			String t = tag.trim();
			if (!ehPalavraApenasModalidadeOuNivel(t)) {
				return t;
			}
		}
		for (String label : labels) {
			String l = label.trim();
			if (l.matches("(?i).*(são paulo|rio de janeiro|belo horizonte|curitiba|porto alegre|recife|florianópolis|brasília|campinas| /[a-z]{2}|-[a-z]{2}).*")) {
				return l;
			}
		}
		Pattern localCorpo = Pattern.compile("(?im)^(?:#+\\s*)?(?:local|localiza[cç][aã]o|cidade)\\s*:?\\s*\\n?\\s*([^\\n\\r#]{2,80})");
		Matcher m = localCorpo.matcher(descricaoLimpa);
		if (m.find()) {
			String candidato = m.group(1).replaceAll("^[\\-*•]+\\s*", "").trim();
			if (!candidato.isBlank() && candidato.length() <= 80) {
				return candidato;
			}
		}
		if (modalidade == ModalidadeTrabalho.REMOTO) {
			return "Remoto (Brasil)";
		}
		return null;
	}

	private boolean ehPalavraApenasModalidadeOuNivel(String tag) {
		String lower = tag.toLowerCase(Locale.ROOT).trim();
		return lower.equals("remoto")
				|| lower.equals("remote")
				|| lower.equals("híbrido")
				|| lower.equals("hibrido")
				|| lower.equals("presencial")
				|| lower.equals("home office")
				|| lower.equals("clt")
				|| lower.equals("pj")
				|| lower.equals("júnior")
				|| lower.equals("junior")
				|| lower.equals("pleno")
				|| lower.equals("sênior")
				|| lower.equals("senior")
				|| lower.equals("estágio")
				|| lower.equals("estagio");
	}

	private List<String> detectarTecnologias(String texto) {
		List<String> encontradas = new ArrayList<>();
		for (Map.Entry<String, Pattern> entry : REGRAS_TECNOLOGIAS.entrySet()) {
			if (entry.getValue().matcher(texto).find()) {
				encontradas.add(entry.getKey());
			}
		}
		return encontradas;
	}

	private String extrairUrlCandidatura(String descricaoLimpa, String urlOrigemFallback) {
		Matcher m = PADRAO_URL.matcher(descricaoLimpa);
		List<String> candidatas = new ArrayList<>();
		while (m.find()) {
			String bruta = m.group().replaceAll("[.,;:!?)\\]*_~]+$", "");
			String validada = SanitizadorConteudo.validarENormalizarUrlPublica(bruta);
			if (validada != null && !ehUrlDeImagemOuTemplate(validada)) {
				candidatas.add(validada);
			}
		}
		if (!candidatas.isEmpty()) {
			return candidatas.get(candidatas.size() - 1);
		}
		return urlOrigemFallback;
	}

	private boolean ehUrlDeImagemOuTemplate(String url) {
		String lower = url.toLowerCase(Locale.ROOT);
		return lower.endsWith(".png")
				|| lower.endsWith(".jpg")
				|| lower.endsWith(".jpeg")
				|| lower.endsWith(".gif")
				|| lower.endsWith(".svg")
				|| lower.contains("user-images.githubusercontent.com")
				|| lower.contains("img.shields.io");
	}

	private static Map<String, Pattern> criarRegrasTecnologias() {
		Map<String, Pattern> mapa = new LinkedHashMap<>();
		// Evita confundir Java com JavaScript!
		mapa.put("Java", Pattern.compile("(?i)\\bjava\\b(?!\\s*script)"));
		mapa.put("JavaScript", Pattern.compile("(?i)\\b(javascript|ecmascript)\\b"));
		mapa.put("TypeScript", Pattern.compile("(?i)\\btypescript\\b"));
		mapa.put("Spring Boot", Pattern.compile("(?i)\\bspring\\s*boot\\b"));
		mapa.put("Angular", Pattern.compile("(?i)\\bangular\\b(?!\\s*js)"));
		mapa.put("React", Pattern.compile("(?i)\\breact(?:\\.js|js)?\\b(?!\\s*native)"));
		mapa.put("React Native", Pattern.compile("(?i)\\breact\\s+native\\b"));
		mapa.put("Vue.js", Pattern.compile("(?i)\\bvue(?:\\.js|js)?\\b"));
		mapa.put("Node.js", Pattern.compile("(?i)\\bnode(?:\\.js|js)?\\b"));
		mapa.put("Python", Pattern.compile("(?i)\\bpython\\b"));
		mapa.put("C#", Pattern.compile("(?i)(?:\\bc#|\\bcsharp\\b)"));
		mapa.put(".NET", Pattern.compile("(?i)(?:\\bdotnet\\b|(?<!\\w)\\.net\\b)"));
		mapa.put("Go", Pattern.compile("(?i)\\bgolang\\b"));
		mapa.put("PHP", Pattern.compile("(?i)\\bphp\\b"));
		mapa.put("Kotlin", Pattern.compile("(?i)\\bkotlin\\b"));
		mapa.put("Ruby", Pattern.compile("(?i)\\bruby\\b"));
		mapa.put("Rust", Pattern.compile("(?i)\\brust\\b"));
		mapa.put("PostgreSQL", Pattern.compile("(?i)\\b(postgresql|postgres)\\b"));
		mapa.put("MySQL", Pattern.compile("(?i)\\bmysql\\b"));
		mapa.put("MongoDB", Pattern.compile("(?i)\\bmongodb\\b"));
		mapa.put("Docker", Pattern.compile("(?i)\\bdocker\\b"));
		mapa.put("Kubernetes", Pattern.compile("(?i)\\b(kubernetes|k8s)\\b"));
		mapa.put("AWS", Pattern.compile("(?i)\\b(aws|amazon web services)\\b"));
		mapa.put("Azure", Pattern.compile("(?i)\\bazure\\b"));
		mapa.put("GCP", Pattern.compile("(?i)\\b(gcp|google cloud)\\b"));
		return mapa;
	}
}

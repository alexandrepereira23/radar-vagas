package br.com.radarvagas.catalogo;

import br.com.radarvagas.acoes.AcaoPessoal;
import br.com.radarvagas.acoes.AcaoPessoalRepository;
import br.com.radarvagas.coleta.AnuncioExtraido;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogoVagasService {

	public enum ResultadoPersistenciaAnuncio {
		NOVO,
		ATUALIZADO,
		REPETIDO
	}

	private final EmpresaRepository empresaRepository;
	private final OportunidadeRepository oportunidadeRepository;
	private final AnuncioOrigemRepository anuncioOrigemRepository;
	private final AcaoPessoalRepository acaoPessoalRepository;
	private final Clock clock;

	public CatalogoVagasService(
			EmpresaRepository empresaRepository,
			OportunidadeRepository oportunidadeRepository,
			AnuncioOrigemRepository anuncioOrigemRepository,
			AcaoPessoalRepository acaoPessoalRepository,
			Clock clock
	) {
		this.empresaRepository = empresaRepository;
		this.oportunidadeRepository = oportunidadeRepository;
		this.anuncioOrigemRepository = anuncioOrigemRepository;
		this.acaoPessoalRepository = acaoPessoalRepository;
		this.clock = clock;
	}

	@Transactional
	public ResultadoPersistenciaAnuncio registrarOuAtualizarAnuncio(AnuncioExtraido extraido) {
		Instant agora = Instant.now(clock);

		Optional<AnuncioOrigem> existenteOpt = anuncioOrigemRepository
				.findByFornecedorAndIdentificadorMuralAndIdentificadorExterno(
						extraido.fornecedor(),
						extraido.identificadorMural(),
						extraido.identificadorExterno()
				);

		if (existenteOpt.isPresent()) {
			return atualizarAnuncioExistente(existenteOpt.get(), extraido, agora);
		}

		return criarNovoAnuncio(extraido, agora);
	}

	private ResultadoPersistenciaAnuncio atualizarAnuncioExistente(
			AnuncioOrigem anuncio,
			AnuncioExtraido extraido,
			Instant agora
	) {
		String novasLabels = juntarLista(extraido.labelsOriginais());
		boolean mudouConteudo = !Objects.equals(anuncio.getTituloOriginal(), extraido.tituloOriginal())
				|| !Objects.equals(anuncio.getCorpoOriginal(), extraido.corpoOriginal())
				|| !Objects.equals(anuncio.getLabelsOriginais(), novasLabels)
				|| anuncio.getDisponibilidadeObservada() != extraido.disponibilidadeObservada()
				|| !Objects.equals(anuncio.getAtualizadoNaFonteEm(), extraido.atualizadoNaFonteEm());

		anuncio.setUltimaObservacaoEm(agora);
		Oportunidade op = anuncio.getOportunidade();
		op.setUltimaObservacaoEm(agora);

		if (!mudouConteudo) {
			anuncioOrigemRepository.save(anuncio);
			oportunidadeRepository.save(op);
			return ResultadoPersistenciaAnuncio.REPETIDO;
		}

		anuncio.setTituloOriginal(extraido.tituloOriginal());
		anuncio.setCorpoOriginal(extraido.corpoOriginal());
		anuncio.setLabelsOriginais(novasLabels);
		anuncio.setUrlOrigem(extraido.urlOrigem());
		anuncio.setUrlCandidatura(extraido.urlCandidatura());
		anuncio.setCodigoRequisicao(extraido.codigoRequisicao());
		anuncio.setDisponibilidadeObservada(extraido.disponibilidadeObservada());
		anuncio.setMotivoIndisponibilidade(extraido.motivoIndisponibilidade());
		// Preserva publicadoNaFonteEm original; nunca usa updatedAt como nova publicação
		if (anuncio.getPublicadoNaFonteEm() == null && extraido.publicadoNaFonteEm() != null) {
			anuncio.setPublicadoNaFonteEm(extraido.publicadoNaFonteEm());
		}
		if (extraido.atualizadoNaFonteEm() != null) {
			anuncio.setAtualizadoNaFonteEm(extraido.atualizadoNaFonteEm());
		}
		anuncioOrigemRepository.save(anuncio);

		// Atualiza a oportunidade consolidada preservando ID, primeiraObservacaoEm, publicadoEm e AcaoPessoal
		Empresa empresa = resolverEmpresa(extraido.nomeEmpresa(), agora);
		if (empresa != null) {
			op.setEmpresa(empresa);
			op.setNomeEmpresa(empresa.getNome());
		}
		op.setCargo(extraido.cargo());
		op.setDescricao(extraido.descricaoLimpa());
		op.setArea(extraido.area());
		op.setSenioridade(extraido.senioridade());
		op.setTipoOportunidade(extraido.tipoOportunidade());
		op.setRegimeContratacao(extraido.regimeContratacao());
		op.setModalidade(extraido.modalidade());
		op.setLocalizacao(extraido.localizacao());
		op.setElegivelBrasil(extraido.elegivelBrasil());
		op.setTecnologias(juntarLista(extraido.tecnologias()));
		op.setDisponibilidadeConsolidada(extraido.disponibilidadeObservada());
		op.setMotivoDisponibilidade(extraido.motivoIndisponibilidade());
		op.setLinkCandidatura(extraido.urlCandidatura());
		op.setLinkOrigemPrincipal(extraido.urlOrigem());
		if (op.getCodigoRequisicao() == null && extraido.codigoRequisicao() != null) {
			op.setCodigoRequisicao(extraido.codigoRequisicao());
		}
		if (op.getPublicadoEm() == null && extraido.publicadoNaFonteEm() != null) {
			op.setPublicadoEm(extraido.publicadoNaFonteEm());
		}
		if (extraido.atualizadoNaFonteEm() != null) {
			op.setAtualizadoNaFonteEm(extraido.atualizadoNaFonteEm());
		}
		oportunidadeRepository.save(op);

		return ResultadoPersistenciaAnuncio.ATUALIZADO;
	}

	private ResultadoPersistenciaAnuncio criarNovoAnuncio(AnuncioExtraido extraido, Instant agora) {
		String chaveDedup = calcularChaveDeduplicacaoConservadora(extraido);
		Optional<Oportunidade> oportunidadeExistente = localizarOportunidadeCorrespondente(extraido, chaveDedup);

		Oportunidade op;
		if (oportunidadeExistente.isPresent()) {
			op = oportunidadeExistente.get();
			op.setUltimaObservacaoEm(agora);
			if (op.getPublicadoEm() == null && extraido.publicadoNaFonteEm() != null) {
				op.setPublicadoEm(extraido.publicadoNaFonteEm());
			}
			if (op.getNomeEmpresa() == null && extraido.nomeEmpresa() != null) {
				Empresa emp = resolverEmpresa(extraido.nomeEmpresa(), agora);
				if (emp != null) {
					op.setEmpresa(emp);
					op.setNomeEmpresa(emp.getNome());
				}
			}
			if (op.getCodigoRequisicao() == null && extraido.codigoRequisicao() != null) {
				op.setCodigoRequisicao(extraido.codigoRequisicao());
			}
			if (extraido.disponibilidadeObservada() == DisponibilidadeVaga.DISPONIVEL) {
				op.setDisponibilidadeConsolidada(DisponibilidadeVaga.DISPONIVEL);
				op.setMotivoDisponibilidade(null);
			}
			oportunidadeRepository.save(op);
		} else {
			op = new Oportunidade();
			Empresa empresa = resolverEmpresa(extraido.nomeEmpresa(), agora);
			op.setEmpresa(empresa);
			op.setNomeEmpresa(empresa != null ? empresa.getNome() : null);
			op.setCargo(extraido.cargo());
			op.setDescricao(extraido.descricaoLimpa());
			op.setArea(extraido.area());
			op.setSenioridade(extraido.senioridade());
			op.setTipoOportunidade(extraido.tipoOportunidade());
			op.setRegimeContratacao(extraido.regimeContratacao());
			op.setModalidade(extraido.modalidade());
			op.setLocalizacao(extraido.localizacao());
			op.setElegivelBrasil(extraido.elegivelBrasil());
			op.setTecnologias(juntarLista(extraido.tecnologias()));
			op.setDisponibilidadeConsolidada(extraido.disponibilidadeObservada());
			op.setMotivoDisponibilidade(extraido.motivoIndisponibilidade());
			op.setLinkCandidatura(extraido.urlCandidatura());
			op.setLinkOrigemPrincipal(extraido.urlOrigem());
			op.setFontePrincipal(extraido.fornecedor());
			op.setMuralPrincipal(extraido.identificadorMural());
			op.setIdentificadorExternoPrincipal(extraido.identificadorExterno());
			op.setCodigoRequisicao(extraido.codigoRequisicao());
			// Nunca substitui publicação desconhecida (null) por agora nem por updatedAt!
			op.setPublicadoEm(extraido.publicadoNaFonteEm());
			op.setPrimeiraObservacaoEm(agora);
			op.setUltimaObservacaoEm(agora);
			op.setAtualizadoNaFonteEm(extraido.atualizadoNaFonteEm());
			op.setChaveDeduplicacao(chaveDedup);
			op = oportunidadeRepository.save(op);
		}

		AnuncioOrigem anuncio = new AnuncioOrigem();
		anuncio.setOportunidade(op);
		anuncio.setFornecedor(extraido.fornecedor());
		anuncio.setIdentificadorMural(extraido.identificadorMural());
		anuncio.setIdentificadorExterno(extraido.identificadorExterno());
		anuncio.setCodigoRequisicao(extraido.codigoRequisicao());
		anuncio.setTituloOriginal(extraido.tituloOriginal());
		anuncio.setCorpoOriginal(extraido.corpoOriginal());
		anuncio.setLabelsOriginais(juntarLista(extraido.labelsOriginais()));
		anuncio.setUrlOrigem(extraido.urlOrigem());
		anuncio.setUrlCandidatura(extraido.urlCandidatura());
		anuncio.setDisponibilidadeObservada(extraido.disponibilidadeObservada());
		anuncio.setMotivoIndisponibilidade(extraido.motivoIndisponibilidade());
		anuncio.setPublicadoNaFonteEm(extraido.publicadoNaFonteEm());
		anuncio.setAtualizadoNaFonteEm(extraido.atualizadoNaFonteEm());
		anuncio.setPrimeiraObservacaoEm(agora);
		anuncio.setUltimaObservacaoEm(agora);
		anuncioOrigemRepository.save(anuncio);

		return ResultadoPersistenciaAnuncio.NOVO;
	}

	/**
	 * Localiza oportunidade existente com evidência forte entre fontes:
	 * 1) Mesma chave de deduplicação (ex: link de candidatura equivalente após normalização segura)
	 * 2) Mesmo código de requisição explícito na mesma empresa identificada.
	 * Mesmo cargo e empresa sem código/link equivalente NÃO são unidos.
	 */
	private Optional<Oportunidade> localizarOportunidadeCorrespondente(AnuncioExtraido extraido, String chaveDedup) {
		Optional<Oportunidade> porChave = oportunidadeRepository.findByChaveDeduplicacao(chaveDedup);
		if (porChave.isPresent()) {
			return porChave;
		}
		if (extraido.nomeEmpresa() != null && !extraido.nomeEmpresa().isBlank()
				&& extraido.codigoRequisicao() != null && !extraido.codigoRequisicao().isBlank()) {
			return oportunidadeRepository.findFirstByNomeEmpresaIgnoreCaseAndCodigoRequisicaoIgnoreCase(
					extraido.nomeEmpresa().trim(),
					extraido.codigoRequisicao().trim()
			);
		}
		return Optional.empty();
	}

	/**
	 * Agrupamento conservador: une oportunidades entre fontes quando compartilham um link específico de candidatura
	 * (diferente de uma issue do GitHub) após normalização segura.
	 */
	private String calcularChaveDeduplicacaoConservadora(AnuncioExtraido extraido) {
		String urlCand = extraido.urlCandidatura();
		if (urlCand != null && ehLinkCandidaturaEspecificoDeVaga(urlCand)) {
			return "LINK_CAND:" + normalizarLinkParaDeduplicacao(urlCand);
		}
		return "ORIGEM:" + extraido.fornecedor() + ":" + extraido.identificadorMural() + ":" + extraido.identificadorExterno();
	}

	private String normalizarLinkParaDeduplicacao(String urlValidada) {
		String norm = urlValidada.trim();
		if (norm.endsWith("/")) {
			norm = norm.substring(0, norm.length() - 1);
		}
		if (norm.endsWith("/application")) {
			norm = norm.substring(0, norm.length() - "/application".length());
		}
		return norm;
	}

	private boolean ehLinkCandidaturaEspecificoDeVaga(String url) {
		try {
			URI uri = URI.create(url);
			String host = uri.getHost() != null ? uri.getHost().toLowerCase(Locale.ROOT) : "";
			if (host.contains("github.com")) {
				return false;
			}
			String path = uri.getPath();
			String query = uri.getQuery();
			boolean temPathEspecifico = path != null && path.length() > 6 && !path.equals("/")
					&& !path.equalsIgnoreCase("/vagas") && !path.equalsIgnoreCase("/careers") && !path.equalsIgnoreCase("/jobs");
			boolean temQueryIdentificadora = query != null && !query.isBlank();
			return temPathEspecifico || temQueryIdentificadora;
		} catch (Exception e) {
			return false;
		}
	}

	private Empresa resolverEmpresa(String nomeEmpresa, Instant agora) {
		if (nomeEmpresa == null || nomeEmpresa.isBlank()) {
			return null;
		}
		String normalizado = nomeEmpresa.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
		return empresaRepository.findByNomeNormalizado(normalizado)
				.orElseGet(() -> {
					Empresa nova = new Empresa();
					nova.setNome(nomeEmpresa.trim());
					nova.setNomeNormalizado(normalizado);
					nova.setCriadoEm(agora);
					nova.setAtualizadoEm(agora);
					return empresaRepository.save(nova);
				});
	}

	@Transactional(readOnly = true)
	public PaginaVagasDto listarVagas(
			int pagina,
			int tamanho,
			String termo,
			String fornecedor,
			String modoPublicacao,
			boolean incluirIndisponiveis,
			boolean bancoTalentos
	) {
		int paginaSegura = Math.max(0, pagina);
		int tamanhoSeguro = Math.max(1, Math.min(50, tamanho));
		String modoPubSeguro = validarModoPublicacao(modoPublicacao);
		TipoOportunidade tipo = bancoTalentos ? TipoOportunidade.BANCO_DE_TALENTOS : TipoOportunidade.VAGA_ABERTA;
		String termoLimpo = (termo == null || termo.isBlank()) ? null : termo.trim();
		String fornecedorLimpo = (fornecedor == null || fornecedor.isBlank() || "TODAS".equalsIgnoreCase(fornecedor))
				? null
				: fornecedor.trim();

		Page<Oportunidade> pageResult = oportunidadeRepository.consultarCatalogoPaginado(
				tipo,
				incluirIndisponiveis,
				fornecedorLimpo,
				modoPubSeguro,
				termoLimpo,
				PageRequest.of(paginaSegura, tamanhoSeguro)
		);

		List<Long> ids = pageResult.getContent().stream().map(Oportunidade::getId).toList();
		Map<Long, AcaoPessoal> acoesPorOportunidade = ids.isEmpty()
				? Map.of()
				: acaoPessoalRepository.findByOportunidadeIdIn(ids).stream()
						.collect(Collectors.toMap(a -> a.getOportunidade().getId(), Function.identity()));

		Map<Long, Long> contagemAnunciosPorOportunidade = ids.isEmpty()
				? Map.of()
				: anuncioOrigemRepository.findByOportunidadeIdIn(ids).stream()
						.collect(Collectors.groupingBy(a -> a.getOportunidade().getId(), Collectors.counting()));

		List<VagaResumoDto> itens = pageResult.getContent().stream()
				.map(o -> mapearParaResumo(
						o,
						acoesPorOportunidade.get(o.getId()),
						contagemAnunciosPorOportunidade.getOrDefault(o.getId(), 1L).intValue()
				))
				.toList();

		return new PaginaVagasDto(
				itens,
				pageResult.getNumber(),
				pageResult.getSize(),
				pageResult.getTotalElements(),
				pageResult.getTotalPages(),
				pageResult.hasNext(),
				pageResult.hasPrevious()
		);
	}

	@Transactional(readOnly = true)
	public PaginaVagasDto listarVagas(
			int pagina,
			int tamanho,
			String termo,
			String modoPublicacao,
			boolean incluirIndisponiveis,
			boolean bancoTalentos
	) {
		return listarVagas(pagina, tamanho, termo, null, modoPublicacao, incluirIndisponiveis, bancoTalentos);
	}

	@Transactional(readOnly = true)
	public Optional<VagaDetalheDto> buscarDetalhe(Long id) {
		return oportunidadeRepository.findById(id).map(o -> {
			AcaoPessoal acao = acaoPessoalRepository.findByOportunidadeId(o.getId()).orElse(null);
			List<AnuncioOrigemDto> anuncios = anuncioOrigemRepository
					.findByOportunidadeIdOrderByPrimeiraObservacaoEmAsc(o.getId())
					.stream()
					.map(this::mapearAnuncioDto)
					.toList();
			return new VagaDetalheDto(
					o.getId(),
					o.getCargo(),
					o.getNomeEmpresa(),
					o.getDescricao(),
					o.getLocalizacao(),
					o.getModalidade().name(),
					o.getModalidade().getRotulo(),
					o.getSenioridade().name(),
					o.getSenioridade().getRotulo(),
					o.getRegimeContratacao().name(),
					o.getRegimeContratacao().getRotulo(),
					o.getArea().name(),
					o.getArea().getRotulo(),
					o.getTipoOportunidade().name(),
					o.getTipoOportunidade().getRotulo(),
					o.getDisponibilidadeConsolidada().name(),
					o.getDisponibilidadeConsolidada().getRotulo(),
					o.getMotivoDisponibilidade(),
					separarLista(o.getTecnologias()),
					o.getPublicadoEm(),
					o.getPublicadoEm() != null,
					o.getPrimeiraObservacaoEm(),
					o.getUltimaObservacaoEm(),
					o.getAtualizadoNaFonteEm(),
					o.getFontePrincipal(),
					o.getMuralPrincipal(),
					o.getIdentificadorExternoPrincipal(),
					o.getLinkCandidatura() != null ? o.getLinkCandidatura() : o.getLinkOrigemPrincipal(),
					o.getLinkOrigemPrincipal(),
					acao != null && acao.isJaMeCandidatei(),
					acao != null && acao.isOculta(),
					anuncios
			);
		});
	}

	private VagaResumoDto mapearParaResumo(Oportunidade o, AcaoPessoal acao, int qtdAnuncios) {
		return new VagaResumoDto(
				o.getId(),
				o.getCargo(),
				o.getNomeEmpresa(),
				o.getLocalizacao(),
				o.getModalidade().name(),
				o.getModalidade().getRotulo(),
				o.getSenioridade().name(),
				o.getSenioridade().getRotulo(),
				o.getRegimeContratacao().name(),
				o.getRegimeContratacao().getRotulo(),
				o.getArea().name(),
				o.getArea().getRotulo(),
				o.getTipoOportunidade().name(),
				o.getTipoOportunidade().getRotulo(),
				o.getDisponibilidadeConsolidada().name(),
				o.getDisponibilidadeConsolidada().getRotulo(),
				o.getMotivoDisponibilidade(),
				separarLista(o.getTecnologias()),
				o.getPublicadoEm(),
				o.getPublicadoEm() != null,
				o.getPrimeiraObservacaoEm(),
				o.getUltimaObservacaoEm(),
				o.getAtualizadoNaFonteEm(),
				o.getFontePrincipal(),
				o.getMuralPrincipal(),
				o.getIdentificadorExternoPrincipal(),
				o.getLinkCandidatura() != null ? o.getLinkCandidatura() : o.getLinkOrigemPrincipal(),
				o.getLinkOrigemPrincipal(),
				acao != null && acao.isJaMeCandidatei(),
				acao != null && acao.isOculta(),
				qtdAnuncios
		);
	}

	private AnuncioOrigemDto mapearAnuncioDto(AnuncioOrigem a) {
		return new AnuncioOrigemDto(
				a.getId(),
				a.getFornecedor(),
				a.getIdentificadorMural(),
				a.getIdentificadorExterno(),
				a.getTituloOriginal(),
				separarLista(a.getLabelsOriginais()),
				a.getUrlOrigem(),
				a.getUrlCandidatura() != null ? a.getUrlCandidatura() : a.getUrlOrigem(),
				a.getDisponibilidadeObservada().name(),
				a.getDisponibilidadeObservada().getRotulo(),
				a.getMotivoIndisponibilidade(),
				a.getPublicadoNaFonteEm(),
				a.getAtualizadoNaFonteEm(),
				a.getPrimeiraObservacaoEm(),
				a.getUltimaObservacaoEm()
		);
	}

	private String validarModoPublicacao(String modo) {
		if (modo == null) {
			return "TODAS";
		}
		String upper = modo.trim().toUpperCase(Locale.ROOT);
		if ("CONHECIDA".equals(upper) || "DESCONHECIDA".equals(upper)) {
			return upper;
		}
		return "TODAS";
	}

	private String juntarLista(List<String> itens) {
		if (itens == null || itens.isEmpty()) {
			return null;
		}
		return itens.stream()
				.filter(s -> s != null && !s.isBlank())
				.map(String::trim)
				.distinct()
				.collect(Collectors.joining(","));
	}

	private List<String> separarLista(String valor) {
		if (valor == null || valor.isBlank()) {
			return List.of();
		}
		return Arrays.stream(valor.split(","))
				.map(String::trim)
				.filter(s -> !s.isBlank())
				.toList();
	}
}

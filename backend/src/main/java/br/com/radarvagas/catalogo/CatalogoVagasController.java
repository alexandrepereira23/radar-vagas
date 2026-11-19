package br.com.radarvagas.catalogo;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vagas")
public class CatalogoVagasController {

	private final CatalogoVagasService catalogoVagasService;

	public CatalogoVagasController(CatalogoVagasService catalogoVagasService) {
		this.catalogoVagasService = catalogoVagasService;
	}

	/**
	 * Lista vagas paginadas a partir exclusivamente do banco local.
	 * Nunca inicia uma coleta externa por visita à página.
	 */
	@GetMapping
	public PaginaVagasDto listar(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "15") int size,
			@RequestParam(required = false) String q,
			@RequestParam(required = false) String fonte,
			@RequestParam(defaultValue = "TODAS") String publicacao,
			@RequestParam(defaultValue = "false") boolean incluirIndisponiveis,
			@RequestParam(defaultValue = "false") boolean bancoTalentos,
			@RequestParam(required = false) String area,
			@RequestParam(required = false) String senioridade,
			@RequestParam(required = false) String modalidade,
			@RequestParam(required = false) String tecnologia,
			@RequestParam(required = false) String localizacao,
			@RequestParam(required = false) String candidatura,
			@RequestParam(required = false) String ocultacao
	) {
		return catalogoVagasService.listarVagas(page, size, FiltrosCatalogo.de(q, fonte, publicacao,
				incluirIndisponiveis, bancoTalentos, area, senioridade, modalidade, tecnologia, localizacao, candidatura, ocultacao));
	}

	@GetMapping("/opcoes")
	public OpcoesCatalogoDto opcoes() {
		return catalogoVagasService.opcoes();
	}

	@GetMapping("/{id}")
	public ResponseEntity<VagaDetalheDto> detalhar(@PathVariable Long id) {
		return catalogoVagasService.buscarDetalhe(id)
				.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.notFound().build());
	}
}

package br.com.radarvagas.coleta;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/coletas")
public class ColetaController {

	private final OrquestradorColetaService orquestradorColetaService;

	public ColetaController(OrquestradorColetaService orquestradorColetaService) {
		this.orquestradorColetaService = orquestradorColetaService;
	}

	@GetMapping("/status")
	public StatusColetaGlobalDto consultarStatus() {
		return orquestradorColetaService.consultarStatusGlobal();
	}

	@PostMapping("/executar")
	public List<ExecucaoColetaDto> executarFontesAtivas() {
		return orquestradorColetaService.executarFontesAtivas();
	}

	@PostMapping("/executar/{idFonte}")
	public ResponseEntity<ExecucaoColetaDto> executarFonteEspecifica(@PathVariable Long idFonte) {
		return orquestradorColetaService.executarFontePorId(idFonte)
				.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.notFound().build());
	}
}

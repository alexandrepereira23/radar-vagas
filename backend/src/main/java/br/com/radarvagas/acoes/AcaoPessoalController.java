package br.com.radarvagas.acoes;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

@RestController
public class AcaoPessoalController {
	private final AcaoPessoalService service;

	public AcaoPessoalController(AcaoPessoalService service) {
		this.service = service;
	}

	@PatchMapping(path = "/api/vagas/{id}/acao-pessoal", consumes = "application/json")
	public AcaoPessoalService.Estado atualizar(@PathVariable Long id, @RequestBody JsonNode corpo) {
		if (!corpo.isObject() || corpo.isEmpty()) {
			throw invalido();
		}
		for (var campo : corpo.properties()) {
			if ((!campo.getKey().equals("jaMeCandidatei") && !campo.getKey().equals("oculta"))
					|| !campo.getValue().isBoolean()) {
				throw invalido();
			}
		}
		return service.atualizar(id, estado(corpo, "jaMeCandidatei"), estado(corpo, "oculta"));
	}

	private static Boolean estado(JsonNode corpo, String campo) {
		return corpo.has(campo) ? corpo.get(campo).asBoolean() : null;
	}

	private static ResponseStatusException invalido() {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST,
				"Informe jaMeCandidatei e/ou oculta com valores booleanos true ou false.");
	}
}

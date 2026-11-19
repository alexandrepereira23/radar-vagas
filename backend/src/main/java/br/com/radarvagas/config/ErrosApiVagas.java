package br.com.radarvagas.config;

import br.com.radarvagas.acoes.AcaoPessoalController;
import br.com.radarvagas.catalogo.CatalogoVagasController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = {CatalogoVagasController.class, AcaoPessoalController.class})
public class ErrosApiVagas {
	@ExceptionHandler(ResponseStatusException.class)
	ProblemDetail rejeitada(ResponseStatusException erro) {
		return ProblemDetail.forStatusAndDetail(erro.getStatusCode(), erro.getReason());
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ProblemDetail parametroInvalido(MethodArgumentTypeMismatchException erro) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Valor inválido para " + erro.getName() + ".");
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ProblemDetail corpoInvalido() {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Informe um corpo JSON válido para a ação pessoal.");
	}
}

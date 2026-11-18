package br.com.radarvagas.coleta;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExecucaoColetaRepository extends JpaRepository<ExecucaoColeta, Long> {

	List<ExecucaoColeta> findTop20ByOrderByIniciadoEmDesc();

	List<ExecucaoColeta> findByFornecedorAndIdentificadorMuralAndStatus(
			String fornecedor,
			String identificadorMural,
			StatusExecucaoColeta status
	);
}

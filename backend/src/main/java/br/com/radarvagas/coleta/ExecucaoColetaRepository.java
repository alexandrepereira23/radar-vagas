package br.com.radarvagas.coleta;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExecucaoColetaRepository extends JpaRepository<ExecucaoColeta, Long> {

	List<ExecucaoColeta> findTop20ByOrderByIniciadoEmDesc();

	Optional<ExecucaoColeta> findFirstByFornecedorAndIdentificadorMuralOrderByIniciadoEmDesc(
			String fornecedor,
			String identificadorMural
	);

	List<ExecucaoColeta> findByFornecedorAndIdentificadorMuralAndStatus(
			String fornecedor,
			String identificadorMural,
			StatusExecucaoColeta status
	);
}

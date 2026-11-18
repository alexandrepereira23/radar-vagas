package br.com.radarvagas.coleta;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfiguracaoFonteRepository extends JpaRepository<ConfiguracaoFonte, Long> {

	Optional<ConfiguracaoFonte> findByFornecedorAndIdentificadorMural(String fornecedor, String identificadorMural);

	List<ConfiguracaoFonte> findByAtivoTrueOrderByIdAsc();

	List<ConfiguracaoFonte> findAllByOrderByIdAsc();
}

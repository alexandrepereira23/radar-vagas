package br.com.radarvagas.catalogo;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnuncioOrigemRepository extends JpaRepository<AnuncioOrigem, Long> {

	Optional<AnuncioOrigem> findByFornecedorAndIdentificadorMuralAndIdentificadorExterno(
			String fornecedor,
			String identificadorMural,
			String identificadorExterno
	);

	List<AnuncioOrigem> findByOportunidadeIdOrderByPrimeiraObservacaoEmAsc(Long oportunidadeId);

	List<AnuncioOrigem> findByOportunidadeIdIn(Collection<Long> oportunidadeIds);
}

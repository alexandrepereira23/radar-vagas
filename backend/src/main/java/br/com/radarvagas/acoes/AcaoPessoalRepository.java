package br.com.radarvagas.acoes;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AcaoPessoalRepository extends JpaRepository<AcaoPessoal, Long> {

	Optional<AcaoPessoal> findByOportunidadeId(Long oportunidadeId);

	List<AcaoPessoal> findByOportunidadeIdIn(Collection<Long> oportunidadeIds);
}

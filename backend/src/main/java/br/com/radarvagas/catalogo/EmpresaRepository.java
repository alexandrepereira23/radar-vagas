package br.com.radarvagas.catalogo;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmpresaRepository extends JpaRepository<Empresa, Long> {

	Optional<Empresa> findByNomeNormalizado(String nomeNormalizado);
}

package br.com.radarvagas.catalogo;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OportunidadeRepository extends JpaRepository<Oportunidade, Long>, JpaSpecificationExecutor<Oportunidade> {

	Optional<Oportunidade> findByChaveDeduplicacao(String chaveDeduplicacao);

	Optional<Oportunidade> findFirstByNomeEmpresaIgnoreCaseAndCodigoRequisicaoIgnoreCase(
			String nomeEmpresa, String codigoRequisicao);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select o from Oportunidade o where o.id = :id")
	Optional<Oportunidade> bloquearPorId(@Param("id") Long id);

	@Query("select distinct o.tecnologias from Oportunidade o where o.tecnologias is not null")
	List<String> combinacoesTecnologias();

	@Query("select distinct o.fontePrincipal from Oportunidade o")
	List<String> fontesPrincipais();

	boolean existsByIdIsNotNull();
}

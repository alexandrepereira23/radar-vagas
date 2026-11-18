package br.com.radarvagas.catalogo;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OportunidadeRepository extends JpaRepository<Oportunidade, Long> {

	Optional<Oportunidade> findByChaveDeduplicacao(String chaveDeduplicacao);

	Optional<Oportunidade> findFirstByNomeEmpresaIgnoreCaseAndCodigoRequisicaoIgnoreCase(
			String nomeEmpresa,
			String codigoRequisicao
	);

	@Query(
			value = """
					SELECT o FROM Oportunidade o
					WHERE o.elegivelBrasil = true
					  AND o.tipoOportunidade = :tipoOportunidade
					  AND (:incluirIndisponiveis = true OR o.disponibilidadeConsolidada = br.com.radarvagas.catalogo.DisponibilidadeVaga.DISPONIVEL)
					  AND (:fornecedor IS NULL OR :fornecedor = ''
					       OR o.fontePrincipal = :fornecedor
					       OR EXISTS (SELECT 1 FROM AnuncioOrigem ao WHERE ao.oportunidade = o AND ao.fornecedor = :fornecedor))
					  AND (:modoPublicacao = 'TODAS'
					       OR (:modoPublicacao = 'CONHECIDA' AND o.publicadoEm IS NOT NULL)
					       OR (:modoPublicacao = 'DESCONHECIDA' AND o.publicadoEm IS NULL))
					  AND (:termo IS NULL OR :termo = ''
					       OR LOWER(o.cargo) LIKE LOWER(CONCAT('%', :termo, '%'))
					       OR LOWER(COALESCE(o.nomeEmpresa, '')) LIKE LOWER(CONCAT('%', :termo, '%'))
					       OR LOWER(COALESCE(o.tecnologias, '')) LIKE LOWER(CONCAT('%', :termo, '%'))
					       OR LOWER(COALESCE(o.localizacao, '')) LIKE LOWER(CONCAT('%', :termo, '%')))
					ORDER BY
					  CASE WHEN o.publicadoEm IS NULL THEN 1 ELSE 0 END ASC,
					  o.publicadoEm DESC,
					  o.primeiraObservacaoEm DESC,
					  o.id DESC
					""",
			countQuery = """
					SELECT COUNT(o) FROM Oportunidade o
					WHERE o.elegivelBrasil = true
					  AND o.tipoOportunidade = :tipoOportunidade
					  AND (:incluirIndisponiveis = true OR o.disponibilidadeConsolidada = br.com.radarvagas.catalogo.DisponibilidadeVaga.DISPONIVEL)
					  AND (:fornecedor IS NULL OR :fornecedor = ''
					       OR o.fontePrincipal = :fornecedor
					       OR EXISTS (SELECT 1 FROM AnuncioOrigem ao WHERE ao.oportunidade = o AND ao.fornecedor = :fornecedor))
					  AND (:modoPublicacao = 'TODAS'
					       OR (:modoPublicacao = 'CONHECIDA' AND o.publicadoEm IS NOT NULL)
					       OR (:modoPublicacao = 'DESCONHECIDA' AND o.publicadoEm IS NULL))
					  AND (:termo IS NULL OR :termo = ''
					       OR LOWER(o.cargo) LIKE LOWER(CONCAT('%', :termo, '%'))
					       OR LOWER(COALESCE(o.nomeEmpresa, '')) LIKE LOWER(CONCAT('%', :termo, '%'))
					       OR LOWER(COALESCE(o.tecnologias, '')) LIKE LOWER(CONCAT('%', :termo, '%'))
					       OR LOWER(COALESCE(o.localizacao, '')) LIKE LOWER(CONCAT('%', :termo, '%')))
					"""
	)
	Page<Oportunidade> consultarCatalogoPaginado(
			@Param("tipoOportunidade") TipoOportunidade tipoOportunidade,
			@Param("incluirIndisponiveis") boolean incluirIndisponiveis,
			@Param("fornecedor") String fornecedor,
			@Param("modoPublicacao") String modoPublicacao,
			@Param("termo") String termo,
			Pageable pageable
	);
}

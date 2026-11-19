package br.com.radarvagas.catalogo;

import br.com.radarvagas.acoes.AcaoPessoal;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

final class ConsultaCatalogo {
	private ConsultaCatalogo() {}

	static Specification<Oportunidade> filtrada(FiltrosCatalogo filtros, Instant agora) {
		return (vaga, consulta, cb) -> {
			var regras = new ArrayList<Predicate>();
			regras.add(cb.isTrue(vaga.get("elegivelBrasil")));
			regras.add(cb.equal(vaga.get("tipoOportunidade"), filtros.bancoTalentos()
					? TipoOportunidade.BANCO_DE_TALENTOS : TipoOportunidade.VAGA_ABERTA));
			if (!filtros.incluirIndisponiveis()) {
				regras.add(cb.equal(vaga.get("disponibilidadeConsolidada"), DisponibilidadeVaga.DISPONIVEL));
			}
			if (filtros.fonte() != null) {
				var origens = consulta.subquery(Integer.class);
				var anuncio = origens.from(AnuncioOrigem.class);
				origens.select(cb.literal(1)).where(cb.equal(anuncio.get("oportunidade"), vaga),
						cb.equal(anuncio.get("fornecedor"), filtros.fonte()));
				regras.add(cb.or(cb.equal(vaga.get("fontePrincipal"), filtros.fonte()), cb.exists(origens)));
			}

			var publicado = vaga.<Instant>get("publicadoEm");
			switch (filtros.publicacao()) {
				case TODAS -> { }
				case CONHECIDA -> regras.add(cb.isNotNull(publicado));
				case DESCONHECIDA -> regras.add(cb.isNull(publicado));
				default -> regras.add(cb.between(publicado, agora.minus(filtros.publicacao().horas, ChronoUnit.HOURS), agora));
			}

			if (filtros.termo() != null) {
				// Mantém o comportamento da busca livre anterior, incluindo curingas LIKE.
				String termo = "%" + filtros.termo().toLowerCase(Locale.ROOT) + "%";
				regras.add(cb.or(cb.like(cb.lower(vaga.get("cargo")), termo),
						cb.like(cb.lower(vaga.get("nomeEmpresa")), termo), cb.like(cb.lower(vaga.get("tecnologias")), termo),
						cb.like(cb.lower(vaga.get("localizacao")), termo)));
			}
			if (filtros.area() != null) regras.add(cb.equal(vaga.get("area"), filtros.area()));
			if (filtros.senioridade() != null) regras.add(cb.equal(vaga.get("senioridade"), filtros.senioridade()));
			if (filtros.modalidade() != null) regras.add(cb.equal(vaga.get("modalidade"), filtros.modalidade()));
			if (filtros.localizacao() != null) {
				regras.add(cb.gt(cb.locate(cb.lower(vaga.get("localizacao")), filtros.localizacao().toLowerCase(Locale.ROOT)), 0));
			}
			if (filtros.tecnologia() != null) {
				// juntarLista grava tokens aparados separados só por vírgulas. LOCATE é literal, não LIKE/regex.
				var lista = cb.concat(cb.concat(",", cb.lower(vaga.get("tecnologias"))), ",");
				regras.add(cb.gt(cb.locate(lista, "," + filtros.tecnologia().toLowerCase(Locale.ROOT) + ","), 0));
			}

			switch (filtros.candidatura()) {
				case TODAS -> { }
				case CANDIDATADAS -> regras.add(acaoVerdadeira(vaga, consulta, cb, "jaMeCandidatei"));
				case NAO_CANDIDATADAS -> regras.add(cb.not(acaoVerdadeira(vaga, consulta, cb, "jaMeCandidatei")));
			}
			switch (filtros.ocultacao()) {
				case INCLUIR -> { }
				case SOMENTE -> regras.add(acaoVerdadeira(vaga, consulta, cb, "oculta"));
				case EXCLUIR -> regras.add(cb.not(acaoVerdadeira(vaga, consulta, cb, "oculta")));
			}

			if (consulta.getResultType() == Oportunidade.class) {
				consulta.orderBy(cb.asc(cb.<Integer>selectCase().when(cb.isNull(publicado), 1).otherwise(0)),
						cb.desc(publicado), cb.desc(vaga.get("primeiraObservacaoEm")), cb.desc(vaga.get("id")));
			}
			return cb.and(regras.toArray(Predicate[]::new));
		};
	}

	private static Predicate acaoVerdadeira(Root<Oportunidade> vaga, CriteriaQuery<?> consulta, CriteriaBuilder cb, String campo) {
		var acoes = consulta.subquery(Integer.class);
		var acao = acoes.from(AcaoPessoal.class);
		acoes.select(cb.literal(1)).where(cb.equal(acao.get("oportunidade"), vaga), cb.isTrue(acao.get(campo)));
		return cb.exists(acoes);
	}
}

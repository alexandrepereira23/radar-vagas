package br.com.radarvagas.coleta;

import br.com.radarvagas.catalogo.AreaTecnologia;
import br.com.radarvagas.catalogo.DisponibilidadeVaga;
import br.com.radarvagas.catalogo.ModalidadeTrabalho;
import br.com.radarvagas.catalogo.RegimeContratacao;
import br.com.radarvagas.catalogo.Senioridade;
import br.com.radarvagas.catalogo.TipoOportunidade;
import java.time.Instant;
import java.util.List;

public record AnuncioExtraido(
		String fornecedor,
		String identificadorMural,
		String identificadorExterno,
		String tituloOriginal,
		String corpoOriginal,
		List<String> labelsOriginais,
		String cargo,
		String nomeEmpresa,
		String descricaoLimpa,
		AreaTecnologia area,
		Senioridade senioridade,
		TipoOportunidade tipoOportunidade,
		RegimeContratacao regimeContratacao,
		ModalidadeTrabalho modalidade,
		String localizacao,
		boolean elegivelBrasil,
		List<String> tecnologias,
		DisponibilidadeVaga disponibilidadeObservada,
		String motivoIndisponibilidade,
		String urlOrigem,
		String urlCandidatura,
		Instant publicadoNaFonteEm,
		Instant atualizadoNaFonteEm,
		String codigoRequisicao
) {
	public AnuncioExtraido(
			String fornecedor,
			String identificadorMural,
			String identificadorExterno,
			String tituloOriginal,
			String corpoOriginal,
			List<String> labelsOriginais,
			String cargo,
			String nomeEmpresa,
			String descricaoLimpa,
			AreaTecnologia area,
			Senioridade senioridade,
			TipoOportunidade tipoOportunidade,
			RegimeContratacao regimeContratacao,
			ModalidadeTrabalho modalidade,
			String localizacao,
			boolean elegivelBrasil,
			List<String> tecnologias,
			DisponibilidadeVaga disponibilidadeObservada,
			String motivoIndisponibilidade,
			String urlOrigem,
			String urlCandidatura,
			Instant publicadoNaFonteEm,
			Instant atualizadoNaFonteEm
	) {
		this(
				fornecedor,
				identificadorMural,
				identificadorExterno,
				tituloOriginal,
				corpoOriginal,
				labelsOriginais,
				cargo,
				nomeEmpresa,
				descricaoLimpa,
				area,
				senioridade,
				tipoOportunidade,
				regimeContratacao,
				modalidade,
				localizacao,
				elegivelBrasil,
				tecnologias,
				disponibilidadeObservada,
				motivoIndisponibilidade,
				urlOrigem,
				urlCandidatura,
				publicadoNaFonteEm,
				atualizadoNaFonteEm,
				null
		);
	}
}

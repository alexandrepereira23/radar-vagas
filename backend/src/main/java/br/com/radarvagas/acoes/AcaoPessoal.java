package br.com.radarvagas.acoes;

import br.com.radarvagas.catalogo.Oportunidade;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "acao_pessoal")
public class AcaoPessoal {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "oportunidade_id", nullable = false, unique = true)
	private Oportunidade oportunidade;

	@Column(name = "ja_me_candidatei", nullable = false)
	private boolean jaMeCandidatei;

	@Column(name = "oculta", nullable = false)
	private boolean oculta;

	@Column(name = "candidatado_em")
	private Instant candidatadoEm;

	@Column(name = "ocultado_em")
	private Instant ocultadoEm;

	@Column(name = "atualizado_em", nullable = false)
	private Instant atualizadoEm;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Oportunidade getOportunidade() {
		return oportunidade;
	}

	public void setOportunidade(Oportunidade oportunidade) {
		this.oportunidade = oportunidade;
	}

	public boolean isJaMeCandidatei() {
		return jaMeCandidatei;
	}

	public void setJaMeCandidatei(boolean jaMeCandidatei) {
		this.jaMeCandidatei = jaMeCandidatei;
	}

	public boolean isOculta() {
		return oculta;
	}

	public void setOculta(boolean oculta) {
		this.oculta = oculta;
	}

	public Instant getCandidatadoEm() {
		return candidatadoEm;
	}

	public void setCandidatadoEm(Instant candidatadoEm) {
		this.candidatadoEm = candidatadoEm;
	}

	public Instant getOcultadoEm() {
		return ocultadoEm;
	}

	public void setOcultadoEm(Instant ocultadoEm) {
		this.ocultadoEm = ocultadoEm;
	}

	public Instant getAtualizadoEm() {
		return atualizadoEm;
	}

	public void setAtualizadoEm(Instant atualizadoEm) {
		this.atualizadoEm = atualizadoEm;
	}
}

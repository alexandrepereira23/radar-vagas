package br.com.radarvagas.catalogo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
		name = "anuncio_origem",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_anuncio_origem_identidade",
				columnNames = {"fornecedor", "identificador_mural", "identificador_externo"}
		)
)
public class AnuncioOrigem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "oportunidade_id", nullable = false)
	private Oportunidade oportunidade;

	@Column(name = "fornecedor", nullable = false, length = 100)
	private String fornecedor;

	@Column(name = "identificador_mural", nullable = false, length = 200)
	private String identificadorMural;

	@Column(name = "identificador_externo", nullable = false, length = 200)
	private String identificadorExterno;

	@Column(name = "codigo_requisicao", length = 150)
	private String codigoRequisicao;

	@Column(name = "titulo_original", nullable = false, length = 500)
	private String tituloOriginal;

	@Column(name = "corpo_original", nullable = false, columnDefinition = "TEXT")
	private String corpoOriginal;

	@Column(name = "labels_originais", length = 1000)
	private String labelsOriginais;

	@Column(name = "url_origem", nullable = false, length = 1000)
	private String urlOrigem;

	@Column(name = "url_candidatura", length = 1000)
	private String urlCandidatura;

	@Enumerated(EnumType.STRING)
	@Column(name = "disponibilidade_observada", nullable = false, length = 50)
	private DisponibilidadeVaga disponibilidadeObservada = DisponibilidadeVaga.DISPONIVEL;

	@Column(name = "motivo_indisponibilidade", length = 500)
	private String motivoIndisponibilidade;

	@Column(name = "publicado_na_fonte_em")
	private Instant publicadoNaFonteEm;

	@Column(name = "atualizado_na_fonte_em")
	private Instant atualizadoNaFonteEm;

	@Column(name = "primeira_observacao_em", nullable = false)
	private Instant primeiraObservacaoEm;

	@Column(name = "ultima_observacao_em", nullable = false)
	private Instant ultimaObservacaoEm;

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

	public String getFornecedor() {
		return fornecedor;
	}

	public void setFornecedor(String fornecedor) {
		this.fornecedor = fornecedor;
	}

	public String getIdentificadorMural() {
		return identificadorMural;
	}

	public void setIdentificadorMural(String identificadorMural) {
		this.identificadorMural = identificadorMural;
	}

	public String getIdentificadorExterno() {
		return identificadorExterno;
	}

	public void setIdentificadorExterno(String identificadorExterno) {
		this.identificadorExterno = identificadorExterno;
	}

	public String getCodigoRequisicao() {
		return codigoRequisicao;
	}

	public void setCodigoRequisicao(String codigoRequisicao) {
		this.codigoRequisicao = codigoRequisicao;
	}

	public String getTituloOriginal() {
		return tituloOriginal;
	}

	public void setTituloOriginal(String tituloOriginal) {
		this.tituloOriginal = tituloOriginal;
	}

	public String getCorpoOriginal() {
		return corpoOriginal;
	}

	public void setCorpoOriginal(String corpoOriginal) {
		this.corpoOriginal = corpoOriginal;
	}

	public String getLabelsOriginais() {
		return labelsOriginais;
	}

	public void setLabelsOriginais(String labelsOriginais) {
		this.labelsOriginais = labelsOriginais;
	}

	public String getUrlOrigem() {
		return urlOrigem;
	}

	public void setUrlOrigem(String urlOrigem) {
		this.urlOrigem = urlOrigem;
	}

	public String getUrlCandidatura() {
		return urlCandidatura;
	}

	public void setUrlCandidatura(String urlCandidatura) {
		this.urlCandidatura = urlCandidatura;
	}

	public DisponibilidadeVaga getDisponibilidadeObservada() {
		return disponibilidadeObservada;
	}

	public void setDisponibilidadeObservada(DisponibilidadeVaga disponibilidadeObservada) {
		this.disponibilidadeObservada = disponibilidadeObservada;
	}

	public String getMotivoIndisponibilidade() {
		return motivoIndisponibilidade;
	}

	public void setMotivoIndisponibilidade(String motivoIndisponibilidade) {
		this.motivoIndisponibilidade = motivoIndisponibilidade;
	}

	public Instant getPublicadoNaFonteEm() {
		return publicadoNaFonteEm;
	}

	public void setPublicadoNaFonteEm(Instant publicadoNaFonteEm) {
		this.publicadoNaFonteEm = publicadoNaFonteEm;
	}

	public Instant getAtualizadoNaFonteEm() {
		return atualizadoNaFonteEm;
	}

	public void setAtualizadoNaFonteEm(Instant atualizadoNaFonteEm) {
		this.atualizadoNaFonteEm = atualizadoNaFonteEm;
	}

	public Instant getPrimeiraObservacaoEm() {
		return primeiraObservacaoEm;
	}

	public void setPrimeiraObservacaoEm(Instant primeiraObservacaoEm) {
		this.primeiraObservacaoEm = primeiraObservacaoEm;
	}

	public Instant getUltimaObservacaoEm() {
		return ultimaObservacaoEm;
	}

	public void setUltimaObservacaoEm(Instant ultimaObservacaoEm) {
		this.ultimaObservacaoEm = ultimaObservacaoEm;
	}
}

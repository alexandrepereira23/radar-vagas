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
import java.time.Instant;

@Entity
@Table(name = "oportunidade")
public class Oportunidade {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "empresa_id")
	private Empresa empresa;

	@Column(name = "nome_empresa", length = 255)
	private String nomeEmpresa;

	@Column(name = "cargo", nullable = false, length = 300)
	private String cargo;

	@Column(name = "descricao", nullable = false, columnDefinition = "TEXT")
	private String descricao;

	@Enumerated(EnumType.STRING)
	@Column(name = "area", nullable = false, length = 50)
	private AreaTecnologia area = AreaTecnologia.NAO_INFORMADA;

	@Enumerated(EnumType.STRING)
	@Column(name = "senioridade", nullable = false, length = 50)
	private Senioridade senioridade = Senioridade.NAO_INFORMADA;

	@Enumerated(EnumType.STRING)
	@Column(name = "tipo_oportunidade", nullable = false, length = 50)
	private TipoOportunidade tipoOportunidade = TipoOportunidade.VAGA_ABERTA;

	@Enumerated(EnumType.STRING)
	@Column(name = "regime_contratacao", nullable = false, length = 50)
	private RegimeContratacao regimeContratacao = RegimeContratacao.NAO_INFORMADO;

	@Enumerated(EnumType.STRING)
	@Column(name = "modalidade", nullable = false, length = 50)
	private ModalidadeTrabalho modalidade = ModalidadeTrabalho.NAO_INFORMADA;

	@Column(name = "localizacao", length = 255)
	private String localizacao;

	@Column(name = "elegivel_brasil", nullable = false)
	private boolean elegivelBrasil = true;

	@Column(name = "tecnologias", length = 1000)
	private String tecnologias;

	@Enumerated(EnumType.STRING)
	@Column(name = "disponibilidade_consolidada", nullable = false, length = 50)
	private DisponibilidadeVaga disponibilidadeConsolidada = DisponibilidadeVaga.DISPONIVEL;

	@Column(name = "motivo_disponibilidade", length = 500)
	private String motivoDisponibilidade;

	@Column(name = "link_candidatura", length = 1000)
	private String linkCandidatura;

	@Column(name = "link_origem_principal", nullable = false, length = 1000)
	private String linkOrigemPrincipal;

	@Column(name = "fonte_principal", nullable = false, length = 100)
	private String fontePrincipal;

	@Column(name = "mural_principal", nullable = false, length = 200)
	private String muralPrincipal;

	@Column(name = "identificador_externo_principal", nullable = false, length = 200)
	private String identificadorExternoPrincipal;

	@Column(name = "codigo_requisicao", length = 150)
	private String codigoRequisicao;

	@Column(name = "publicado_em")
	private Instant publicadoEm;

	@Column(name = "primeira_observacao_em", nullable = false)
	private Instant primeiraObservacaoEm;

	@Column(name = "ultima_observacao_em", nullable = false)
	private Instant ultimaObservacaoEm;

	@Column(name = "atualizado_na_fonte_em")
	private Instant atualizadoNaFonteEm;

	@Column(name = "chave_deduplicacao", nullable = false, unique = true, length = 500)
	private String chaveDeduplicacao;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Empresa getEmpresa() {
		return empresa;
	}

	public void setEmpresa(Empresa empresa) {
		this.empresa = empresa;
	}

	public String getNomeEmpresa() {
		return nomeEmpresa;
	}

	public void setNomeEmpresa(String nomeEmpresa) {
		this.nomeEmpresa = nomeEmpresa;
	}

	public String getCargo() {
		return cargo;
	}

	public void setCargo(String cargo) {
		this.cargo = cargo;
	}

	public String getDescricao() {
		return descricao;
	}

	public void setDescricao(String descricao) {
		this.descricao = descricao;
	}

	public AreaTecnologia getArea() {
		return area;
	}

	public void setArea(AreaTecnologia area) {
		this.area = area;
	}

	public Senioridade getSenioridade() {
		return senioridade;
	}

	public void setSenioridade(Senioridade senioridade) {
		this.senioridade = senioridade;
	}

	public TipoOportunidade getTipoOportunidade() {
		return tipoOportunidade;
	}

	public void setTipoOportunidade(TipoOportunidade tipoOportunidade) {
		this.tipoOportunidade = tipoOportunidade;
	}

	public RegimeContratacao getRegimeContratacao() {
		return regimeContratacao;
	}

	public void setRegimeContratacao(RegimeContratacao regimeContratacao) {
		this.regimeContratacao = regimeContratacao;
	}

	public ModalidadeTrabalho getModalidade() {
		return modalidade;
	}

	public void setModalidade(ModalidadeTrabalho modalidade) {
		this.modalidade = modalidade;
	}

	public String getLocalizacao() {
		return localizacao;
	}

	public void setLocalizacao(String localizacao) {
		this.localizacao = localizacao;
	}

	public boolean isElegivelBrasil() {
		return elegivelBrasil;
	}

	public void setElegivelBrasil(boolean elegivelBrasil) {
		this.elegivelBrasil = elegivelBrasil;
	}

	public String getTechnologiesRaw() {
		return tecnologias;
	}

	public String getTecnologias() {
		return tecnologias;
	}

	public void setTecnologias(String tecnologias) {
		this.tecnologias = tecnologias;
	}

	public DisponibilidadeVaga getDisponibilidadeConsolidada() {
		return disponibilidadeConsolidada;
	}

	public void setDisponibilidadeConsolidada(DisponibilidadeVaga disponibilidadeConsolidada) {
		this.disponibilidadeConsolidada = disponibilidadeConsolidada;
	}

	public String getMotivoDisponibilidade() {
		return motivoDisponibilidade;
	}

	public void setMotivoDisponibilidade(String motivoDisponibilidade) {
		this.motivoDisponibilidade = motivoDisponibilidade;
	}

	public String getLinkCandidatura() {
		return linkCandidatura;
	}

	public void setLinkCandidatura(String linkCandidatura) {
		this.linkCandidatura = linkCandidatura;
	}

	public String getLinkOrigemPrincipal() {
		return linkOrigemPrincipal;
	}

	public void setLinkOrigemPrincipal(String linkOrigemPrincipal) {
		this.linkOrigemPrincipal = linkOrigemPrincipal;
	}

	public String getFontePrincipal() {
		return fontePrincipal;
	}

	public void setFontePrincipal(String fontePrincipal) {
		this.fontePrincipal = fontePrincipal;
	}

	public String getMuralPrincipal() {
		return muralPrincipal;
	}

	public void setMuralPrincipal(String muralPrincipal) {
		this.muralPrincipal = muralPrincipal;
	}

	public String getIdentificadorExternoPrincipal() {
		return identificadorExternoPrincipal;
	}

	public void setIdentificadorExternoPrincipal(String identificadorExternoPrincipal) {
		this.identificadorExternoPrincipal = identificadorExternoPrincipal;
	}

	public String getCodigoRequisicao() {
		return codigoRequisicao;
	}

	public void setCodigoRequisicao(String codigoRequisicao) {
		this.codigoRequisicao = codigoRequisicao;
	}

	public Instant getPublicadoEm() {
		return publicadoEm;
	}

	public void setPublicadoEm(Instant publicadoEm) {
		this.publicadoEm = publicadoEm;
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

	public Instant getAtualizadoNaFonteEm() {
		return atualizadoNaFonteEm;
	}

	public void setAtualizadoNaFonteEm(Instant atualizadoNaFonteEm) {
		this.atualizadoNaFonteEm = atualizadoNaFonteEm;
	}

	public String getChaveDeduplicacao() {
		return chaveDeduplicacao;
	}

	public void setChaveDeduplicacao(String chaveDeduplicacao) {
		this.chaveDeduplicacao = chaveDeduplicacao;
	}
}

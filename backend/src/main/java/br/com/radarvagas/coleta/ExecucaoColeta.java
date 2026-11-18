package br.com.radarvagas.coleta;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "execucao_coleta")
public class ExecucaoColeta {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "fornecedor", nullable = false, length = 100)
	private String fornecedor;

	@Column(name = "identificador_mural", nullable = false, length = 200)
	private String identificadorMural;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 50)
	private StatusExecucaoColeta status = StatusExecucaoColeta.EM_ANDAMENTO;

	@Column(name = "iniciado_em", nullable = false)
	private Instant iniciadoEm;

	@Column(name = "finalizado_em")
	private Instant finalizadoEm;

	@Column(name = "anuncios_recebidos", nullable = false)
	private int anunciosRecebidos;

	@Column(name = "anuncios_validos", nullable = false)
	private int anunciosValidos;

	@Column(name = "anuncios_novos", nullable = false)
	private int anunciosNovos;

	@Column(name = "anuncios_atualizados", nullable = false)
	private int anunciosAtualizados;

	@Column(name = "anuncios_repetidos", nullable = false)
	private int anunciosRepetidos;

	@Column(name = "anuncios_ignorados", nullable = false)
	private int anunciosIgnorados;

	@Column(name = "erros_contagem", nullable = false)
	private int errosContagem;

	@Column(name = "rate_limit_restante")
	private Integer rateLimitRestante;

	@Column(name = "rate_limit_reset_em")
	private Instant rateLimitResetEm;

	@Column(name = "erro_resumido", length = 1000)
	private String erroResumido;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
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

	public StatusExecucaoColeta getStatus() {
		return status;
	}

	public void setStatus(StatusExecucaoColeta status) {
		this.status = status;
	}

	public Instant getIniciadoEm() {
		return iniciadoEm;
	}

	public void setIniciadoEm(Instant iniciadoEm) {
		this.iniciadoEm = iniciadoEm;
	}

	public Instant getFinalizadoEm() {
		return finalizadoEm;
	}

	public void setFinalizadoEm(Instant finalizadoEm) {
		this.finalizadoEm = finalizadoEm;
	}

	public int getAnunciosRecebidos() {
		return anunciosRecebidos;
	}

	public void setAnunciosRecebidos(int anunciosRecebidos) {
		this.anunciosRecebidos = anunciosRecebidos;
	}

	public int getAnunciosValidos() {
		return anunciosValidos;
	}

	public void setAnunciosValidos(int anunciosValidos) {
		this.anunciosValidos = anunciosValidos;
	}

	public int getAnunciosNovos() {
		return anunciosNovos;
	}

	public void setAnunciosNovos(int anunciosNovos) {
		this.anunciosNovos = anunciosNovos;
	}

	public int getAnunciosAtualizados() {
		return anunciosAtualizados;
	}

	public void setAnunciosAtualizados(int anunciosAtualizados) {
		this.anunciosAtualizados = anunciosAtualizados;
	}

	public int getAnunciosRepetidos() {
		return anunciosRepetidos;
	}

	public void setAnunciosRepetidos(int anunciosRepetidos) {
		this.anunciosRepetidos = anunciosRepetidos;
	}

	public int getAnunciosIgnorados() {
		return anunciosIgnorados;
	}

	public void setAnunciosIgnorados(int anunciosIgnorados) {
		this.anunciosIgnorados = anunciosIgnorados;
	}

	public int getErrosContagem() {
		return errosContagem;
	}

	public void setErrosContagem(int errosContagem) {
		this.errosContagem = errosContagem;
	}

	public Integer getRateLimitRestante() {
		return rateLimitRestante;
	}

	public void setRateLimitRestante(Integer rateLimitRestante) {
		this.rateLimitRestante = rateLimitRestante;
	}

	public Instant getRateLimitResetEm() {
		return rateLimitResetEm;
	}

	public void setRateLimitResetEm(Instant rateLimitResetEm) {
		this.rateLimitResetEm = rateLimitResetEm;
	}

	public String getErroResumido() {
		return erroResumido;
	}

	public void setErroResumido(String erroResumido) {
		this.erroResumido = erroResumido;
	}
}

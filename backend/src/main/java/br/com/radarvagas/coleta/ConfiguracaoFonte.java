package br.com.radarvagas.coleta;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
		name = "configuracao_fonte",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_configuracao_fonte",
				columnNames = {"fornecedor", "identificador_mural"}
		)
)
public class ConfiguracaoFonte {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "fornecedor", nullable = false, length = 100)
	private String fornecedor;

	@Column(name = "identificador_mural", nullable = false, length = 200)
	private String identificadorMural;

	@Column(name = "nome_exibicao", nullable = false, length = 255)
	private String nomeExibicao;

	@Column(name = "ativo", nullable = false)
	private boolean ativo = true;

	@Column(name = "cadencia_minutos", nullable = false)
	private int cadenciaMinutos = 360;

	@Column(name = "categoria", nullable = false, length = 50)
	private String categoria = "COMUNIDADE";

	@Column(name = "estado_validacao", nullable = false, length = 50)
	private String estadoValidacao = "CONFIGURADA_NAO_VALIDADA";

	@Column(name = "metodo_acesso", length = 255)
	private String metodoAcesso;

	@Column(name = "credencial_necessaria", length = 150)
	private String credencialNecessaria;

	@Column(name = "observacao_validacao", length = 1000)
	private String observacaoValidacao;

	@Column(name = "ultima_coleta_sucesso_em")
	private Instant ultimaColetaSucessoEm;

	@Column(name = "cursor_ultima_atualizacao")
	private Instant cursorUltimaAtualizacao;

	@Column(name = "pagina_continuacao")
	private Integer paginaContinuacao;

	@Column(name = "cursor_janela_atual")
	private Instant cursorJanelaAtual;

	@Column(name = "maior_atualizacao_janela")
	private Instant maiorAtualizacaoJanela;

	@Column(name = "criado_em", nullable = false)
	private Instant criadoEm;

	@Column(name = "atualizado_em", nullable = false)
	private Instant atualizadoEm;

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

	public String getNomeExibicao() {
		return nomeExibicao;
	}

	public void setNomeExibicao(String nomeExibicao) {
		this.nomeExibicao = nomeExibicao;
	}

	public boolean isAtivo() {
		return ativo;
	}

	public void setAtivo(boolean ativo) {
		this.ativo = ativo;
	}

	public int getCadenciaMinutos() {
		return cadenciaMinutos;
	}

	public void setCadenciaMinutos(int cadenciaMinutos) {
		this.cadenciaMinutos = cadenciaMinutos;
	}

	public String getCategoria() {
		return categoria;
	}

	public void setCategoria(String categoria) {
		this.categoria = categoria;
	}

	public String getEstadoValidacao() {
		return estadoValidacao;
	}

	public void setEstadoValidacao(String estadoValidacao) {
		this.estadoValidacao = estadoValidacao;
	}

	public String getMetodoAcesso() {
		return metodoAcesso;
	}

	public void setMetodoAcesso(String metodoAcesso) {
		this.metodoAcesso = metodoAcesso;
	}

	public String getCredencialNecessaria() {
		return credencialNecessaria;
	}

	public void setCredencialNecessaria(String credencialNecessaria) {
		this.credencialNecessaria = credencialNecessaria;
	}

	public String getObservacaoValidacao() {
		return observacaoValidacao;
	}

	public void setObservacaoValidacao(String observacaoValidacao) {
		this.observacaoValidacao = observacaoValidacao;
	}

	public Instant getUltimaColetaSucessoEm() {
		return ultimaColetaSucessoEm;
	}

	public void setUltimaColetaSucessoEm(Instant ultimaColetaSucessoEm) {
		this.ultimaColetaSucessoEm = ultimaColetaSucessoEm;
	}

	public Instant getCursorUltimaAtualizacao() {
		return cursorUltimaAtualizacao;
	}

	public void setCursorUltimaAtualizacao(Instant cursorUltimaAtualizacao) {
		this.cursorUltimaAtualizacao = cursorUltimaAtualizacao;
	}

	public Integer getPaginaContinuacao() {
		return paginaContinuacao;
	}

	public void setPaginaContinuacao(Integer paginaContinuacao) {
		this.paginaContinuacao = paginaContinuacao;
	}

	public Instant getCursorJanelaAtual() {
		return cursorJanelaAtual;
	}

	public void setCursorJanelaAtual(Instant cursorJanelaAtual) {
		this.cursorJanelaAtual = cursorJanelaAtual;
	}

	public Instant getMaiorAtualizacaoJanela() {
		return maiorAtualizacaoJanela;
	}

	public void setMaiorAtualizacaoJanela(Instant maiorAtualizacaoJanela) {
		this.maiorAtualizacaoJanela = maiorAtualizacaoJanela;
	}

	public Instant getCriadoEm() {
		return criadoEm;
	}

	public void setCriadoEm(Instant criadoEm) {
		this.criadoEm = criadoEm;
	}

	public Instant getAtualizadoEm() {
		return atualizadoEm;
	}

	public void setAtualizadoEm(Instant atualizadoEm) {
		this.atualizadoEm = atualizadoEm;
	}
}

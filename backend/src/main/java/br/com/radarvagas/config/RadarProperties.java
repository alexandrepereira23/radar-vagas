package br.com.radarvagas.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "radar")
public class RadarProperties {

	private Cors cors = new Cors();
	private Coleta coleta = new Coleta();
	private Github github = new Github();
	private Greenhouse greenhouse = new Greenhouse();
	private Gupy gupy = new Gupy();
	private Ashby ashby = new Ashby();
	private Lever lever = new Lever();
	private Jooble jooble = new Jooble();
	private Adzuna adzuna = new Adzuna();
	private List<FonteCatalogoConfig> fontesRegistradas = new ArrayList<>();

	public Cors getCors() {
		return cors;
	}

	public void setCors(Cors cors) {
		this.cors = cors;
	}

	public Coleta getColeta() {
		return coleta;
	}

	public void setColeta(Coleta coleta) {
		this.coleta = coleta;
	}

	public Github getGithub() {
		return github;
	}

	public void setGithub(Github github) {
		this.github = github;
	}

	public Greenhouse getGreenhouse() {
		return greenhouse;
	}

	public void setGreenhouse(Greenhouse greenhouse) {
		this.greenhouse = greenhouse;
	}

	public Gupy getGupy() {
		return gupy;
	}

	public void setGupy(Gupy gupy) {
		this.gupy = gupy;
	}

	public Ashby getAshby() {
		return ashby;
	}

	public void setAshby(Ashby ashby) {
		this.ashby = ashby;
	}

	public Lever getLever() {
		return lever;
	}

	public void setLever(Lever lever) {
		this.lever = lever;
	}

	public Jooble getJooble() {
		return jooble;
	}

	public void setJooble(Jooble jooble) {
		this.jooble = jooble;
	}

	public Adzuna getAdzuna() {
		return adzuna;
	}

	public void setAdzuna(Adzuna adzuna) {
		this.adzuna = adzuna;
	}

	public List<FonteCatalogoConfig> getFontesRegistradas() {
		return fontesRegistradas;
	}

	public void setFontesRegistradas(List<FonteCatalogoConfig> fontesRegistradas) {
		this.fontesRegistradas = fontesRegistradas;
	}

	public static class Cors {
		private List<String> origensPermitidas = List.of("http://127.0.0.1:4200", "http://localhost:4200");

		public List<String> getOrigensPermitidas() {
			return origensPermitidas;
		}

		public void setOrigensPermitidas(List<String> origensPermitidas) {
			this.origensPermitidas = origensPermitidas;
		}
	}

	public static class Coleta {
		private boolean agendamentoAtivo = true;
		private long intervaloVerificacaoMs = 300_000L;
		private int desatualizadoAposHoras = 12;

		public boolean isAgendamentoAtivo() {
			return agendamentoAtivo;
		}

		public void setAgendamentoAtivo(boolean agendamentoAtivo) {
			this.agendamentoAtivo = agendamentoAtivo;
		}

		public long getIntervaloVerificacaoMs() {
			return intervaloVerificacaoMs;
		}

		public void setIntervaloVerificacaoMs(long intervaloVerificacaoMs) {
			this.intervaloVerificacaoMs = intervaloVerificacaoMs;
		}

		public int getDesatualizadoAposHoras() {
			return desatualizadoAposHoras;
		}

		public void setDesatualizadoAposHoras(int desatualizadoAposHoras) {
			this.desatualizadoAposHoras = desatualizadoAposHoras;
		}
	}

	public static class Github {
		private String apiBaseUrl = "https://api.github.com";
		private String token = "";
		private String userAgent = "radar-vagas-local/0.2";
		private int itensPorPagina = 30;
		private int maxPaginasPorExecucao = 2;
		private int sobreposicaoIncrementalMinutos = 60;
		private int maxTentativas = 2;
		private long esperaInicialMs = 500L;
		private int timeoutSegundos = 15;
		private List<RepositorioConfig> repositorios = new ArrayList<>();

		public String getApiBaseUrl() {
			return apiBaseUrl;
		}

		public void setApiBaseUrl(String apiBaseUrl) {
			this.apiBaseUrl = apiBaseUrl;
		}

		public String getToken() {
			return token;
		}

		public void setToken(String token) {
			this.token = token;
		}

		public String getUserAgent() {
			return userAgent;
		}

		public void setUserAgent(String userAgent) {
			this.userAgent = userAgent;
		}

		public int getItensPorPagina() {
			return itensPorPagina;
		}

		public void setItensPorPagina(int itensPorPagina) {
			this.itensPorPagina = itensPorPagina;
		}

		public int getMaxPaginasPorExecucao() {
			return maxPaginasPorExecucao;
		}

		public void setMaxPaginasPorExecucao(int maxPaginasPorExecucao) {
			this.maxPaginasPorExecucao = maxPaginasPorExecucao;
		}

		public int getSobreposicaoIncrementalMinutos() {
			return sobreposicaoIncrementalMinutos;
		}

		public void setSobreposicaoIncrementalMinutos(int sobreposicaoIncrementalMinutos) {
			this.sobreposicaoIncrementalMinutos = sobreposicaoIncrementalMinutos;
		}

		public int getMaxTentativas() {
			return maxTentativas;
		}

		public void setMaxTentativas(int maxTentativas) {
			this.maxTentativas = maxTentativas;
		}

		public long getEsperaInicialMs() {
			return esperaInicialMs;
		}

		public void setEsperaInicialMs(long esperaInicialMs) {
			this.esperaInicialMs = esperaInicialMs;
		}

		public int getTimeoutSegundos() {
			return timeoutSegundos;
		}

		public void setTimeoutSegundos(int timeoutSegundos) {
			this.timeoutSegundos = timeoutSegundos;
		}

		public List<RepositorioConfig> getRepositorios() {
			return repositorios;
		}

		public void setRepositorios(List<RepositorioConfig> repositorios) {
			this.repositorios = repositorios;
		}
	}

	public static class Greenhouse {
		private String apiBaseUrl = "https://boards-api.greenhouse.io";
		private int maxItensPorMural = 40;
		private int timeoutSegundos = 15;

		public String getApiBaseUrl() {
			return apiBaseUrl;
		}

		public void setApiBaseUrl(String apiBaseUrl) {
			this.apiBaseUrl = apiBaseUrl;
		}

		public int getMaxItensPorMural() {
			return maxItensPorMural;
		}

		public void setMaxItensPorMural(int maxItensPorMural) {
			this.maxItensPorMural = maxItensPorMural;
		}

		public int getTimeoutSegundos() {
			return timeoutSegundos;
		}

		public void setTimeoutSegundos(int timeoutSegundos) {
			this.timeoutSegundos = timeoutSegundos;
		}
	}

	public static class Gupy {
		private String portalBaseUrl = "https://portal.gupy.io";
		private int maxItensPorBusca = 20;
		private int timeoutSegundos = 15;

		public String getPortalBaseUrl() {
			return portalBaseUrl;
		}

		public void setPortalBaseUrl(String portalBaseUrl) {
			this.portalBaseUrl = portalBaseUrl;
		}

		public int getMaxItensPorBusca() {
			return maxItensPorBusca;
		}

		public void setMaxItensPorBusca(int maxItensPorBusca) {
			this.maxItensPorBusca = maxItensPorBusca;
		}

		public int getTimeoutSegundos() {
			return timeoutSegundos;
		}

		public void setTimeoutSegundos(int timeoutSegundos) {
			this.timeoutSegundos = timeoutSegundos;
		}
	}

	public static class Ashby {
		private String apiBaseUrl = "https://api.ashbyhq.com";
		private int maxItensPorMural = 30;
		private int timeoutSegundos = 15;

		public String getApiBaseUrl() {
			return apiBaseUrl;
		}

		public void setApiBaseUrl(String apiBaseUrl) {
			this.apiBaseUrl = apiBaseUrl;
		}

		public int getMaxItensPorMural() {
			return maxItensPorMural;
		}

		public void setMaxItensPorMural(int maxItensPorMural) {
			this.maxItensPorMural = maxItensPorMural;
		}

		public int getTimeoutSegundos() {
			return timeoutSegundos;
		}

		public void setTimeoutSegundos(int timeoutSegundos) {
			this.timeoutSegundos = timeoutSegundos;
		}
	}

	public static class Lever {
		private String apiBaseUrl = "https://api.lever.co";
		private int maxItensPorMural = 30;
		private int timeoutSegundos = 15;

		public String getApiBaseUrl() {
			return apiBaseUrl;
		}

		public void setApiBaseUrl(String apiBaseUrl) {
			this.apiBaseUrl = apiBaseUrl;
		}

		public int getMaxItensPorMural() {
			return maxItensPorMural;
		}

		public void setMaxItensPorMural(int maxItensPorMural) {
			this.maxItensPorMural = maxItensPorMural;
		}

		public int getTimeoutSegundos() {
			return timeoutSegundos;
		}

		public void setTimeoutSegundos(int timeoutSegundos) {
			this.timeoutSegundos = timeoutSegundos;
		}
	}

	public static class Jooble {
		private String apiBaseUrl = "https://br.jooble.org";
		private String apiKey = "";
		private int maxItensPorBusca = 20;
		private int timeoutSegundos = 15;

		public String getApiBaseUrl() {
			return apiBaseUrl;
		}

		public void setApiBaseUrl(String apiBaseUrl) {
			this.apiBaseUrl = apiBaseUrl;
		}

		public String getApiKey() {
			return apiKey;
		}

		public void setApiKey(String apiKey) {
			this.apiKey = apiKey;
		}

		public int getMaxItensPorBusca() {
			return maxItensPorBusca;
		}

		public void setMaxItensPorBusca(int maxItensPorBusca) {
			this.maxItensPorBusca = maxItensPorBusca;
		}

		public int getTimeoutSegundos() {
			return timeoutSegundos;
		}

		public void setTimeoutSegundos(int timeoutSegundos) {
			this.timeoutSegundos = timeoutSegundos;
		}
	}

	public static class Adzuna {
		private String apiBaseUrl = "https://api.adzuna.com";
		private String appId = "";
		private String appKey = "";
		private int maxItensPorBusca = 20;
		private int timeoutSegundos = 15;

		public String getApiBaseUrl() {
			return apiBaseUrl;
		}

		public void setApiBaseUrl(String apiBaseUrl) {
			this.apiBaseUrl = apiBaseUrl;
		}

		public String getAppId() {
			return appId;
		}

		public void setAppId(String appId) {
			this.appId = appId;
		}

		public String getAppKey() {
			return appKey;
		}

		public void setAppKey(String appKey) {
			this.appKey = appKey;
		}

		public int getMaxItensPorBusca() {
			return maxItensPorBusca;
		}

		public void setMaxItensPorBusca(int maxItensPorBusca) {
			this.maxItensPorBusca = maxItensPorBusca;
		}

		public int getTimeoutSegundos() {
			return timeoutSegundos;
		}

		public void setTimeoutSegundos(int timeoutSegundos) {
			this.timeoutSegundos = timeoutSegundos;
		}
	}

	public static class RepositorioConfig {
		private String identificador;
		private String nomeExibicao;
		private boolean ativo = true;
		private int cadenciaMinutos = 360;

		public String getIdentificador() {
			return identificador;
		}

		public void setIdentificador(String identificador) {
			this.identificador = identificador;
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
	}

	public static class FonteCatalogoConfig {
		private String fornecedor;
		private String identificador;
		private String nomeExibicao;
		private boolean ativo = true;
		private int cadenciaMinutos = 360;
		private String categoria = "COMUNIDADE";
		private String estadoValidacao = "ATIVA_VALIDADA";
		private String metodoAcesso;
		private String credencialNecessaria;
		private String observacaoValidacao;

		public String getFornecedor() {
			return fornecedor;
		}

		public void setFornecedor(String fornecedor) {
			this.fornecedor = fornecedor;
		}

		public String getIdentificador() {
			return identificador;
		}

		public void setIdentificador(String identificador) {
			this.identificador = identificador;
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
	}
}

package br.com.radarvagas;

import br.com.radarvagas.acoes.AcaoPessoalRepository;
import br.com.radarvagas.catalogo.AnuncioOrigemRepository;
import br.com.radarvagas.catalogo.OportunidadeRepository;
import br.com.radarvagas.coleta.ConfiguracaoFonteRepository;
import br.com.radarvagas.coleta.ExecucaoColetaRepository;
import br.com.radarvagas.coleta.OrquestradorColetaService;
import br.com.radarvagas.config.RadarProperties;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

/** Reconstitui configuração e banco antes de cada método, inclusive entre contextos Spring distintos. */
abstract class ColetaTestFixture {
	@Autowired private RadarProperties properties;
	@Autowired private Environment environment;
	@Autowired private AcaoPessoalRepository acoes;
	@Autowired private AnuncioOrigemRepository anuncios;
	@Autowired private OportunidadeRepository oportunidades;
	@Autowired private ExecucaoColetaRepository execucoes;
	@Autowired private ConfiguracaoFonteRepository fontes;
	@Autowired private OrquestradorColetaService orquestrador;

	@BeforeEach
	void restaurarFixtureCompleta() {
		RadarProperties limpa = Binder.get(environment).bind("radar", RadarProperties.class)
				.orElseThrow(() -> new IllegalStateException("Configuração de teste ausente"));
		properties.setCors(limpa.getCors());
		properties.setColeta(limpa.getColeta());
		properties.setGithub(limpa.getGithub());
		properties.setGreenhouse(limpa.getGreenhouse());
		properties.setGupy(limpa.getGupy());
		properties.setAshby(limpa.getAshby());
		properties.setLever(limpa.getLever());
		properties.setJooble(limpa.getJooble());
		properties.setAdzuna(limpa.getAdzuna());
		properties.setFontesRegistradas(limpa.getFontesRegistradas());
		acoes.deleteAll();
		anuncios.deleteAll();
		oportunidades.deleteAll();
		execucoes.deleteAll();
		fontes.deleteAll();
		orquestrador.inicializarFontesERecuperarInterrompidas();
	}
}

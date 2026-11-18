package br.com.radarvagas;

import br.com.radarvagas.config.RadarProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(RadarProperties.class)
@EnableScheduling
public class RadarVagasBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(RadarVagasBackendApplication.class, args);
	}

}

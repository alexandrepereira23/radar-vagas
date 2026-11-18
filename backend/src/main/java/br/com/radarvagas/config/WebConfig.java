package br.com.radarvagas.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

	private final RadarProperties properties;

	public WebConfig(RadarProperties properties) {
		this.properties = properties;
	}

	@Bean
	public Clock systemClock() {
		return Clock.systemUTC();
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		String[] origens = properties.getCors().getOrigensPermitidas().toArray(String[]::new);
		registry.addMapping("/api/**")
				.allowedOrigins(origens)
				.allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
				.allowedHeaders("*");
	}
}

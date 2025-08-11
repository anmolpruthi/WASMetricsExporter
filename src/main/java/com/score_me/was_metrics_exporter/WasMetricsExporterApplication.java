package com.score_me.was_metrics_exporter;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WasMetricsExporterApplication {

	public static void main(String[] args) {
		Dotenv dotenv = Dotenv.load();
		System.setProperty("HOST_URL", dotenv.get("HOST_URL"));
		System.setProperty("HOST_USERNAME", dotenv.get("HOST_USERNAME"));
		System.setProperty("HOST_PASSWORD", dotenv.get("HOST_PASSWORD"));
		SpringApplication.run(WasMetricsExporterApplication.class, args);


	}

}

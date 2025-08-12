package com.score_me.was_metrics_exporter;

import io.github.cdimascio.dotenv.Dotenv;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


@SpringBootApplication
@Slf4j
public class WasMetricsExporterApplication {
	private static final Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

	public static void main(String[] args) {
//		log.info(Arrays.toString(args));
		System.setProperty("HOST_URL", dotenv.get("HOST_URL"));
		System.setProperty("HOST_USERNAME", dotenv.get("HOST_USERNAME"));
		System.setProperty("HOST_PASSWORD", dotenv.get("HOST_PASSWORD"));

		SpringApplication.run(WasMetricsExporterApplication.class, args);
	}
}

//	@Override
//	public void run(ApplicationArguments args) throws Exception {
//		args.getOptionNames().forEach(arg -> System.out.println(arg));
//		args.getNonOptionArgs().forEach(System.out::println);
//		String username = args.getOptionNames().contains("username")? args.getOptionValues("username").getFirst() : dotenv.get("HOST_USERNAME");
//		String password = args.getOptionNames().contains("password") ? args.getOptionValues("password").getFirst() : dotenv.get("HOST_PASSWORD");
//		String host = (args.getOptionValues("host").getFirst()).isEmpty() ? dotenv.get("HOST_URL") : args.getOptionValues("host").getFirst();
//		System.setProperty("HOST_URL", host);
//		System.setProperty("HOST_USERNAME", username);
//		System.setProperty("HOST_PASSWORD", password);
//
//		System.setProperty("HOST_URL", dotenv.get("HOST_URL"));
//		System.setProperty("HOST_USERNAME", dotenv.get("HOST_USERNAME"));
//		System.setProperty("HOST_PASSWORD", dotenv.get("HOST_PASSWORD"));
//	}
//}

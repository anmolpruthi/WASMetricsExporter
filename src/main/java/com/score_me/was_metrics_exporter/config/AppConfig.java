package com.score_me.was_metrics_exporter.config;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
public class AppConfig {

    @Value("${monitor.api-base-url:http://localhost:8080}")
    private String apiBaseUrl;

    @Value("${monitor.verify-ssl:true}")
    private boolean verifySsl;

    @Bean
    public WebClient webClient(WebClient.Builder b) {
        if (!verifySsl) {
            try {
                SslContext sslContext = SslContextBuilder.forClient()
                        .trustManager(InsecureTrustManagerFactory.INSTANCE)
                        .build();
                HttpClient httpClient = HttpClient.create().secure(spec -> spec.sslContext(sslContext));
                return b.clientConnector(new ReactorClientHttpConnector(httpClient)).baseUrl(apiBaseUrl).build();
            } catch (Exception e) {
                throw new RuntimeException("Failed to create insecure WebClient", e);
            }
        }
        return b.baseUrl(apiBaseUrl).build();
    }
}
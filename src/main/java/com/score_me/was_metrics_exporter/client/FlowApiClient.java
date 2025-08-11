package com.score_me.was_metrics_exporter.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.tomcat.websocket.AuthenticationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class FlowApiClient {
    private final WebClient wc;
    private final Logger log = LoggerFactory.getLogger(FlowApiClient.class);

    @Value("${monitor.user:admin}")
    private String user;
    @Value("${monitor.password:admin}")
    private String password;

    private String token;
    private Instant tokenExpiry = Instant.EPOCH;

    public FlowApiClient(WebClient webClient) {
        this.wc = webClient;
    }

    private synchronized void ensureToken() {
        if (token == null || Instant.now().isAfter(tokenExpiry.minusSeconds(30))) {
            log.info("Refreshing API token...");
            try {
                String body = "username=" + URLEncoder.encode(user, StandardCharsets.UTF_8) +
                        "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8);

                String resp = wc.post()
                        .uri("/access/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .bodyValue(body)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                if (resp == null) throw new AuthenticationException("Could not fetch API token");

                // NiFi typically returns the token as plain text
                token = resp.trim().replaceAll("^\"|\"$", "");

                tokenExpiry = Instant.now().plus(6, ChronoUnit.HOURS);
                log.info("Obtained token (len={})", token.length());
            } catch (Exception e) {
                log.error("Failed to obtain token", e);
                throw new RuntimeException(e);
            }
        }
    }

    public JsonNode get(String uri) {
        ensureToken();
        return wc.get()
                .uri(uri)
                .headers(h -> h.setBearerAuth(token))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
    }
}
package com.seoulink.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Component
public class TossPaymentsClient {
    private final RestClient restClient;
    private final String authorization;
    public TossPaymentsClient(RestClient.Builder builder, @Value("${toss.secret-key}") String secretKey) {
        restClient = builder.baseUrl("https://api.tosspayments.com").build();
        authorization = "Basic " + Base64.getEncoder().encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
    }
    public JsonNode confirm(String paymentKey, String orderId, int amount) {
        return restClient.post().uri("/v1/payments/confirm").header(HttpHeaders.AUTHORIZATION, authorization).contentType(MediaType.APPLICATION_JSON).body(Map.of("paymentKey", paymentKey, "orderId", orderId, "amount", amount)).retrieve().body(JsonNode.class);
    }
}

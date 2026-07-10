package com.seoulink.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PortOneClient {

    private final RestClient restClient;
    private final String apiSecret;
    private final String storeId;

    public PortOneClient(
            RestClient.Builder builder,
            @Value("${portone.api-url:https://api.portone.io}") String apiUrl,
            @Value("${portone.api-secret}") String apiSecret,
            @Value("${portone.store-id}") String storeId
    ) {
        this.restClient = builder.baseUrl(apiUrl).build();
        this.apiSecret = apiSecret;
        this.storeId = storeId;
    }

    public JsonNode getPayment(String paymentId) {
        return restClient.get()
                .uri("/payments/{paymentId}", paymentId)
                .header(HttpHeaders.AUTHORIZATION, "PortOne " + apiSecret)
                .retrieve()
                .body(JsonNode.class);
    }

    public String getStoreId() {
        return storeId;
    }
}

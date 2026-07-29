package com.seoulink.backend.infrastructure.external.toss;

import com.seoulink.backend.domain.payment.dto.response.TossPaymentResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Component
public class TossPaymentsClient {

    private final RestClient restClient;
    private final String authorization;

    public TossPaymentsClient(
            RestClient.Builder builder,
            @Value("${toss.secret-key}") String secretKey
    ) {
        this.restClient = builder
                .baseUrl("https://api.tosspayments.com")
                .build();

        this.authorization =
                secretKey == null || secretKey.isBlank()
                        ? null
                        : createAuthorizationHeader(secretKey);
    }

    /**
     * 토스페이먼츠 결제 승인 API를 호출합니다.
     */
    public TossPaymentResponse confirm(
            String paymentKey,
            String orderId,
            int amount
    ) {
        if (authorization == null) {
            throw new IllegalStateException(
                    "TOSS_SECRET_KEY가 설정되지 않았습니다."
            );
        }

        Map<String, Object> requestBody = Map.of(
                "paymentKey", paymentKey,
                "orderId", orderId,
                "amount", amount
        );

        try {
            TossPaymentResponse response = restClient.post()
                    .uri("/v1/payments/confirm")
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(TossPaymentResponse.class);

            if (response == null) {
                throw new IllegalStateException(
                        "토스 결제 승인 응답이 비어 있습니다."
                );
            }

            return response;
        } catch (RestClientResponseException e) {
            throw new IllegalStateException(
                    "토스 결제 승인 실패 (HTTP "
                            + e.getStatusCode().value()
                            + "): "
                            + e.getResponseBodyAsString(),
                    e
            );
        }
    }

    /**
     * 토스페이먼츠 Basic 인증 헤더를 생성합니다.
     *
     * 형식:
     * Basic Base64(secretKey + ":")
     */
    private String createAuthorizationHeader(String secretKey) {
        String credential = secretKey + ":";

        String encodedCredential = Base64.getEncoder()
                .encodeToString(
                        credential.getBytes(StandardCharsets.UTF_8)
                );

        return "Basic " + encodedCredential;
    }
}
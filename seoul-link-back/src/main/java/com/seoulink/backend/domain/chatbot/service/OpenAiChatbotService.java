package com.seoulink.backend.domain.chatbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seoulink.backend.domain.chatbot.dto.request.ChatbotRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

@Service
/**
 * 도메인 규칙과 트랜잭션을 처리하는 서비스입니다.
 */
public class OpenAiChatbotService {

    private final RestClient restClient;
    private final String apiKey;
    private final String model;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenAiChatbotService(
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.model}") String model
    ) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader("Content-Type", "application/json");
        if (!this.apiKey.isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + this.apiKey);
        }
        this.restClient = builder.build();
    }

    // 사용자의 여행 조건을 프롬프트로 구성해 OpenAI Responses API에 전달한다.
    public String generateCourseRecommendation(ChatbotRequest request) {
        if (apiKey.isBlank()) {
            throw new IllegalStateException(
                    "OPENAI_API_KEY가 설정되지 않았습니다. 결제 기능은 사용할 수 있지만 챗봇 답변 생성에는 OpenAI API 키가 필요합니다."
            );
        }
        String prompt = """
                당신은 서울 여행 전문 AI 플래너입니다.
                사용자의 질문에 맞춰 실제로 이동 가능한 서울 여행 코스를 한국어로 제안하세요.

                답변 지침:
                - 먼저 사용자의 조건을 이해했다는 짧고 친근한 안내를 작성합니다.
                - 3~5개의 장소를 시간순으로 추천합니다.
                - 각 장소는 방문 시간, 장소명, 추천 이유, 지역을 포함합니다.
                - 장소 사이의 이동 순서가 자연스러워야 합니다.
                - 예상 소요 시간과 대략적인 1인 비용을 마지막에 정리합니다.
                - 운영시간이나 비용이 변동될 수 있음을 짧게 안내합니다.
                - 과장하거나 존재하지 않는 장소를 만들지 않습니다.

                여행 콘셉트: %s
                사용자 질문: %s
                """.formatted(request.getTravelConcept(), request.getQuestion());

        Map<String, Object> body = Map.of("model", model, "input", prompt);

        try {
            String responseBody = restClient.post()
                    .uri("/responses")
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return extractText(responseBody);
        } catch (RestClientResponseException e) {
            throw new IllegalStateException("OpenAI API 호출에 실패했습니다: " + e.getResponseBodyAsString(), e);
        }
    }

    // Responses API의 output 배열에서 실제 텍스트 응답만 추출한다.
    private String extractText(String responseBody) {
        try {
            JsonNode output = objectMapper.readTree(responseBody).path("output");
            for (JsonNode outputItem : output) {
                for (JsonNode contentItem : outputItem.path("content")) {
                    String text = contentItem.path("text").asText();
                    if (!text.isBlank()) return text;
                }
            }
            throw new IllegalStateException("OpenAI 응답에서 답변을 찾을 수 없습니다.");
        } catch (Exception e) {
            throw new IllegalStateException("OpenAI 응답 처리 중 오류가 발생했습니다.", e);
        }
    }
}

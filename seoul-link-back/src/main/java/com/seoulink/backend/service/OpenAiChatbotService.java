package com.seoulink.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seoulink.backend.dto.request.ChatbotRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

@Service
public class OpenAiChatbotService {

    private final RestClient restClient;
    private final String model;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenAiChatbotService(
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.model}") String model
    ) {
        this.model = model;
        this.restClient = RestClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    public String generateCourseRecommendation(ChatbotRequest request) {
        String prompt = """
                너는 서울 여행 코스 추천 전문가야.

                사용자의 여행 컨셉과 질문을 바탕으로 서울 여행 코스를 추천해줘.

                조건:
                - 한국어로 답변
                - 장소명, 추천 이유, 이동 흐름 포함
                - 하루 일정처럼 보기 좋게 작성
                - 너무 장황하지 않게 작성

                여행 컨셉: %s
                사용자 질문: %s
                """.formatted(request.getTravelConcept(), request.getQuestion());

        Map<String, Object> body = Map.of(
                "model", model,
                "input", prompt
        );

        try {
            String responseBody = restClient.post()
                    .uri("/responses")
                    .body(body)
                    .retrieve()
                    .body(String.class);

            return extractText(responseBody);
        } catch (RestClientResponseException e) {
            throw new IllegalStateException("OpenAI API 호출 실패: " + e.getResponseBodyAsString(), e);
        }
    }

    private String extractText(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode output = root.path("output");

            for (JsonNode outputItem : output) {
                JsonNode content = outputItem.path("content");

                for (JsonNode contentItem : content) {
                    String text = contentItem.path("text").asText();

                    if (!text.isBlank()) {
                        return text;
                    }
                }
            }

            throw new IllegalStateException("OpenAI 응답에서 답변 텍스트를 찾을 수 없습니다.");
        } catch (Exception e) {
            throw new IllegalStateException("OpenAI 응답 처리 중 오류가 발생했습니다.", e);
        }
    }
}
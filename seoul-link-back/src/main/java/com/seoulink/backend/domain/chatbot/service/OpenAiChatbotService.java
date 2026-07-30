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

    /** OpenAI가 반환한 상세 답변과 이력 카드용 압축 코스 요약이다. */
    public record ChatbotRecommendation(String answer, String courseSummary) {}

    // 사용자의 여행 조건을 프롬프트로 구성해 OpenAI Responses API에 전달한다.
    public ChatbotRecommendation generateCourseRecommendation(
            ChatbotRequest request,
            java.util.List<com.seoulink.backend.domain.chatbot.entity.ChatbotHistory> previousConversation
    ) {
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
                - 사용자가 명시한 일정이 1일이면 하루에 3~7개 장소만 추천합니다. 일정 언급이 없으면 당일치기 1일로 제안합니다.
                - "N박 N+1일" 또는 "N일" 요청은 정확히 그 일수만큼 [DAY 1]부터 [DAY N]까지 만듭니다. 일정은 최대 7일(6박 7일)까지만 제안합니다.
                - 2일 이상 일정은 매일 3~7개 장소를 추천합니다. 즉, 1박 2일은 두 DAY의 장소 6~14개와 숙소 1곳, 3박 4일은 네 DAY의 장소 12~28개와 숙소 3곳을 포함해야 합니다. 박수가 늘면 DAY와 전체 장소 수도 같은 비율로 늘려야 합니다.
                - 숙박 일정은 DAY 1부터 마지막 DAY 전날까지 매일 저녁 숙소 1곳을 반드시 포함합니다. [숙소]의 숙소 행은 DAY 1, DAY 2, DAY 3 순서대로 기록해 각 숙소가 해당 DAY와 다음 DAY 사이에 배치될 수 있게 합니다. [핵심 정보]의 장소 줄에도 모든 숙소명을 반드시 포함합니다.
                - 장소 사이의 이동 순서가 자연스러워야 하며, 추천 이유는 한 문장으로 짧게 씁니다.
                - 장문의 서술형 문단, 이모지, 마크다운 표는 사용하지 않습니다.
                - 운영시간이나 비용이 변동될 수 있음을 짧게 안내합니다.
                - 과장하거나 존재하지 않는 장소를 만들지 않습니다.

                answer 값의 형식은 아래를 반드시 지키세요. 대괄호 제목과 세로 막대(|)를 바꾸지 마세요.
                [안내]
                한두 문장의 짧은 안내

                [DAY 1]
                - 10:00–11:30 | 장소명 | 지역 | 90분 | 짧은 추천 이유
                - 11:40–12:40 | 장소명 | 지역 | 60분 | 짧은 추천 이유

                2일 이상일 때만 아래처럼 DAY별 일정을 요청 일수만큼 이어 쓰고, 숙박일 수만큼 숙소를 포함합니다.
                [숙소]
                - 19:00 | 숙소명 | 지역 | 1박 | 숙소 추천 이유

                [핵심 정보]
                - 시간대: 10:00–18:00
                - 장소: DAY 1 장소명 → 숙소명 → DAY 2 장소명 (2일 이상 일정은 숙소명을 반드시 포함)
                - 소요 시간: 약 7시간 30분
                - 예상 비용: 1인 약 25,000~50,000원
                - 안내: 운영시간과 비용은 방문 전 확인하세요.

                반드시 아래 JSON 객체만 반환하세요. Markdown 코드 블록이나 객체 밖의 문장은 금지합니다.
                {
                  "answer": "사용자에게 보여 줄 상세 답변 전문",
                  "courseSummary": "장소 동선, 예상 소요 시간, 추천 대상을 포함한 3줄 이내의 압축 요약"
                }

                여행 콘셉트: %s
                사용자 질문: %s
                """.formatted(
                request.getTravelConcept(),
                request.getQuestion() + "\n\nPrevious conversation context:\n" + formatConversationContext(previousConversation)
        );

        Map<String, Object> body = Map.of("model", model, "input", prompt);

        try {
            String responseBody = restClient.post()
                    .uri("/responses")
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return parseRecommendation(extractText(responseBody));
        } catch (RestClientResponseException e) {
            throw new IllegalStateException("OpenAI API 호출에 실패했습니다: " + e.getResponseBodyAsString(), e);
        }
    }

    // Responses API의 output 배열에서 실제 텍스트 응답만 추출한다.
    private String formatConversationContext(
            java.util.List<com.seoulink.backend.domain.chatbot.entity.ChatbotHistory> histories
    ) {
        if (histories == null || histories.isEmpty()) {
            return "(first message in this conversation)";
        }

        StringBuilder context = new StringBuilder();
        int start = Math.max(0, histories.size() - 6);
        for (int index = start; index < histories.size(); index++) {
            com.seoulink.backend.domain.chatbot.entity.ChatbotHistory history = histories.get(index);
            context.append("User: ").append(history.getQuestion()).append('\n');
            context.append("Assistant: ").append(history.getAnswer()).append('\n');
        }
        return context.toString();
    }

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

    // 모델이 반환한 JSON에서 상세 답변과 카드용 요약을 각각 꺼낸다.
    private ChatbotRecommendation parseRecommendation(String responseText) {
        try {
            String json = responseText.trim();
            if (json.startsWith("```")) {
                json = json.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
            }
            JsonNode result = objectMapper.readTree(json);
            String answer = result.path("answer").asText().trim();
            String courseSummary = result.path("courseSummary").asText().trim();
            if (answer.isBlank() || courseSummary.isBlank()) {
                throw new IllegalStateException("OpenAI 응답에 answer 또는 courseSummary가 없습니다.");
            }
            return new ChatbotRecommendation(answer, courseSummary);
        } catch (Exception e) {
            throw new IllegalStateException("OpenAI 응답 JSON 처리 중 오류가 발생했습니다.", e);
        }
    }
}

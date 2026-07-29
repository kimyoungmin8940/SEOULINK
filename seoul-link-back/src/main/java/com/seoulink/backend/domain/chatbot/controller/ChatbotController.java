package com.seoulink.backend.domain.chatbot.controller;

import com.seoulink.backend.domain.chatbot.dto.request.ChatbotRequest;
import com.seoulink.backend.domain.chatbot.entity.ChatbotHistory;
import com.seoulink.backend.domain.chatbot.service.ChatbotService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chatbot")
/**
 * HTTP 요청을 도메인 서비스로 연결하는 컨트롤러입니다.
 */
public class ChatbotController {

    private final ChatbotService chatbotService;

    public ChatbotController(ChatbotService chatbotService) {
        this.chatbotService = chatbotService;
    }

    // 질문에 대한 AI 추천을 생성하고, 이용권 사용 이력과 함께 저장한다.
    @PostMapping("/ask")
    public ChatbotHistory ask(@Valid @RequestBody ChatbotRequest request) {
        return chatbotService.ask(request);
    }

    // 마이페이지와 챗봇 사이드바에서 사용할 최근 대화 이력을 조회한다.
    @GetMapping("/histories")
    public List<ChatbotHistory> getHistories(@RequestParam Long memberId) {
        return chatbotService.getHistories(memberId);
    }
}

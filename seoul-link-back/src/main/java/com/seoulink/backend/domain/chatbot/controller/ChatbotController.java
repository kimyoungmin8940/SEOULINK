package com.seoulink.backend.domain.chatbot.controller;

import com.seoulink.backend.domain.chatbot.dto.request.ChatbotRequest;
import com.seoulink.backend.domain.chatbot.entity.ChatbotHistory;
import com.seoulink.backend.domain.chatbot.service.ChatbotService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chatbot")
public class ChatbotController {

    private final ChatbotService chatbotService;

    public ChatbotController(ChatbotService chatbotService) {
        this.chatbotService = chatbotService;
    }

    @PostMapping("/ask")
    public ChatbotHistory ask(@Valid @RequestBody ChatbotRequest request) {
        return chatbotService.ask(request);
    }

    @GetMapping("/histories")
    public List<ChatbotHistory> getHistories(@RequestParam Long memberId) {
        return chatbotService.getHistories(memberId);
    }
}

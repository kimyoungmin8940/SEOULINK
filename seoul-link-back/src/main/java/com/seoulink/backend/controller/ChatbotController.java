package com.seoulink.backend.controller;

import com.seoulink.backend.dto.request.ChatbotRequest;
import com.seoulink.backend.entity.ChatbotHistory;
import com.seoulink.backend.service.ChatbotService;
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

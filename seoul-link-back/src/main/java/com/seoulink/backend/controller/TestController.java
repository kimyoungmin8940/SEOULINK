package com.seoulink.backend.controller;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
public class TestController {

    @GetMapping("/api/test")
    public Map<String, String> test() {
        return Map.of(
                "message", "프론트-백 연결 성공!",
                "project", "Seoul Link"
        );
    }
}
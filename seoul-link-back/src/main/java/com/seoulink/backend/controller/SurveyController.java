package com.seoulink.backend.controller;

import com.seoulink.backend.dto.request.SurveySubmitRequest;
import com.seoulink.backend.dto.response.SurveyQuestionResponse;
import com.seoulink.backend.dto.response.SurveyResultDetailResponse;
import com.seoulink.backend.dto.response.SurveySubmitResponse;
import com.seoulink.backend.service.SurveyService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/surveys")
public class SurveyController {

    private final SurveyService surveyService;

    public SurveyController(SurveyService surveyService) {
        this.surveyService = surveyService;
    }

    @PostMapping
    public ResponseEntity<SurveySubmitResponse> submitSurvey(@Valid @RequestBody SurveySubmitRequest request) {
        return ResponseEntity.ok(surveyService.submitSurvey(request));
    }

    @GetMapping("/questions")
    public List<SurveyQuestionResponse> getQuestions() {
        return surveyService.getQuestions();
    }

    @GetMapping("/results/{resultId}")
    public SurveyResultDetailResponse getResult(@PathVariable Long resultId) {
        return surveyService.getResult(resultId);
    }
}

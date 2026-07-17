package com.seoulink.backend.domain.survey.controller;

import com.seoulink.backend.domain.survey.dto.response.SurveyQuestionResponse;
import com.seoulink.backend.domain.survey.service.SurveyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.seoulink.backend.domain.survey.dto.request.GuestSurveySubmitRequest;
import com.seoulink.backend.domain.survey.dto.response.GuestSurveySubmitResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * 취향 설문 관련 HTTP 요청을 처리하는 컨트롤러이다.
 *
 * <p>설문 질문 조회, 사용자 답변 제출, 설문 결과 조회 등의 API를 제공하고
 * 계산 및 저장 로직은 {@code SurveyService}에 위임한다.</p>
 */
@RestController
@RequestMapping("/api/surveys")
@RequiredArgsConstructor
public class SurveyController {

    private final SurveyService surveyService;

    //설문 질문과 각 질문의 선택지를 조회
    // 요청 주소 : /api/surveys/questions
    @GetMapping("/questions")
    public ResponseEntity<List<SurveyQuestionResponse>>
    getQuestions() {

        List<SurveyQuestionResponse> questions =
                surveyService.getQuestions();

        return ResponseEntity.ok(questions);
    }

    @PostMapping("/guest")
    public ResponseEntity<GuestSurveySubmitResponse>
    submitGuestSurvey(
            @Valid
            @RequestBody
            GuestSurveySubmitRequest request
    ) {
        GuestSurveySubmitResponse response =
                surveyService.submitGuestSurvey(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}
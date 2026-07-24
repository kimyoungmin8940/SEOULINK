package com.seoulink.backend.domain.survey.service;

import com.seoulink.backend.domain.survey.dto.request.GuestSurveySubmitRequest;
import com.seoulink.backend.domain.survey.dto.request.SurveyAnswerRequest;
import com.seoulink.backend.domain.survey.dto.response.GuestSurveySubmitResponse;
import com.seoulink.backend.domain.survey.dto.response.SurveyQuestionResponse;
import com.seoulink.backend.domain.survey.dto.response.SurveyResultResponse;
import com.seoulink.backend.domain.survey.entity.SurveyAnswer;
import com.seoulink.backend.domain.survey.entity.SurveyOption;
import com.seoulink.backend.domain.survey.entity.SurveyQuestion;
import com.seoulink.backend.domain.survey.entity.SurveyResult;
import com.seoulink.backend.domain.survey.entity.TravelSurvey;
import com.seoulink.backend.domain.survey.entity.TravelTypeMaster;
import com.seoulink.backend.domain.survey.repository.SurveyAnswerRepository;
import com.seoulink.backend.domain.survey.repository.SurveyOptionRepository;
import com.seoulink.backend.domain.survey.repository.SurveyQuestionRepository;
import com.seoulink.backend.domain.survey.repository.SurveyResultRepository;
import com.seoulink.backend.domain.survey.repository.TravelSurveyRepository;
import com.seoulink.backend.domain.survey.repository.TravelTypeMasterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
/**
 * 취향 설문의 진행, 답변 저장, 점수 계산 및 결과 생성을 담당하는 서비스이다.
 *
 * <p>{@code TRAVEL_SURVEY}, {@code SURVEY_ANSWER}, {@code SURVEY_RESULT}
 * 데이터를 하나의 작업 단위로 저장할 수 있으므로 트랜잭션 경계를 신중히 설정한다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SurveyService {

    private static final int GUEST_RESULT_EXPIRATION_DAYS = 1;

    private static final Set<String> ALLOWED_SCORE_CODES =
            Set.of(
                    "A", "H",
                    "T", "M",
                    "L", "B",
                    "S", "D",
                    "P", "R"
            );

    private final SurveyQuestionRepository
            surveyQuestionRepository;

    private final SurveyOptionRepository
            surveyOptionRepository;

    private final TravelSurveyRepository
            travelSurveyRepository;

    private final SurveyAnswerRepository
            surveyAnswerRepository;

    private final SurveyResultRepository
            surveyResultRepository;

    private final TravelTypeMasterRepository
            travelTypeMasterRepository;

    //질문과 선택지를 표시 순서대로 조회
    public List<SurveyQuestionResponse> getQuestions() {
        List<SurveyQuestion> questions =
                surveyQuestionRepository
                        .findAllByOrderByDisplayOrderAsc();

        return questions.stream()
                .map(SurveyQuestionResponse::from)
                .toList();
    }

    //비회원 설문을 제출하고 결과를 생성
    @Transactional
    public GuestSurveySubmitResponse submitGuestSurvey(
            GuestSurveySubmitRequest request
    ) {
        validateAnswers(request.answers());

        String guestToken = createGuestToken();

        TravelSurvey survey =
                TravelSurvey.createGuestSurvey(
                        guestToken,
                        request.region(),
                        request.startDate(),
                        request.endDate(),
                        request.companionType(),
                        request.transportType(),
                        LocalDateTime.now().plusDays(
                                GUEST_RESULT_EXPIRATION_DAYS
                        )
                );

        TravelSurvey savedSurvey =
                travelSurveyRepository.save(survey);

        List<SurveyOption> selectedOptions =
                validateAndGetSelectedOptions(
                        request.answers()
                );

        saveAnswers(
                savedSurvey.getSurveyId(),
                request.answers()
        );

        String travelCode =
                calculateTravelCode(selectedOptions);

        TravelTypeMaster travelType =
                findTravelType(travelCode);

        SurveyResult surveyResult =
                SurveyResult.create(
                        savedSurvey.getSurveyId(),
                        travelCode
                );

        SurveyResult savedResult =
                surveyResultRepository.save(surveyResult);

        return GuestSurveySubmitResponse.from(
                savedSurvey,
                savedResult,
                travelType
        );
    }

    //설문 실행 번호로 설문 결과를 조회
    public SurveyResultResponse getSurveyResult(
            Long surveyId
    ) {
        SurveyResult result =
                surveyResultRepository
                        .findBySurveyId(surveyId)
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "설문 결과를 찾을 수 없습니다."
                                )
                        );

        TravelTypeMaster travelType =
                findTravelType(result.getTravelCode());

        return SurveyResultResponse.from(
                result,
                travelType
        );
    }

    //비회원 설문 결과를 회원에게 연결
    @Transactional
    public SurveyResultResponse claimGuestSurvey(
            String guestToken,
            Long memberId
    ) {
        if (guestToken == null || guestToken.isBlank()) {
            throw new IllegalArgumentException(
                    "비회원 토큰은 필수입니다."
            );
        }

        if (memberId == null || memberId <= 0) {
            throw new IllegalArgumentException(
                    "회원 번호가 올바르지 않습니다."
            );
        }

        TravelSurvey survey =
                travelSurveyRepository
                        .findByGuestTokenAndMemberIdIsNull(
                                guestToken
                        )
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "연결할 비회원 검사 결과를 찾을 수 없습니다."
                                )
                        );

        survey.claimByMember(memberId);

        SurveyResult result =
                surveyResultRepository
                        .findBySurveyId(
                                survey.getSurveyId()
                        )
                        .orElseThrow(
                                () -> new IllegalStateException(
                                        "검사 결과가 생성되지 않았습니다."
                                )
                        );

        TravelTypeMaster travelType =
                findTravelType(result.getTravelCode());

        return SurveyResultResponse.from(
                result,
                travelType
        );
    }

    //제출된 답변의 개수와 중복 여부를 검사
    private void validateAnswers(
            List<SurveyAnswerRequest> answers
    ) {
        if (answers == null || answers.isEmpty()) {
            throw new IllegalArgumentException(
                    "설문 답변이 비어 있습니다."
            );
        }

        long totalQuestionCount =
                surveyQuestionRepository.count();

        if (answers.size() != totalQuestionCount) {
            throw new IllegalArgumentException(
                    "모든 질문에 답변해야 합니다."
            );
        }

        Set<Long> questionIds = new HashSet<>();

        for (SurveyAnswerRequest answer : answers) {
            if (!questionIds.add(answer.questionId())) {
                throw new IllegalArgumentException(
                        "같은 질문에 답변이 중복되었습니다."
                );
            }
        }
    }

    //선택지가 실제로 해당 질문에 속하는지 검사하고 선택지 엔티티 목록을 반환
    private List<SurveyOption>
    validateAndGetSelectedOptions(
            List<SurveyAnswerRequest> answers
    ) {
        List<SurveyOption> selectedOptions =
                new ArrayList<>();

        for (SurveyAnswerRequest answer : answers) {
            SurveyOption option =
                    surveyOptionRepository
                            .findByOptionIdAndQuestionQuestionId(
                                    answer.optionId(),
                                    answer.questionId()
                            )
                            .orElseThrow(
                                    () -> new IllegalArgumentException(
                                            "질문에 속하지 않는 선택지입니다. "
                                                    + "questionId="
                                                    + answer.questionId()
                                                    + ", optionId="
                                                    + answer.optionId()
                                    )
                            );

            selectedOptions.add(option);
        }

        return selectedOptions;
    }

    //검증된 답변을 SURVEY_ANSWER 테이블에 저장
    private void saveAnswers(
            Long surveyId,
            List<SurveyAnswerRequest> requests
    ) {
        List<SurveyAnswer> answers =
                requests.stream()
                        .map(
                                request ->
                                        SurveyAnswer.create(
                                                surveyId,
                                                request.questionId(),
                                                request.optionId()
                                        )
                        )
                        .toList();

        surveyAnswerRepository.saveAll(answers);
    }

    //선택지의 SCORE_CODE와 SCORE_VALUE를 합산하여 5글자 여행 유형 코드를 만듬
    private String calculateTravelCode(
            List<SurveyOption> selectedOptions
    ) {
        Map<String, Integer> scores = new HashMap<>();

        for (SurveyOption option : selectedOptions) {
            String scoreCode =
                    normalizeScoreCode(
                            option.getScoreCode()
                    );

            if (!ALLOWED_SCORE_CODES.contains(scoreCode)) {
                throw new IllegalStateException(
                        "지원하지 않는 점수 코드입니다: "
                                + scoreCode
                );
            }

            scores.merge(
                    scoreCode,
                    option.getScoreValue(),
                    Integer::sum
            );
        }

        return new StringBuilder()
                .append(selectHigherCode(scores, "A", "H"))
                .append(selectHigherCode(scores, "T", "M"))
                .append(selectHigherCode(scores, "L", "B"))
                .append(selectHigherCode(scores, "S", "D"))
                .append(selectHigherCode(scores, "P", "R"))
                .toString();
    }

    //두 성향 코드 중 점수가 높은 코드를 선택
    private String selectHigherCode(
            Map<String, Integer> scores,
            String firstCode,
            String secondCode
    ) {
        int firstScore =
                scores.getOrDefault(firstCode, 0);

        int secondScore =
                scores.getOrDefault(secondCode, 0);

        if (firstScore == secondScore) {
            throw new IllegalStateException(
                    firstCode + "/" + secondCode
                            + " 성향 점수가 동점입니다."
            );
        }

        return firstScore > secondScore
                ? firstCode
                : secondCode;
    }

    //여행 유형 코드의 앞뒤 공백을 제거하고 대문자로 통일
    private String normalizeScoreCode(
            String scoreCode
    ) {
        if (scoreCode == null || scoreCode.isBlank()) {
            throw new IllegalStateException(
                    "선택지의 점수 코드가 비어 있습니다."
            );
        }

        return scoreCode.trim().toUpperCase();
    }

    //여행 유형 코드에 해당하는 유형 정보를 조회
    private TravelTypeMaster findTravelType(
            String travelCode
    ) {
        String normalizedCode =
                travelCode.trim().toUpperCase();

        return travelTypeMasterRepository
                .findByTravelCode(normalizedCode)
                .orElseThrow(
                        () -> new IllegalStateException(
                                "여행 유형 정보를 찾을 수 없습니다: "
                                        + normalizedCode
                        )
                );
    }

    //중복되지 않는 비회원 UUID 토큰을 생성
    private String createGuestToken() {
        String guestToken;

        do {
            guestToken = UUID.randomUUID().toString();
        } while (
                travelSurveyRepository
                        .existsByGuestToken(guestToken)
        );

        return guestToken;
    }
}

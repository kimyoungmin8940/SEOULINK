package com.seoulink.backend.service;

import com.seoulink.backend.dto.request.SurveySubmitRequest;
import com.seoulink.backend.dto.response.PlaceResponse;
import com.seoulink.backend.dto.response.RecommendedCourseDayResponse;
import com.seoulink.backend.dto.response.RecommendedCoursePlaceResponse;
import com.seoulink.backend.dto.response.SurveyOptionResponse;
import com.seoulink.backend.dto.response.SurveyQuestionResponse;
import com.seoulink.backend.dto.response.SurveyResultDetailResponse;
import com.seoulink.backend.dto.response.SurveySubmitResponse;
import com.seoulink.backend.entity.Place;
import com.seoulink.backend.entity.SurveyAnswer;
import com.seoulink.backend.entity.SurveyOption;
import com.seoulink.backend.entity.SurveyQuestion;
import com.seoulink.backend.entity.SurveyResult;
import com.seoulink.backend.entity.TravelSurvey;
import com.seoulink.backend.entity.TravelTypeMaster;
import com.seoulink.backend.entity.TravelTypePlace;
import com.seoulink.backend.repository.MemberRepository;
import com.seoulink.backend.repository.PlaceRepository;
import com.seoulink.backend.repository.SurveyAnswerRepository;
import com.seoulink.backend.repository.SurveyOptionRepository;
import com.seoulink.backend.repository.SurveyQuestionRepository;
import com.seoulink.backend.repository.SurveyResultRepository;
import com.seoulink.backend.repository.TravelSurveyRepository;
import com.seoulink.backend.repository.TravelTypeMasterRepository;
import com.seoulink.backend.repository.TravelTypePlaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SurveyService {

    private final SurveyQuestionRepository surveyQuestionRepository;
    private final TravelSurveyRepository travelSurveyRepository;
    private final SurveyOptionRepository surveyOptionRepository;
    private final SurveyAnswerRepository surveyAnswerRepository;
    private final SurveyResultRepository surveyResultRepository;
    private final TravelTypeMasterRepository travelTypeMasterRepository;
    private final TravelTypePlaceRepository travelTypePlaceRepository;
    private final PlaceRepository placeRepository;
    private final MemberRepository memberRepository;

    public SurveyService(
            SurveyQuestionRepository surveyQuestionRepository,
            TravelSurveyRepository travelSurveyRepository,
            SurveyOptionRepository surveyOptionRepository,
            SurveyAnswerRepository surveyAnswerRepository,
            SurveyResultRepository surveyResultRepository,
            TravelTypeMasterRepository travelTypeMasterRepository,
            TravelTypePlaceRepository travelTypePlaceRepository,
            PlaceRepository placeRepository,
            MemberRepository memberRepository
    ) {
        this.surveyQuestionRepository = surveyQuestionRepository;
        this.travelSurveyRepository = travelSurveyRepository;
        this.surveyOptionRepository = surveyOptionRepository;
        this.surveyAnswerRepository = surveyAnswerRepository;
        this.surveyResultRepository = surveyResultRepository;
        this.travelTypeMasterRepository = travelTypeMasterRepository;
        this.travelTypePlaceRepository = travelTypePlaceRepository;
        this.placeRepository = placeRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional
    public SurveySubmitResponse submitSurvey(SurveySubmitRequest request) {
        validateSurveyRequest(request);

        TravelSurvey survey = new TravelSurvey();
        survey.setMemberId(request.getMemberId());
        survey.setRegion(request.getRegion());
        survey.setStartDate(request.getStartDate());
        survey.setEndDate(request.getEndDate());
        survey.setPeopleCount(request.getPeopleCount());
        travelSurveyRepository.save(survey);

        Map<String, Map<String, Integer>> scoreMapByCategory = new LinkedHashMap<>();

        for (SurveySubmitRequest.AnswerRequest answerRequest : request.getAnswers()) {
            SurveyOption option = surveyOptionRepository.findById(answerRequest.getOptionId())
                    .orElseThrow(() -> new IllegalArgumentException("선택지를 찾을 수 없습니다."));

            SurveyQuestion question = surveyQuestionRepository.findById(answerRequest.getQuestionId())
                    .orElseThrow(() -> new IllegalArgumentException("질문을 찾을 수 없습니다."));

            if (!question.getQuestionId().equals(option.getQuestionId())) {
                throw new IllegalArgumentException("질문과 선택지가 일치하지 않습니다.");
            }

            SurveyAnswer answer = new SurveyAnswer();
            answer.setSurveyId(survey.getSurveyId());
            answer.setQuestionId(answerRequest.getQuestionId());
            answer.setOptionId(answerRequest.getOptionId());
            surveyAnswerRepository.save(answer);

            scoreMapByCategory
                    .computeIfAbsent(question.getCategory(), key -> new HashMap<>())
                    .merge(option.getScoreCode(), option.getScoreValue(), Integer::sum);
        }

        String travelCode = buildTravelCode(scoreMapByCategory);

        TravelTypeMaster type = travelTypeMasterRepository.findById(travelCode)
                .orElseThrow(() -> new IllegalArgumentException("여행 유형을 찾을 수 없습니다."));

        SurveyResult result = new SurveyResult();
        result.setSurveyId(survey.getSurveyId());
        result.setTravelCode(travelCode);
        surveyResultRepository.save(result);

        List<Place> recommendedPlaces = recommendPlaceEntities(travelCode, survey.getRegion());
        List<PlaceResponse> placeResponses = recommendedPlaces.stream()
                .map(PlaceResponse::new)
                .toList();

        return new SurveySubmitResponse(
                survey.getSurveyId(),
                result.getResultId(),
                travelCode,
                type.getTypeTitle(),
                type.getTypeDescription(),
                placeResponses,
                buildItinerary(recommendedPlaces, survey)
        );
    }

    public List<SurveyQuestionResponse> getQuestions() {
        return surveyQuestionRepository.findAllByOrderByQuestionIdAsc()
                .stream()
                .map(question -> new SurveyQuestionResponse(
                        question,
                        surveyOptionRepository.findByQuestionIdOrderByOptionIdAsc(question.getQuestionId())
                                .stream()
                                .map(SurveyOptionResponse::new)
                                .toList()
                ))
                .toList();
    }

    public SurveyResultDetailResponse getResult(Long resultId) {
        SurveyResult result = surveyResultRepository.findById(resultId)
                .orElseThrow(() -> new IllegalArgumentException("설문 결과를 찾을 수 없습니다."));

        TravelSurvey survey = travelSurveyRepository.findById(result.getSurveyId())
                .orElseThrow(() -> new IllegalArgumentException("설문 정보를 찾을 수 없습니다."));

        TravelTypeMaster type = travelTypeMasterRepository.findById(result.getTravelCode())
                .orElseThrow(() -> new IllegalArgumentException("여행 유형을 찾을 수 없습니다."));

        List<Place> recommendedPlaces = recommendPlaceEntities(result.getTravelCode(), survey.getRegion());

        return new SurveyResultDetailResponse(
                result,
                survey,
                type,
                recommendedPlaces.stream().map(PlaceResponse::new).toList(),
                buildItinerary(recommendedPlaces, survey)
        );
    }

    private String buildTravelCode(Map<String, Map<String, Integer>> scoreMapByCategory) {
        if (scoreMapByCategory.isEmpty()) {
            throw new IllegalArgumentException("설문 결과를 계산할 수 없습니다.");
        }

        StringBuilder travelCode = new StringBuilder();
        for (Map<String, Integer> scoreMap : scoreMapByCategory.values()) {
            String scoreCode = scoreMap.entrySet()
                    .stream()
                    .max(Map.Entry.comparingByValue())
                    .orElseThrow(() -> new IllegalArgumentException("설문 결과를 계산할 수 없습니다."))
                    .getKey();

            travelCode.append(extractTravelCodeLetter(scoreCode));
        }

        if (travelCode.length() != 5) {
            throw new IllegalArgumentException("여행 유형 코드는 5글자여야 합니다.");
        }

        return travelCode.toString();
    }

    private String extractTravelCodeLetter(String scoreCode) {
        if (scoreCode == null || scoreCode.isBlank()) {
            throw new IllegalArgumentException("설문 결과를 계산할 수 없습니다.");
        }

        String normalizedCode = scoreCode.trim().toUpperCase();
        return normalizedCode.substring(normalizedCode.length() - 1);
    }

    private void validateSurveyRequest(SurveySubmitRequest request) {
        if (request.getMemberId() != null && !memberRepository.existsById(request.getMemberId())) {
            throw new IllegalArgumentException("회원 정보를 찾을 수 없습니다.");
        }

        if (request.getStartDate() != null
                && request.getEndDate() != null
                && request.getEndDate().isBefore(request.getStartDate())) {
            throw new IllegalArgumentException("여행 종료일은 시작일보다 빠를 수 없습니다.");
        }
    }

    private List<Place> recommendPlaceEntities(String travelCode, String region) {
        List<Long> placeIds = travelTypePlaceRepository
                .findTop30ByTravelCodeOrderByWeightScoreDesc(travelCode)
                .stream()
                .map(TravelTypePlace::getPlaceId)
                .toList();

        List<Place> places = placeRepository.findByPlaceIdInAndIsActive(placeIds, "Y")
                .stream()
                .filter(place -> region == null
                        || place.getRegion().contains(region)
                        || region.contains(place.getRegion()))
                .sorted(Comparator.comparing(Place::getRating).reversed())
                .toList();

        List<Place> result = new ArrayList<>();
        addByCategory(result, places, "TOUR", 6);
        addByCategory(result, places, "RESTAURANT", 4);
        addByCategory(result, places, "CAFE", 3);
        addByCategory(result, places, "HOTEL", 2);

        if (result.isEmpty()) {
            return places.stream().limit(10).toList();
        }
        return result;
    }

    private void addByCategory(List<Place> result, List<Place> places, String category, int limit) {
        places.stream()
                .filter(place -> category.equals(place.getCategory()))
                .limit(limit)
                .forEach(result::add);
    }

    private List<RecommendedCourseDayResponse> buildItinerary(List<Place> places, TravelSurvey survey) {
        int days = calculateDays(survey);
        List<RecommendedCourseDayResponse> itinerary = new ArrayList<>();

        for (int day = 1; day <= days; day++) {
            List<RecommendedCoursePlaceResponse> dayPlaces = pickDayPlaces(places, day);
            itinerary.add(new RecommendedCourseDayResponse(day, dayPlaces));
        }

        return itinerary;
    }

    private int calculateDays(TravelSurvey survey) {
        if (survey.getStartDate() == null || survey.getEndDate() == null) {
            return 1;
        }

        long days = Duration.between(
                survey.getStartDate().toLocalDate().atStartOfDay(),
                survey.getEndDate().toLocalDate().atStartOfDay()
        ).toDays() + 1;

        return (int) Math.max(1, Math.min(days, 5));
    }

    private List<RecommendedCoursePlaceResponse> pickDayPlaces(List<Place> places, int dayNo) {
        List<RecommendedCoursePlaceResponse> result = new ArrayList<>();
        List<String> categories = List.of("TOUR", "RESTAURANT", "CAFE", "TOUR", "HOTEL");
        List<LocalTime> times = List.of(
                LocalTime.of(10, 0),
                LocalTime.of(12, 30),
                LocalTime.of(15, 0),
                LocalTime.of(17, 0),
                LocalTime.of(20, 0)
        );

        for (int i = 0; i < categories.size(); i++) {
            String category = categories.get(i);
            Place place = places.stream()
                    .filter(candidate -> category.equals(candidate.getCategory()))
                    .skip((long) (dayNo - 1) * 2)
                    .findFirst()
                    .orElse(null);

            if (place != null) {
                int stayMinutes = "RESTAURANT".equals(category) || "CAFE".equals(category) ? 60 : 90;
                result.add(new RecommendedCoursePlaceResponse(
                        place,
                        dayNo,
                        result.size() + 1,
                        times.get(i).toString(),
                        stayMinutes
                ));
            }
        }

        return result;
    }
}
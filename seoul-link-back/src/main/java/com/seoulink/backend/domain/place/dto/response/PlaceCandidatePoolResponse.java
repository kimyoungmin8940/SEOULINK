package com.seoulink.backend.domain.place.dto.response;

import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 코스 생성 서비스가 백엔드 내부에서 직접 사용하는 추천 장소 후보 풀이다.
 *
 * <p>장소마다 대체 후보를 중첩하지 않고 카테고리별 후보를 한 번만 보관한다.
 * 코스 생성 서비스는 장소를 선택한 뒤 같은 카테고리의 남은 후보를 대체 후보로
 * 사용할 수 있다.</p>
 */
@Getter
public class PlaceCandidatePoolResponse {

    private final String travelCode;
    private final String scheduleType;
    private final String companionType;
    private final List<String> preferredRegions;
    private final int targetCandidateCount;
    private final int maxLookupCount;
    private final Map<String, List<PlaceRecommendationResponse>> candidatesByCategory;
    private final Map<String, List<PlaceRecommendationResponse>> fallbackCandidatesByCategory;
    private final List<PlaceRecommendationResponse> hotelCandidates;
    private final List<PlaceRecommendationResponse> fallbackHotelCandidates;

    public PlaceCandidatePoolResponse(
            String travelCode,
            String scheduleType,
            String companionType,
            List<String> preferredRegions,
            int targetCandidateCount,
            int maxLookupCount,
            Map<String, List<PlaceRecommendationResponse>> candidatesByCategory,
            Map<String, List<PlaceRecommendationResponse>> fallbackCandidatesByCategory,
            List<PlaceRecommendationResponse> hotelCandidates,
            List<PlaceRecommendationResponse> fallbackHotelCandidates
    ) {
        this.travelCode = travelCode;
        this.scheduleType = scheduleType;
        this.companionType = companionType;
        this.preferredRegions = List.copyOf(preferredRegions);
        this.targetCandidateCount = targetCandidateCount;
        this.maxLookupCount = maxLookupCount;
        this.candidatesByCategory = immutableCopy(candidatesByCategory);
        this.fallbackCandidatesByCategory = immutableCopy(fallbackCandidatesByCategory);
        this.hotelCandidates = List.copyOf(hotelCandidates);
        this.fallbackHotelCandidates = List.copyOf(fallbackHotelCandidates);
    }

    /** 기존 직접 생성 코드와의 호환을 유지한다. */
    public PlaceCandidatePoolResponse(
            String travelCode,
            String scheduleType,
            String companionType,
            int targetCandidateCount,
            int maxLookupCount,
            Map<String, List<PlaceRecommendationResponse>> candidatesByCategory,
            Map<String, List<PlaceRecommendationResponse>> fallbackCandidatesByCategory,
            List<PlaceRecommendationResponse> hotelCandidates,
            List<PlaceRecommendationResponse> fallbackHotelCandidates
    ) {
        this(
                travelCode,
                scheduleType,
                companionType,
                List.of(),
                targetCandidateCount,
                maxLookupCount,
                candidatesByCategory,
                fallbackCandidatesByCategory,
                hotelCandidates,
                fallbackHotelCandidates
        );
    }

    /**
     * TOUR, RESTAURANT, CAFE 후보를 점수순으로 합쳐 코스 생성 서비스에 전달한다.
     */
    public List<PlaceRecommendationResponse> getCandidates() {
        return candidatesByCategory.values()
                .stream()
                .flatMap(List::stream)
                .sorted((left, right) -> {
                    int scoreComparison = Double.compare(
                            right.getRecommendationScore(),
                            left.getRecommendationScore()
                    );
                    if (scoreComparison != 0) {
                        return scoreComparison;
                    }
                    return Long.compare(left.getPlaceId(), right.getPlaceId());
                })
                .toList();
    }

    public List<PlaceRecommendationResponse> getCandidatesForCategory(String category) {
        if (category == null) {
            return List.of();
        }
        return candidatesByCategory.getOrDefault(
                category.trim().toUpperCase(),
                List.of()
        );
    }

    public List<PlaceRecommendationResponse> getFallbackCandidatesForCategory(
            String category
    ) {
        if (category == null) {
            return List.of();
        }
        return fallbackCandidatesByCategory.getOrDefault(
                category.trim().toUpperCase(),
                List.of()
        );
    }

    private Map<String, List<PlaceRecommendationResponse>> immutableCopy(
            Map<String, List<PlaceRecommendationResponse>> source
    ) {
        Map<String, List<PlaceRecommendationResponse>> copy =
                new LinkedHashMap<>();
        source.forEach((category, candidates) ->
                copy.put(category, List.copyOf(candidates)));
        return java.util.Collections.unmodifiableMap(copy);
    }
}

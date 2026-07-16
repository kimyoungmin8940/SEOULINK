package com.seoulink.backend.domain.place.service;

import com.seoulink.backend.domain.place.dto.response.PlaceAlternativeResponse;
import com.seoulink.backend.domain.place.dto.response.PlaceRecommendationListResponse;
import com.seoulink.backend.domain.place.dto.response.PlaceRecommendationResponse;
import com.seoulink.backend.domain.place.entity.Place;
import com.seoulink.backend.domain.place.repository.PlaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class PlaceRecommendationService {

    // 요청에서 개수를 생략했을 때 반환할 대표 추천 장소 수
    private static final int DEFAULT_LIMIT = 10;

    // 대표 추천 장소 하나당 함께 반환할 대체 후보 수
    private static final int DEFAULT_ALTERNATIVE_LIMIT = 3;

    // 여행 코드 한 글자와 장소의 성향 태그가 일치할 때 얻는 점수
    // 5개 성향이 모두 일치하면 18점 * 5개 = 90점이다.
    private static final double CODE_MATCH_SCORE = 18.0;

    private final PlaceRepository placeRepository;

    public PlaceRecommendationService(PlaceRepository placeRepository) {
        this.placeRepository = placeRepository;
    }

    /**
     * 추천 API의 전체 처리 흐름이다.
     *
     * 1. 여행 코드를 검사하고 대문자로 통일한다.
     * 2. 활성화된 장소만 DB에서 조회한다.
     * 3. 각 장소의 취향 점수 + 평점 보너스 + 리뷰 보너스를 계산한다.
     * 4. 점수가 높은 순서대로 대표 추천 장소를 선택한다.
     * 5. 대표 장소마다 겹치지 않는 대체 후보를 붙인다.
     */
    public PlaceRecommendationListResponse recommend(String travelCode, Integer limit, Integer alternativeLimit) {
        // 예: " atbsp " -> "ATBSP"
        String normalizedTravelCode = normalizeTravelCode(travelCode);

        // limit 값이 없거나 0 이하이면 기본값을 사용하고, 최대 50개까지만 허용한다.
        int resolvedLimit = resolveLimit(limit, DEFAULT_LIMIT);
        int resolvedAlternativeLimit = resolveLimit(alternativeLimit, DEFAULT_ALTERNATIVE_LIMIT);

        // 추천 대상으로 사용하도록 설정된 활성 장소만 가져온다.
        List<ScoredPlace> scoredPlaces = placeRepository.findByIsActive("Y")
                .stream()

                // Place와 계산된 점수를 한 쌍으로 묶는다.
                // DB의 PLACES 테이블에 사용자별 점수를 저장하지 않고 요청할 때마다 계산한다.
                .map(place -> new ScoredPlace(place, calculateScore(place, normalizedTravelCode)))

                // 0점인 장소는 추천 후보에서 제외한다.
                // 현재 평점과 리뷰도 점수에 포함되므로 취향 태그가 일치하지 않아도
                // 평점 또는 리뷰가 있으면 0점보다 커져 후보에 남을 수 있다.
                .filter(scoredPlace -> scoredPlace.score() > 0)

                // 추천 점수 -> 평점 -> 리뷰 수 -> placeId 순서로 정렬한다.
                .sorted(scoreComparator())
                .toList();

        // 정렬된 전체 후보 중 앞에서부터 limit개를 대표 추천 장소로 선택한다.
        List<ScoredPlace> recommended = scoredPlaces.stream()
                .limit(resolvedLimit)
                .toList();

        // 대표 추천 장소가 다른 장소의 대체 후보로 다시 들어가지 않도록 ID를 모아 둔다.
        Set<Long> recommendedPlaceIds = recommended.stream()
                .map(scoredPlace -> scoredPlace.place().getPlaceId())
                .collect(Collectors.toSet());

        // 대표 장소마다 alternativeCandidates를 만들어 최종 응답 DTO로 변환한다.
        List<PlaceRecommendationResponse> recommendedPlaces = recommended.stream()
                .map(scoredPlace -> toRecommendationResponse(
                        scoredPlace,
                        scoredPlaces,
                        recommendedPlaceIds,
                        resolvedAlternativeLimit
                ))
                .toList();

        // 최종 JSON: { "travelCode": "ATBSP", "recommendedPlaces": [...] }
        return new PlaceRecommendationListResponse(normalizedTravelCode, recommendedPlaces);
    }

    /**
     * 대표 추천 장소 하나에 대체 장소 목록을 붙인다.
     */
    private PlaceRecommendationResponse toRecommendationResponse(
            ScoredPlace recommendedPlace,
            List<ScoredPlace> scoredPlaces,
            Set<Long> recommendedPlaceIds,
            int alternativeLimit
    ) {
        List<PlaceAlternativeResponse> alternatives = scoredPlaces.stream()

                // 자기 자신은 자신의 대체 후보가 될 수 없다.
                .filter(candidate -> !candidate.place().getPlaceId().equals(recommendedPlace.place().getPlaceId()))

                // 이미 대표 추천 목록에 들어간 장소도 대체 후보에서는 제외한다.
                .filter(candidate -> !recommendedPlaceIds.contains(candidate.place().getPlaceId()))

                // 대표 장소와 테마가 하나 이상 겹치거나 기본 카테고리가 같은 장소만 남긴다.
                .filter(candidate -> isAlternativeFor(recommendedPlace.place(), candidate.place()))

                // 겹치는 테마 수 -> 추천 점수 -> placeId 순서로 대체 후보를 정렬한다.
                .sorted(alternativeComparator(recommendedPlace.place()))
                .limit(alternativeLimit)
                .map(candidate -> new PlaceAlternativeResponse(candidate.place(), candidate.score()))
                .toList();

        return new PlaceRecommendationResponse(
                recommendedPlace.place(),
                recommendedPlace.score(),
                alternatives
        );
    }

    /**
     * 장소 하나의 최종 추천 점수를 계산한다.
     *
     * 현재 점수 구성:
     * - 여행 코드 일치 점수: 최대 90점 (18점 * 5글자)
     * - 평점 보너스: 최대 6점
     * - 리뷰 보너스: 최대 4점
     * - 최종 점수: 최대 100점
     *
     * 예: 코드 4개 일치 72점 + 평점 보너스 5.64점 + 리뷰 보너스 3.54점
     *     = 81.18점 -> 소수점 첫째 자리로 반올림하여 81.2점
     */
    private double calculateScore(Place place, String travelCode) {
        double score = 0.0;

        // 여행 코드 5글자를 하나씩 읽어 장소 태그와 일치하는지 확인한다.
        for (int i = 0; i < travelCode.length(); i++) {
            score += calculateCodeScore(place, travelCode.charAt(i));
        }

        // 취향 코드 점수 외에 장소 자체의 신뢰도 지표를 보너스로 더한다.
        score += calculateRatingBonus(place);
        score += calculateReviewBonus(place);

        // 100점을 넘으면 100점으로 제한하고 소수점 첫째 자리로 반올림한다.
        return roundOneDecimal(Math.min(score, 100.0));
    }

    /**
     * 여행 코드 한 글자를 장소의 TAG_*_YN 값과 비교한다.
     * 해당 태그가 "Y"이면 18점, "N" 또는 null이면 0점이다.
     *
     * 현재 코드와 DB 태그 연결:
     * A -> TAG_DOPAMINE : 활동적인 장소
     * H -> TAG_RELAX    : 휴식·힐링 장소
     * T -> TAG_HISTORY  : 역사·전통 장소
     * M -> TAG_MODERN   : 현대적인 장소
     * B -> TAG_BUDGET   : 가성비 장소
     * L -> TAG_LUXURY   : 고급·럭셔리 장소
     * S -> TAG_STABLE   : 안정적인 장소
     * D -> TAG_DOPAMINE : 자극적인 장소
     * P -> TAG_PACKED   : 빽빽한 일정에 맞는 장소
     * R -> TAG_RELAX    : 여유로운 일정에 맞는 장소
     *
     * A와 D는 TAG_DOPAMINE을, H와 R은 TAG_RELAX을 함께 사용한다.
     * 현재는 코드의 위치가 아니라 글자 자체만 보고 점수를 계산한다.
     */
    private double calculateCodeScore(Place place, char code) {
        return switch (code) {
            case 'A' -> yesScore(place.getTagDopamine());
            case 'H' -> yesScore(place.getTagRelax());
            case 'T' -> yesScore(place.getTagHistory());
            case 'M' -> yesScore(place.getTagModern());
            case 'B' -> yesScore(place.getTagBudget());
            case 'L' -> yesScore(place.getTagLuxury());
            case 'S' -> yesScore(place.getTagStable());
            case 'D' -> yesScore(place.getTagDopamine());
            case 'P' -> yesScore(place.getTagPacked());
            case 'R' -> yesScore(place.getTagRelax());
            default -> 0.0;
        };
    }

    /**
     * 대체 후보 자격을 검사한다.
     * 8개 서비스 테마 중 하나라도 겹치거나 category 값이 같으면 true이다.
     */
    private boolean isAlternativeFor(Place basePlace, Place candidatePlace) {
        return countThemeOverlap(basePlace, candidatePlace) > 0
                || safeEquals(basePlace.getCategory(), candidatePlace.getCategory());
    }

    /**
     * 전체 추천 후보의 정렬 기준이다.
     * 1순위 추천 점수 내림차순
     * 2순위 평점 내림차순
     * 3순위 리뷰 수 내림차순
     * 4순위 placeId 오름차순
     *
     * 마지막에 placeId를 비교하므로 점수, 평점, 리뷰 수가 모두 같아도
     * 실행할 때마다 결과 순서가 달라지지 않는다.
     */
    private Comparator<ScoredPlace> scoreComparator() {
        return Comparator.comparing(ScoredPlace::score, Comparator.reverseOrder())
                .thenComparing(scoredPlace -> nullToZero(scoredPlace.place().getRating()), Comparator.reverseOrder())
                .thenComparing(scoredPlace -> nullToZero(scoredPlace.place().getReviewCount()), Comparator.reverseOrder())
                .thenComparing(scoredPlace -> scoredPlace.place().getPlaceId());
    }

    /**
     * 대체 후보의 정렬 기준이다.
     * 1순위 대표 장소와 겹치는 테마 수 내림차순
     * 2순위 추천 점수 내림차순
     * 3순위 placeId 오름차순
     */
    private Comparator<ScoredPlace> alternativeComparator(Place basePlace) {
        return Comparator.<ScoredPlace>comparingInt(candidate -> countThemeOverlap(basePlace, candidate.place()))
                .reversed()
                .thenComparing(ScoredPlace::score, Comparator.reverseOrder())
                .thenComparing(candidate -> candidate.place().getPlaceId());
    }

    /**
     * 두 장소가 공통으로 가진 서비스 테마의 개수를 계산한다.
     * 예를 들어 두 장소 모두 데이트=Y, 야경=Y라면 결과는 2이다.
     */
    private int countThemeOverlap(Place left, Place right) {
        int count = 0;
        if (bothYes(left.getThemePalaceCultureYn(), right.getThemePalaceCultureYn())) count++;
        if (bothYes(left.getThemeNatureHangangYn(), right.getThemeNatureHangangYn())) count++;
        if (bothYes(left.getThemeDateYn(), right.getThemeDateYn())) count++;
        if (bothYes(left.getThemeFoodTourYn(), right.getThemeFoodTourYn())) count++;
        if (bothYes(left.getThemeCafeTourYn(), right.getThemeCafeTourYn())) count++;
        if (bothYes(left.getThemeShoppingHotplaceYn(), right.getThemeShoppingHotplaceYn())) count++;
        if (bothYes(left.getThemeNightViewYn(), right.getThemeNightViewYn())) count++;
        if (bothYes(left.getThemeHotelStayYn(), right.getThemeHotelStayYn())) count++;
        return count;
    }

    // DB 값이 "Y"일 때만 코드 일치 점수 18점을 반환한다.
    private double yesScore(String value) {
        return isYes(value) ? CODE_MATCH_SCORE : 0.0;
    }

    /**
     * 평점(0~5점)을 추천 점수 0~6점으로 환산한다.
     * 예: 평점 4.7 -> 4.7 / 5.0 * 6.0 = 5.64점
     */
    private double calculateRatingBonus(Place place) {
        return nullToZero(place.getRating()) / 5.0 * 6.0;
    }

    /**
     * 리뷰 수를 로그로 줄여서 0~4점의 보너스로 환산한다.
     * 리뷰 수가 아주 많더라도 최대 4점까지만 부여한다.
     * 로그를 사용하는 이유는 리뷰 10개와 100개의 차이보다
     * 리뷰 10,000개와 10,090개의 차이를 작게 반영하기 위해서다.
     */
    private double calculateReviewBonus(Place place) {
        int reviewCount = nullToZero(place.getReviewCount());
        return Math.min(4.0, Math.log10(reviewCount + 1.0) * 1.7);
    }

    /**
     * 입력된 여행 코드의 공백을 제거하고 대문자로 통일한다.
     * 현재는 길이가 5글자인지만 검사하며, 각 위치에 허용되는 글자인지는 검사하지 않는다.
     */
    private String normalizeTravelCode(String travelCode) {
        if (travelCode == null || travelCode.isBlank()) {
            throw new IllegalArgumentException("여행 유형 코드는 필수입니다.");
        }

        String normalized = travelCode.trim().toUpperCase();
        if (normalized.length() != 5) {
            throw new IllegalArgumentException("여행 유형 코드는 5글자여야 합니다.");
        }

        return normalized;
    }

    // 요청 개수가 비어 있거나 0 이하이면 기본값, 50보다 크면 50을 반환한다.
    private int resolveLimit(Integer value, int defaultValue) {
        if (value == null || value <= 0) {
            return defaultValue;
        }
        return Math.min(value, 50);
    }

    // 두 값이 모두 "Y"인지 확인한다. 테마 중복 개수를 셀 때 사용한다.
    private boolean bothYes(String left, String right) {
        return isYes(left) && isYes(right);
    }

    // 대소문자를 구분하지 않고 DB의 Y/N 값이 "Y"인지 확인한다.
    private boolean isYes(String value) {
        return "Y".equalsIgnoreCase(value);
    }

    // null끼리도 안전하게 비교할 수 있는 문자열 비교 함수이다.
    private boolean safeEquals(String left, String right) {
        if (left == null) {
            return right == null;
        }
        return left.equals(right);
    }

    // 평점이 null이면 계산 중 오류가 나지 않도록 0.0으로 바꾼다.
    private double nullToZero(Double value) {
        return value == null ? 0.0 : value;
    }

    // 리뷰 수가 null이면 계산 중 오류가 나지 않도록 0으로 바꾼다.
    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    // 소수점 첫째 자리까지 반올림한다. 예: 81.18 -> 81.2
    private double roundOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    // 장소와 계산된 점수를 함께 전달하기 위한 서비스 내부 전용 자료형이다.
    // DB 테이블이나 API JSON에는 ScoredPlace라는 이름으로 노출되지 않는다.
    private record ScoredPlace(Place place, Double score) {
    }
}

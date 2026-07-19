package com.seoulink.backend.domain.place.service;

import com.seoulink.backend.domain.place.dto.response.PlaceAlternativeResponse;
import com.seoulink.backend.domain.place.dto.response.PlaceRecommendationListResponse;
import com.seoulink.backend.domain.place.dto.response.PlaceRecommendationResponse;
import com.seoulink.backend.domain.place.entity.Place;
import com.seoulink.backend.domain.place.exception.InvalidTravelCodeException;
import com.seoulink.backend.domain.place.repository.PlaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class PlaceRecommendationService {

    // 요청에서 개수를 생략했을 때 반환할 대표 추천 장소 수
    private static final int DEFAULT_LIMIT = 10;

    // 대표 추천 장소 하나당 함께 반환할 대체 후보 수
    private static final int DEFAULT_ALTERNATIVE_LIMIT = 3;

    private static final int MAX_LIMIT = 50;
    private static final int MAX_LIMIT_PER_CATEGORY = 20;

    private static final Pattern DISTRICT_PATTERN = Pattern.compile("([가-힣]+구)");
    private static final Pattern NEIGHBORHOOD_PATTERN = Pattern.compile("([가-힣0-9]+동)");

    // 여행 코드 한 글자와 장소의 성향 태그가 일치할 때 얻는 점수
    // 5개 성향이 모두 일치하면 18점 * 5개 = 90점이다.
    private static final double CODE_MATCH_SCORE = 18.0;

    // 역할 1이 카테고리별 목표 개수를 정할 수 있도록 대표 후보에 기본 장소 종류를 고르게 포함한다.
    private static final List<String> SUPPORTED_CATEGORIES = List.of(
            "TOUR",
            "RESTAURANT",
            "CAFE",
            "HOTEL"
    );

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
        return recommend(travelCode, null, limit, null, alternativeLimit);
    }

    /**
     * 역할 1이 지역과 필요한 후보 규모를 지정해 호출하는 최종 추천 진입점이다.
     * limitPerCategory가 있으면 각 카테고리에서 해당 개수만큼 반환하고,
     * 없으면 기존 호환 방식대로 전체 limit 안에서 카테고리 대표를 우선 확보한다.
     */
    public PlaceRecommendationListResponse recommend(
            String travelCode,
            String region,
            Integer limit,
            Integer limitPerCategory,
            Integer alternativeLimit
    ) {
        // 예: " atbsp " -> "ATBSP"
        String normalizedTravelCode = normalizeTravelCode(travelCode);
        String normalizedRegion = normalizeRegion(region);

        // limit 값이 없거나 0 이하이면 기본값을 사용하고, 최대 50개까지만 허용한다.
        int resolvedLimit = resolveLimit(limit, DEFAULT_LIMIT);
        Integer resolvedLimitPerCategory = resolveLimitPerCategory(limitPerCategory);
        int resolvedAlternativeLimit = resolveLimit(alternativeLimit, DEFAULT_ALTERNATIVE_LIMIT);

        // 추천 대상으로 사용하도록 설정된 활성 장소만 가져온다.
        List<ScoredPlace> scoredPlaces = findActivePlaces(normalizedRegion)
                .stream()

                // Place와 계산된 점수를 한 쌍으로 묶는다.
                // DB의 PLACES 테이블에 사용자별 점수를 저장하지 않고 요청할 때마다 계산한다.
                .map(place -> scorePlace(place, normalizedTravelCode))

                // 평점이 높더라도 여행 코드 태그가 하나도 맞지 않으면 추천 대상에서 제외한다.
                .filter(scoredPlace -> scoredPlace.preferenceScore() > 0)

                // 추천 점수 -> 평점 -> 리뷰 수 -> placeId 순서로 정렬한다.
                .sorted(scoreComparator())
                .toList();

        List<ScoredPlace> recommended = resolvedLimitPerCategory == null
                ? selectCategoryBalancedPlaces(scoredPlaces, resolvedLimit)
                : selectPlacesPerCategory(scoredPlaces, resolvedLimitPerCategory);

        // 대표 추천 장소가 다른 장소의 대체 후보로 다시 들어가지 않도록 ID를 모아 둔다.
        Set<Long> unavailableAlternativeIds = recommended.stream()
                .map(scoredPlace -> scoredPlace.place().getPlaceId())
                .collect(Collectors.toSet());

        // 한 대체 장소가 여러 대표 장소에 반복되지 않도록 앞에서 사용한 ID를 계속 누적한다.
        List<PlaceRecommendationResponse> recommendedPlaces = new ArrayList<>();
        for (ScoredPlace scoredPlace : recommended) {
            PlaceRecommendationResponse response = toRecommendationResponse(
                    scoredPlace,
                    scoredPlaces,
                    unavailableAlternativeIds,
                    resolvedAlternativeLimit
            );
            recommendedPlaces.add(response);
            response.getAlternativeCandidates().forEach(
                    alternative -> unavailableAlternativeIds.add(alternative.getPlaceId())
            );
        }

        // 최종 JSON: { "travelCode": "ATBSP", "recommendedPlaces": [...] }
        return new PlaceRecommendationListResponse(normalizedTravelCode, recommendedPlaces);
    }

    /**
     * 대표 추천 장소 하나에 대체 장소 목록을 붙인다.
     */
    private PlaceRecommendationResponse toRecommendationResponse(
            ScoredPlace recommendedPlace,
            List<ScoredPlace> scoredPlaces,
            Set<Long> unavailableAlternativeIds,
            int alternativeLimit
    ) {
        List<PlaceAlternativeResponse> alternatives = scoredPlaces.stream()

                // 자기 자신은 자신의 대체 후보가 될 수 없다.
                .filter(candidate -> !candidate.place().getPlaceId().equals(recommendedPlace.place().getPlaceId()))

                // 이미 대표 추천 목록에 들어간 장소도 대체 후보에서는 제외한다.
                .filter(candidate -> !unavailableAlternativeIds.contains(candidate.place().getPlaceId()))

                // 코스 교체 후에도 식당이 관광지로 바뀌지 않도록 같은 기본 카테고리만 남긴다.
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
    private ScoredPlace scorePlace(Place place, String travelCode) {
        double preferenceScore = calculatePreferenceScore(place, travelCode);
        double score = preferenceScore;

        // 취향 코드 점수 외에 장소 자체의 신뢰도 지표를 보너스로 더한다.
        score += calculateRatingBonus(place);
        score += calculateReviewBonus(place);

        // 100점을 넘으면 100점으로 제한하고 소수점 첫째 자리로 반올림한다.
        return new ScoredPlace(
                place,
                roundOneDecimal(Math.min(score, 100.0)),
                preferenceScore
        );
    }

    /**
     * 위치별 의미가 확정된 5글자 코드와 장소 태그를 비교한다.
     * 1=A/H, 2=T/M, 3=L/B, 4=S/D, 5=P/R 순서이며 각 위치는 최대 18점이다.
     */
    private double calculatePreferenceScore(Place place, String travelCode) {
        return calculateCodeScore(place, travelCode.charAt(0))
                + calculateCodeScore(place, travelCode.charAt(1))
                + calculateCodeScore(place, travelCode.charAt(2))
                + calculateCodeScore(place, travelCode.charAt(3))
                + calculateCodeScore(place, travelCode.charAt(4));
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
     * normalizeTravelCode에서 각 위치의 허용 문자를 먼저 검증한 뒤 이 메서드가 호출된다.
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
     * 대체 후보 자격을 검사한다. 대체 후에도 하루 카테고리 목표가 유지되어야 하므로
     * TOUR·RESTAURANT·CAFE·HOTEL 기본 카테고리가 같은 장소만 허용한다.
     */
    private boolean isAlternativeFor(Place basePlace, Place candidatePlace) {
        return sameCategory(basePlace.getCategory(), candidatePlace.getCategory());
    }

    /**
     * 카테고리별 최고점 장소를 먼저 확보하고 나머지를 전체 점수 순으로 채운다.
     * 최종 응답은 다시 점수순으로 정렬하므로 recommendedPlaces의 순서 의미도 유지된다.
     */
    private List<ScoredPlace> selectCategoryBalancedPlaces(List<ScoredPlace> scoredPlaces, int limit) {
        if (limit <= 0 || scoredPlaces.isEmpty()) {
            return List.of();
        }

        List<ScoredPlace> selected = new ArrayList<>();
        Set<Long> selectedIds = new HashSet<>();

        List<ScoredPlace> categoryLeaders = SUPPORTED_CATEGORIES.stream()
                .map(category -> scoredPlaces.stream()
                        .filter(candidate -> sameCategory(candidate.place().getCategory(), category))
                        .findFirst()
                        .orElse(null))
                .filter(candidate -> candidate != null)
                .sorted(scoreComparator())
                .toList();

        for (ScoredPlace candidate : categoryLeaders) {
            if (selected.size() >= limit) break;
            addIfAbsent(selected, selectedIds, candidate);
        }

        for (ScoredPlace candidate : scoredPlaces) {
            if (selected.size() >= limit) break;
            addIfAbsent(selected, selectedIds, candidate);
        }

        return selected.stream()
                .sorted(scoreComparator())
                .toList();
    }

    /** 각 기본 카테고리에서 점수가 높은 후보를 동일한 상한으로 선별한다. */
    private List<ScoredPlace> selectPlacesPerCategory(List<ScoredPlace> scoredPlaces, int limitPerCategory) {
        return SUPPORTED_CATEGORIES.stream()
                .flatMap(category -> scoredPlaces.stream()
                        .filter(candidate -> sameCategory(candidate.place().getCategory(), category))
                        .limit(limitPerCategory))
                .sorted(scoreComparator())
                .toList();
    }

    private List<Place> findActivePlaces(String region) {
        if (region == null) {
            return placeRepository.findByIsActive("Y");
        }
        return placeRepository.findByRegionContainingAndIsActive(region, "Y");
    }

    private void addIfAbsent(List<ScoredPlace> selected, Set<Long> selectedIds, ScoredPlace candidate) {
        if (selectedIds.add(candidate.place().getPlaceId())) {
            selected.add(candidate);
        }
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
     * 각 위치는 팀에서 확정한 A/H, T/M, L/B, S/D, P/R 중 하나여야 한다.
     */
    private String normalizeTravelCode(String travelCode) {
        if (travelCode == null || travelCode.isBlank()) {
            throw new InvalidTravelCodeException("여행 유형 코드는 필수입니다.");
        }

        String normalized = travelCode.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() != 5) {
            throw new InvalidTravelCodeException("여행 유형 코드는 5글자여야 합니다.");
        }

        if (!normalized.matches("[AH][TM][LB][SD][PR]")) {
            throw new InvalidTravelCodeException(
                    "여행 유형 코드는 A/H, T/M, L/B, S/D, P/R 순서여야 합니다."
            );
        }

        return normalized;
    }

    // 요청 개수가 비어 있거나 0 이하이면 기본값, 50보다 크면 50을 반환한다.
    private int resolveLimit(Integer value, int defaultValue) {
        if (value == null || value <= 0) {
            return defaultValue;
        }
        return Math.min(value, MAX_LIMIT);
    }

    private Integer resolveLimitPerCategory(Integer value) {
        if (value == null) {
            return null;
        }
        if (value <= 0) {
            return 1;
        }
        return Math.min(value, MAX_LIMIT_PER_CATEGORY);
    }

    /** 서울 전체 선택은 필터링하지 않고, 구·동처럼 구체적인 지역만 DB 조회에 적용한다. */
    private String normalizeRegion(String region) {
        if (region == null || region.isBlank()) {
            return null;
        }

        String normalized = region.trim();
        String compact = normalized.replaceAll("\\s+", "");
        if (compact.equals("서울")
                || compact.equals("서울특별시")
                || compact.equals("서울전체")
                || compact.equals("전체")) {
            return null;
        }

        String localArea = compact
                .replaceFirst("^서울특별시", "")
                .replaceFirst("^서울시", "")
                .replaceFirst("^서울", "");

        Matcher districtMatcher = DISTRICT_PATTERN.matcher(localArea);
        if (districtMatcher.find()) {
            return districtMatcher.group(1);
        }

        Matcher neighborhoodMatcher = NEIGHBORHOOD_PATTERN.matcher(localArea);
        if (neighborhoodMatcher.find()) {
            return neighborhoodMatcher.group(1);
        }
        return normalized;
    }

    // 두 값이 모두 "Y"인지 확인한다. 테마 중복 개수를 셀 때 사용한다.
    private boolean bothYes(String left, String right) {
        return isYes(left) && isYes(right);
    }

    // 대소문자를 구분하지 않고 DB의 Y/N 값이 "Y"인지 확인한다.
    private boolean isYes(String value) {
        return "Y".equalsIgnoreCase(value);
    }

    private boolean sameCategory(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
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
    private record ScoredPlace(Place place, Double score, Double preferenceScore) {
    }
}

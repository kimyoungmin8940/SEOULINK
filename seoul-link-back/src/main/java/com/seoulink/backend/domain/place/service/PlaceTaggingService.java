package com.seoulink.backend.domain.place.service;

import com.seoulink.backend.domain.place.entity.Place;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class PlaceTaggingService {

    private static final int PACKED_MAX_STAY_MINUTES = 90;

    public void applyTags(Place place) {
        resetRecommendationFields(place);

        String name = normalize(place.getName());
        String apiCategory = normalize(place.getApiCategory());
        String address = normalize(place.getAddress());
        String roadAddress = normalize(place.getRoadAddress());
        String region = normalize(place.getRegion());
        String semanticText = name + " " + apiCategory;
        String locationText = address + " " + roadAddress + " " + region;

        applyApiCategory(place, apiCategory);
        applyKeywords(place, name, semanticText, locationText);
        applySpecialPlaces(place, name);

        int avgStayMinutes = estimateStayMinutes(place);
        place.setAvgStayMinutes(avgStayMinutes);
        applyScheduleDensityTag(place, avgStayMinutes);
    }

    private void applyApiCategory(Place place, String apiCategory) {
        // 카카오 카테고리는 "음식점 > 카페"처럼 상위·하위 분류가 함께 올 수 있다.
        // 더 구체적인 카테고리를 먼저 검사해야 카페가 음식점으로 덮이지 않는다.
        if (containsAny(apiCategory, "숙박", "호텔", "게스트하우스", "호스텔")) {
            place.setCategory("HOTEL");
            place.setThemeHotelStayYn("Y");
            place.setTagStable("Y");
            place.setTagLuxury("Y");
        } else if (containsAny(apiCategory, "카페", "커피", "디저트")) {
            place.setCategory("CAFE");
            place.setThemeCafeTourYn("Y");
            place.setTagModern("Y");
            place.setTagStable("Y");
        } else if (containsAny(apiCategory, "음식점", "한식", "양식", "중식", "일식", "맛집")) {
            place.setCategory("RESTAURANT");
            place.setThemeFoodTourYn("Y");
            place.setTagBudget("Y");
        } else if (containsAny(apiCategory, "관광명소", "문화시설", "박물관", "미술관", "공연장")) {
            place.setCategory("TOUR");
        }

        if (containsAny(apiCategory, "쇼핑", "백화점", "쇼핑몰", "시장")) {
            place.setCategory("TOUR");
            place.setThemeShoppingHotplaceYn("Y");
            place.setTagModern("Y");
            place.setTagDopamine("Y");
        }

        // 관광명소라는 API 분류만으로는 역사 장소라고 볼 수 없다.
        // 문화시설 또는 구체적인 문화 키워드가 있을 때만 궁궐·문화 테마를 부여한다.
        if (containsAny(apiCategory, "문화시설", "박물관", "미술관", "공연장")) {
            place.setThemePalaceCultureYn("Y");
            place.setTagHistory("Y");
            place.setTagStable("Y");
        }
    }

    private void applyKeywords(Place place, String name, String semanticText, String locationText) {
        // 장소 성격 태그는 주소가 아니라 장소명과 API 카테고리에서 판단한다.
        // 예를 들어 용산구 주소의 "산" 때문에 모든 장소가 자연형이 되는 오분류를 막는다.
        if (containsAny(name, "궁", "궁궐")
                || containsAny(semanticText, "한옥", "박물관", "미술관", "역사관", "유적", "문화재")) {
            place.setThemePalaceCultureYn("Y");
            place.setTagHistory("Y");
            place.setTagStable("Y");
        }

        if (containsAny(
                semanticText,
                "한강", "공원", "숲", "수목원", "식물원", "자락길", "둘레길", "산책", "청계천",
                "남산", "북한산", "관악산", "인왕산", "아차산", "응봉산", "도봉산", "수락산", "불암산", "청계산"
        )) {
            place.setThemeNatureHangangYn("Y");
            place.setTagRelax("Y");
            place.setTagStable("Y");
            place.setTagDopamine("Y");
        }

        if (containsAny(semanticText, "데이트", "호수", "한강", "전망대", "아쿠아리움", "공연", "거리")) {
            place.setThemeDateYn("Y");
            place.setTagModern("Y");
        }

        if (containsAny(semanticText, "시장", "백화점", "쇼핑몰", "거리", "팝업", "핫플", "성수", "홍대", "명동")
                || containsAny(locationText, "성수동", "홍대입구", "명동")) {
            place.setThemeShoppingHotplaceYn("Y");
            place.setTagModern("Y");
            place.setTagBudget("Y");
        }

        if (containsAny(semanticText, "타워", "전망대", "야경", "루프톱", "스카이", "옥상", "팔각정")) {
            place.setThemeNightViewYn("Y");
            place.setThemeDateYn("Y");
            place.setTagDopamine("Y");
            place.setTagModern("Y");
        }
    }

    private void applySpecialPlaces(Place place, String name) {
        if (containsAny(name, "경복궁", "창덕궁", "덕수궁", "창경궁", "종묘")) {
            place.setThemePalaceCultureYn("Y");
            place.setTagHistory("Y");
            place.setTagBudget("Y");
            place.setTagStable("Y");
        }

        if (containsAny(name, "서울숲", "여의도한강공원", "반포한강공원", "노들섬")) {
            place.setThemeNatureHangangYn("Y");
            place.setThemeDateYn("Y");
            place.setTagRelax("Y");
            place.setTagStable("Y");
        }

        if (containsAny(name, "익선동", "연남동", "석촌호수", "해방촌", "성수")) {
            place.setThemeDateYn("Y");
            place.setThemeShoppingHotplaceYn("Y");
            place.setTagModern("Y");
            place.setTagDopamine("Y");
        }

        if (containsAny(name, "n서울타워", "남산서울타워", "서울스카이", "응봉산", "낙산공원")) {
            place.setThemeNightViewYn("Y");
            place.setThemeDateYn("Y");
            place.setTagDopamine("Y");
            place.setTagModern("Y");
        }

        if (containsAny(name, "광장시장", "망원시장", "남대문시장", "통인시장")) {
            place.setThemeFoodTourYn("Y");
            place.setThemeShoppingHotplaceYn("Y");
            place.setTagBudget("Y");
            place.setTagHistory("Y");
        }
    }

    private int estimateStayMinutes(Place place) {
        if (isYes(place.getThemeHotelStayYn())) return 720;
        if (isYes(place.getThemeFoodTourYn())) return 70;
        if (isYes(place.getThemeCafeTourYn())) return 60;
        if (isYes(place.getThemePalaceCultureYn())) return 120;
        if (isYes(place.getThemeNatureHangangYn())) return 90;
        return 60;
    }

    /**
     * P형은 하루에 여러 장소를 방문하므로 체류시간이 90분 이하인 장소를 우선 활용한다.
     * 숙소는 체크인 지점에 가까워 짧은 체류시간 장소로 취급하지 않는다.
     */
    private void applyScheduleDensityTag(Place place, int avgStayMinutes) {
        if (!isYes(place.getThemeHotelStayYn()) && avgStayMinutes <= PACKED_MAX_STAY_MINUTES) {
            place.setTagPacked("Y");
        }
    }

    private void resetRecommendationFields(Place place) {
        place.setTagHistory("N");
        place.setTagModern("N");
        place.setTagBudget("N");
        place.setTagLuxury("N");
        place.setTagStable("N");
        place.setTagDopamine("N");
        place.setTagRelax("N");
        place.setTagPacked("N");
        place.setThemePalaceCultureYn("N");
        place.setThemeNatureHangangYn("N");
        place.setThemeDateYn("N");
        place.setThemeFoodTourYn("N");
        place.setThemeCafeTourYn("N");
        place.setThemeShoppingHotplaceYn("N");
        place.setThemeNightViewYn("N");
        place.setThemeHotelStayYn("N");
    }

    private boolean containsAny(String text, String... keywords) {
        if (text == null || text.isBlank()) return false;
        for (String keyword : keywords) {
            if (text.contains(normalize(keyword))) return true;
        }
        return false;
    }

    private boolean isYes(String value) {
        return "Y".equalsIgnoreCase(value);
    }

    private String normalize(String value) {
        if (value == null) return "";
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }
}

package com.seoulink.backend.domain.place.service;

import com.seoulink.backend.domain.place.entity.Place;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class PlaceTaggingService {

    public void applyTags(Place place) {
        resetRecommendationFields(place);

        String name = normalize(place.getName());
        String apiCategory = normalize(place.getApiCategory());
        String address = normalize(place.getAddress());
        String region = normalize(place.getRegion());
        String text = name + " " + apiCategory + " " + address + " " + region;

        applyApiCategory(place, apiCategory);
        applyKeywords(place, text);
        applySpecialPlaces(place, name);
        place.setAvgStayMinutes(estimateStayMinutes(place));
    }

    private void applyApiCategory(Place place, String apiCategory) {
        if (containsAny(apiCategory, "음식점", "한식", "양식", "중식", "일식", "맛집")) {
            place.setCategory("RESTAURANT");
            place.setThemeFoodTourYn("Y");
            place.setTagBudget("Y");
        }

        if (containsAny(apiCategory, "카페", "커피", "디저트")) {
            place.setCategory("CAFE");
            place.setThemeCafeTourYn("Y");
            place.setTagModern("Y");
            place.setTagStable("Y");
        }

        if (containsAny(apiCategory, "숙박", "호텔", "게스트하우스", "호스텔")) {
            place.setCategory("HOTEL");
            place.setThemeHotelStayYn("Y");
            place.setTagStable("Y");
            place.setTagLuxury("Y");
        }

        if (containsAny(apiCategory, "쇼핑", "백화점", "쇼핑몰", "시장")) {
            place.setThemeShoppingHotplaceYn("Y");
            place.setTagModern("Y");
            place.setTagDopamine("Y");
        }

        if (containsAny(apiCategory, "문화시설", "박물관", "미술관", "공연장", "관광명소")) {
            place.setThemePalaceCultureYn("Y");
            place.setTagHistory("Y");
            place.setTagStable("Y");
        }
    }

    private void applyKeywords(Place place, String text) {
        if (containsAny(text, "궁", "궁궐", "한옥", "박물관", "미술관", "역사관", "유적", "문화재")) {
            place.setThemePalaceCultureYn("Y");
            place.setTagHistory("Y");
            place.setTagStable("Y");
        }

        if (containsAny(text, "한강", "공원", "숲", "산", "수목원", "식물원", "자락길", "청계천")) {
            place.setThemeNatureHangangYn("Y");
            place.setTagRelax("Y");
            place.setTagStable("Y");
        }

        if (containsAny(text, "데이트", "호수", "한강", "전망대", "아쿠아리움", "공연", "거리")) {
            place.setThemeDateYn("Y");
            place.setTagModern("Y");
        }

        if (containsAny(text, "시장", "백화점", "쇼핑몰", "거리", "팝업", "성수", "홍대", "명동")) {
            place.setThemeShoppingHotplaceYn("Y");
            place.setTagModern("Y");
            place.setTagBudget("Y");
        }

        if (containsAny(text, "타워", "전망대", "야경", "루프톱", "스카이", "옥상", "팔각정")) {
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

package com.seoulink.backend.infrastructure.importer.kakao;

import java.util.ArrayList;
import java.util.List;

public record PlaceSeed(
        String name,
        String category,
        String searchKeyword,

        Long contentId,
        Double rating,
        Integer reviewCount,
        String description,
        String imageUrl,

        List<PlaceTheme> themes,
        Boolean tagHistory,
        Boolean tagModern,
        Boolean tagBudget,
        Boolean tagLuxury,
        Boolean tagStable,
        Boolean tagDopamine,
        Boolean tagRelax,
        Boolean tagPacked,

        String sourceType,
        String recommendYn,
        String approvalStatus,
        Long createdByMemberId,

        Boolean indoor,
        Boolean rainOk,
        Boolean nightOk,
        Integer avgStayMinutes,
        PriceLevel priceLevel,
        String isActive
) {

    public static Builder builder(String name, String category, List<PlaceTheme> themes) {
        return new Builder(name, category, themes);
    }

    public String tagHistoryYn() {
        return yn(tagHistory != null ? tagHistory : themes.contains(PlaceTheme.PALACE_CULTURE));
    }

    public String tagModernYn() {
        boolean defaultValue = themes.contains(PlaceTheme.DATE)
                || themes.contains(PlaceTheme.CAFE_TOUR)
                || themes.contains(PlaceTheme.SHOPPING_HOTPLACE)
                || themes.contains(PlaceTheme.NIGHT_VIEW)
                || themes.contains(PlaceTheme.EXHIBITION);
        return yn(tagModern != null ? tagModern : defaultValue);
    }

    public String tagBudgetYn() {
        boolean defaultValue = themes.contains(PlaceTheme.BUDGET)
                || themes.contains(PlaceTheme.FOOD_TOUR);
        return yn(tagBudget != null ? tagBudget : defaultValue);
    }

    public String tagLuxuryYn() {
        boolean defaultValue = themes.contains(PlaceTheme.LUXURY)
                || themes.contains(PlaceTheme.HOTEL_STAY)
                || themes.contains(PlaceTheme.SHOPPING_HOTPLACE);
        return yn(tagLuxury != null ? tagLuxury : defaultValue);
    }

    public String tagStableYn() {
        boolean defaultValue = themes.contains(PlaceTheme.PALACE_CULTURE)
                || themes.contains(PlaceTheme.NATURE_HANGANG)
                || themes.contains(PlaceTheme.EXHIBITION)
                || themes.contains(PlaceTheme.INDOOR)
                || themes.contains(PlaceTheme.RAINY_DAY);
        return yn(tagStable != null ? tagStable : defaultValue);
    }

    public String tagDopamineYn() {
        boolean defaultValue = themes.contains(PlaceTheme.SHOPPING_HOTPLACE)
                || themes.contains(PlaceTheme.NIGHT_VIEW)
                || themes.contains(PlaceTheme.FOOD_TOUR);
        return yn(tagDopamine != null ? tagDopamine : defaultValue);
    }

    public String tagRelaxYn() {
        boolean defaultValue = themes.contains(PlaceTheme.NATURE_HANGANG)
                || themes.contains(PlaceTheme.DATE)
                || themes.contains(PlaceTheme.CAFE_TOUR)
                || themes.contains(PlaceTheme.HOTEL_STAY)
                || themes.contains(PlaceTheme.INDOOR)
                || themes.contains(PlaceTheme.RAINY_DAY);
        return yn(tagRelax != null ? tagRelax : defaultValue);
    }

    public String tagPackedYn() {
        boolean defaultValue = themes.contains(PlaceTheme.PALACE_CULTURE)
                || themes.contains(PlaceTheme.SHOPPING_HOTPLACE)
                || themes.contains(PlaceTheme.NIGHT_VIEW)
                || themes.contains(PlaceTheme.FOOD_TOUR);
        return yn(tagPacked != null ? tagPacked : defaultValue);
    }

    public String indoorYn() {
        boolean defaultValue = themes.contains(PlaceTheme.INDOOR)
                || themes.contains(PlaceTheme.RAINY_DAY)
                || themes.contains(PlaceTheme.EXHIBITION)
                || themes.contains(PlaceTheme.CAFE_TOUR)
                || themes.contains(PlaceTheme.HOTEL_STAY)
                || themes.contains(PlaceTheme.SHOPPING_HOTPLACE);
        return yn(indoor != null ? indoor : defaultValue);
    }

    public String rainOkYn() {
        boolean defaultValue = themes.contains(PlaceTheme.RAINY_DAY)
                || themes.contains(PlaceTheme.INDOOR)
                || themes.contains(PlaceTheme.EXHIBITION)
                || themes.contains(PlaceTheme.CAFE_TOUR)
                || themes.contains(PlaceTheme.HOTEL_STAY)
                || themes.contains(PlaceTheme.SHOPPING_HOTPLACE);
        return yn(rainOk != null ? rainOk : defaultValue);
    }

    public String nightOkYn() {
        boolean defaultValue = themes.contains(PlaceTheme.NIGHT_VIEW)
                || themes.contains(PlaceTheme.DATE)
                || themes.contains(PlaceTheme.SHOPPING_HOTPLACE)
                || themes.contains(PlaceTheme.HOTEL_STAY);
        return yn(nightOk != null ? nightOk : defaultValue);
    }

    public Integer resolvedAvgStayMinutes() {
        if (avgStayMinutes != null) {
            return avgStayMinutes;
        }

        if (themes.contains(PlaceTheme.HOTEL_STAY) || "HOTEL".equals(category)) {
            return 720;
        }

        if (themes.contains(PlaceTheme.SHOPPING_HOTPLACE) || themes.contains(PlaceTheme.EXHIBITION)) {
            return 120;
        }

        if (themes.contains(PlaceTheme.PALACE_CULTURE) || themes.contains(PlaceTheme.NATURE_HANGANG)) {
            return 90;
        }

        if ("RESTAURANT".equals(category) || "CAFE".equals(category)) {
            return 60;
        }

        return 60;
    }

    public PriceLevel resolvedPriceLevel() {
        if (priceLevel != null) {
            return priceLevel;
        }

        if (themes.contains(PlaceTheme.LUXURY)
                || themes.contains(PlaceTheme.HOTEL_STAY)
                || "HOTEL".equals(category)) {
            return PriceLevel.HIGH;
        }

        if (themes.contains(PlaceTheme.BUDGET) || themes.contains(PlaceTheme.FOOD_TOUR)) {
            return PriceLevel.LOW;
        }

        return PriceLevel.MEDIUM;
    }

    private static String yn(boolean value) {
        return value ? "Y" : "N";
    }

    public static class Builder {
        private final String name;
        private final String category;
        private final List<PlaceTheme> themes;

        private String searchKeyword;
        private Long contentId;
        private Double rating = 0.0;
        private Integer reviewCount = 0;
        private String description;
        private String imageUrl;

        private Boolean tagHistory;
        private Boolean tagModern;
        private Boolean tagBudget;
        private Boolean tagLuxury;
        private Boolean tagStable;
        private Boolean tagDopamine;
        private Boolean tagRelax;
        private Boolean tagPacked;

        private String sourceType = "RECOMMEND";
        private String recommendYn = "Y";
        private String approvalStatus = "APPROVED";
        private Long createdByMemberId;

        private Boolean indoor;
        private Boolean rainOk;
        private Boolean nightOk;
        private Integer avgStayMinutes;
        private PriceLevel priceLevel;
        private String isActive = "Y";

        private Builder(String name, String category, List<PlaceTheme> themes) {
            this.name = name;
            this.category = category;
            this.searchKeyword = name;
            this.themes = themes == null ? List.of() : new ArrayList<>(themes);
        }

        public Builder searchKeyword(String searchKeyword) {
            this.searchKeyword = searchKeyword;
            return this;
        }

        public Builder contentId(Long contentId) {
            this.contentId = contentId;
            return this;
        }

        public Builder rating(Double rating) {
            this.rating = rating;
            return this;
        }

        public Builder reviewCount(Integer reviewCount) {
            this.reviewCount = reviewCount;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder imageUrl(String imageUrl) {
            this.imageUrl = imageUrl;
            return this;
        }

        public Builder tags(
                boolean tagHistory,
                boolean tagModern,
                boolean tagBudget,
                boolean tagLuxury,
                boolean tagStable,
                boolean tagDopamine,
                boolean tagRelax,
                boolean tagPacked
        ) {
            this.tagHistory = tagHistory;
            this.tagModern = tagModern;
            this.tagBudget = tagBudget;
            this.tagLuxury = tagLuxury;
            this.tagStable = tagStable;
            this.tagDopamine = tagDopamine;
            this.tagRelax = tagRelax;
            this.tagPacked = tagPacked;
            return this;
        }

        public Builder sourceType(String sourceType) {
            this.sourceType = sourceType;
            return this;
        }

        public Builder recommendYn(String recommendYn) {
            this.recommendYn = recommendYn;
            return this;
        }

        public Builder approvalStatus(String approvalStatus) {
            this.approvalStatus = approvalStatus;
            return this;
        }

        public Builder createdByMemberId(Long createdByMemberId) {
            this.createdByMemberId = createdByMemberId;
            return this;
        }

        public Builder condition(boolean indoor, boolean rainOk, boolean nightOk) {
            this.indoor = indoor;
            this.rainOk = rainOk;
            this.nightOk = nightOk;
            return this;
        }

        public Builder avgStayMinutes(Integer avgStayMinutes) {
            this.avgStayMinutes = avgStayMinutes;
            return this;
        }

        public Builder priceLevel(PriceLevel priceLevel) {
            this.priceLevel = priceLevel;
            return this;
        }

        public Builder isActive(String isActive) {
            this.isActive = isActive;
            return this;
        }

        public PlaceSeed build() {
            return new PlaceSeed(
                    name,
                    category,
                    searchKeyword,
                    contentId,
                    rating,
                    reviewCount,
                    description,
                    imageUrl,
                    List.copyOf(themes),
                    tagHistory,
                    tagModern,
                    tagBudget,
                    tagLuxury,
                    tagStable,
                    tagDopamine,
                    tagRelax,
                    tagPacked,
                    sourceType,
                    recommendYn,
                    approvalStatus,
                    createdByMemberId,
                    indoor,
                    rainOk,
                    nightOk,
                    avgStayMinutes,
                    priceLevel,
                    isActive
            );
        }
    }
}

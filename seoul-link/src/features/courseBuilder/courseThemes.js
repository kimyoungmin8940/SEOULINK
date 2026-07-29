export const MARKER_ICON_BY_THEME = {
    ALL: "/markers/theme-all-marker.png",
    PALACE_CULTURE: "/markers/theme-palace-culture-marker.png",
    NATURE_HANGANG: "/markers/theme-nature-hangang-marker.png",
    DATE: "/markers/theme-date-marker.png",
    FOOD_TOUR: "/markers/theme-food-tour-marker.png",
    CAFE_TOUR: "/markers/theme-cafe-tour-marker.png",
    SHOPPING_HOTPLACE: "/markers/theme-shopping-hotplace-marker.png",
    NIGHT_VIEW: "/markers/theme-night-view-marker.png",
    HOTEL_STAY: "/markers/theme-hotel-stay-marker.png",
};

export const MARKER_ICON_BY_BASE_CATEGORY = {
    TOUR: MARKER_ICON_BY_THEME.PALACE_CULTURE,
    RESTAURANT: MARKER_ICON_BY_THEME.FOOD_TOUR,
    CAFE: MARKER_ICON_BY_THEME.CAFE_TOUR,
    HOTEL: MARKER_ICON_BY_THEME.HOTEL_STAY,
};

export const COURSE_THEMES = [
    { value: "ALL", label: "전체", icon: MARKER_ICON_BY_THEME.ALL },
    {
        value: "PALACE_CULTURE",
        label: "궁궐, 문화",
        icon: MARKER_ICON_BY_THEME.PALACE_CULTURE,
        markerCategory: "TOUR",
        kakaoCategories: [{ groupCode: "AT4", baseCategory: "TOUR" }],
        kakaoKeywords: ["궁궐", "문화재", "박물관", "미술관", "전시관", "공연장"],
    },
    {
        value: "NATURE_HANGANG",
        label: "자연, 한강",
        icon: MARKER_ICON_BY_THEME.NATURE_HANGANG,
        markerCategory: "TOUR",
        kakaoCategories: [],
        kakaoKeywords: ["한강공원", "공원", "숲길", "산책로", "서울숲", "남산"],
    },
    {
        value: "DATE",
        label: "데이트",
        icon: MARKER_ICON_BY_THEME.DATE,
        markerCategory: "TOUR",
        kakaoCategories: [
            { groupCode: "CE7", baseCategory: "CAFE" },
            { groupCode: "FD6", baseCategory: "RESTAURANT" },
            { groupCode: "AT4", baseCategory: "TOUR" },
        ],
        kakaoKeywords: ["데이트", "전시", "미술관", "전망대", "루프탑", "한강공원"],
    },
    {
        value: "FOOD_TOUR",
        label: "맛집 탐방",
        icon: MARKER_ICON_BY_THEME.FOOD_TOUR,
        markerCategory: "RESTAURANT",
        kakaoCategories: [{ groupCode: "FD6", baseCategory: "RESTAURANT" }],
        kakaoKeywords: [],
    },
    {
        value: "CAFE_TOUR",
        label: "카페투어",
        icon: MARKER_ICON_BY_THEME.CAFE_TOUR,
        markerCategory: "CAFE",
        kakaoCategories: [{ groupCode: "CE7", baseCategory: "CAFE" }],
        kakaoKeywords: ["디저트카페", "베이커리카페", "루프탑카페"],
    },
    {
        value: "SHOPPING_HOTPLACE",
        label: "쇼핑, 핫플",
        icon: MARKER_ICON_BY_THEME.SHOPPING_HOTPLACE,
        markerCategory: "TOUR",
        kakaoCategories: [],
        kakaoKeywords: [
            "쇼핑몰",
            "백화점",
            "복합쇼핑몰",
            "아울렛",
            "전통시장",
            "편집샵",
            "소품샵",
            "쇼룸",
            "팝업스토어",
            "성수 편집샵",
            "성수 소품샵",
            "성수 쇼룸",
            "성수 팝업스토어",
        ],
    },
    {
        value: "NIGHT_VIEW",
        label: "야경",
        icon: MARKER_ICON_BY_THEME.NIGHT_VIEW,
        markerCategory: "TOUR",
        kakaoCategories: [],
        kakaoKeywords: ["야경", "전망대", "서울타워", "남산", "낙산공원", "한강공원"],
    },
    {
        value: "HOTEL_STAY",
        label: "숙소",
        icon: MARKER_ICON_BY_THEME.HOTEL_STAY,
        markerCategory: "HOTEL",
        kakaoCategories: [{ groupCode: "AD5", baseCategory: "HOTEL" }],
        kakaoKeywords: ["호텔", "숙소", "게스트하우스"],
    },
];

export const SEARCHABLE_THEMES = COURSE_THEMES.filter((theme) => theme.value !== "ALL");

export const THEME_BY_VALUE = COURSE_THEMES.reduce((acc, theme) => {
    acc[theme.value] = theme;
    return acc;
}, {});

export const DEFAULT_FOOD_SUBCATEGORY = "ALL";

export const FOOD_SUBCATEGORIES = [
    { value: "ALL", label: "전체", keywords: [] },
    { value: "KOREAN", label: "한식", keywords: ["한식", "한식 맛집", "백반", "국밥", "찌개", "한정식", "고기집"] },
    { value: "WESTERN", label: "양식", keywords: ["양식", "양식 맛집", "파스타", "스테이크", "브런치", "피자"] },
    { value: "CHINESE", label: "중식", keywords: ["중식", "중국집", "짜장면", "짬뽕", "마라탕", "딤섬"] },
    { value: "JAPANESE", label: "일식", keywords: ["일식", "초밥", "라멘", "돈카츠", "이자카야", "우동"] },
    { value: "CAFE_DESSERT", label: "디저트", keywords: ["디저트", "베이커리", "케이크", "도넛", "아이스크림"] },
];

export const FOOD_SUBCATEGORY_BY_VALUE = FOOD_SUBCATEGORIES.reduce((acc, subcategory) => {
    acc[subcategory.value] = subcategory;
    return acc;
}, {});

import { useCallback, useEffect, useRef, useState } from "react";
import { loadKakaoMap } from "./lib/loadKakaoMap";
import {
    calculateCourseBuilderRoutes,
    fetchCourseBuilderDbPlaces,
    saveCourseBuilderCourse,
} from "./api/courseBuilderApi";
import "./CourseBuilder.css";

const DEFAULT_REGION = "서울";
const TEST_MEMBER_ID = 1;

const DEFAULT_MAP_LEVEL_SEOUL = 8;
const DEFAULT_MAP_LEVEL_GU = 6;
// 장소명 클릭 이동 시 약 30km 범위가 보이도록 사용하는 지도 레벨
const PLACE_FOCUS_MAP_LEVEL_30KM = 8;
const KAKAO_PAGE_SIZE = 15;

// 전체 선택: 테마별 최대 30개
const MAX_COUNT_PER_THEME_ALL = 30;

// 테마 하나 선택: 해당 테마 최대 120개
const MAX_COUNT_SINGLE_THEME = 120;

const AUTO_SEARCH_DELAY_MS = 900;
const AUTO_ROUTE_CALC_DELAY_MS = 500;
const MOVE_MAP_WAIT_MS = 500;

const MARKER_IMAGE_BY_THEME = {
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

const FALLBACK_MARKER_IMAGE_BY_CATEGORY = {
    TOUR: MARKER_IMAGE_BY_THEME.PALACE_CULTURE,
    RESTAURANT: MARKER_IMAGE_BY_THEME.FOOD_TOUR,
    CAFE: MARKER_IMAGE_BY_THEME.CAFE_TOUR,
    HOTEL: MARKER_IMAGE_BY_THEME.HOTEL_STAY,
};

const SEOUL_REGION_CENTERS = {
    서울: { latitude: 37.5665, longitude: 126.978 },
    강남구: { latitude: 37.5172, longitude: 127.0473 },
    강동구: { latitude: 37.5301, longitude: 127.1238 },
    강북구: { latitude: 37.6396, longitude: 127.0257 },
    강서구: { latitude: 37.5509, longitude: 126.8495 },
    관악구: { latitude: 37.4784, longitude: 126.9516 },
    광진구: { latitude: 37.5384, longitude: 127.0823 },
    구로구: { latitude: 37.4955, longitude: 126.8877 },
    금천구: { latitude: 37.4569, longitude: 126.8955 },
    노원구: { latitude: 37.6542, longitude: 127.0568 },
    도봉구: { latitude: 37.6688, longitude: 127.0471 },
    동대문구: { latitude: 37.5744, longitude: 127.0396 },
    동작구: { latitude: 37.5124, longitude: 126.9393 },
    마포구: { latitude: 37.5663, longitude: 126.9019 },
    서대문구: { latitude: 37.5791, longitude: 126.9368 },
    서초구: { latitude: 37.4837, longitude: 127.0324 },
    성동구: { latitude: 37.5633, longitude: 127.0371 },
    성북구: { latitude: 37.5894, longitude: 127.0167 },
    송파구: { latitude: 37.5145, longitude: 127.1059 },
    양천구: { latitude: 37.5169, longitude: 126.8664 },
    영등포구: { latitude: 37.5264, longitude: 126.8962 },
    용산구: { latitude: 37.5326, longitude: 126.9905 },
    은평구: { latitude: 37.6027, longitude: 126.9291 },
    종로구: { latitude: 37.5735, longitude: 126.979 },
    중구: { latitude: 37.5636, longitude: 126.9976 },
    중랑구: { latitude: 37.6063, longitude: 127.0927 },
};

const SEOUL_REGIONS = [
    "서울",
    "강남구",
    "강동구",
    "강북구",
    "강서구",
    "관악구",
    "광진구",
    "구로구",
    "금천구",
    "노원구",
    "도봉구",
    "동대문구",
    "동작구",
    "마포구",
    "서대문구",
    "서초구",
    "성동구",
    "성북구",
    "송파구",
    "양천구",
    "영등포구",
    "용산구",
    "은평구",
    "종로구",
    "중구",
    "중랑구",
];

const THEME_BUTTONS = [
    {
        value: "ALL",
        label: "전체",
        icon: MARKER_IMAGE_BY_THEME.ALL,
    },
    {
        value: "PALACE_CULTURE",
        label: "궁궐, 문화",
        icon: MARKER_IMAGE_BY_THEME.PALACE_CULTURE,
        markerCategory: "TOUR",
        kakaoCategories: [{ groupCode: "AT4", baseCategory: "TOUR" }],
        kakaoKeywords: ["궁궐", "문화재", "박물관", "미술관", "전시관", "공연장"],
    },
    {
        value: "NATURE_HANGANG",
        label: "자연, 한강",
        icon: MARKER_IMAGE_BY_THEME.NATURE_HANGANG,
        markerCategory: "TOUR",
        kakaoCategories: [],
        kakaoKeywords: ["한강공원", "공원", "숲길", "산책로", "서울숲", "남산"],
    },
    {
        value: "DATE",
        label: "데이트",
        icon: MARKER_IMAGE_BY_THEME.DATE,
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
        icon: MARKER_IMAGE_BY_THEME.FOOD_TOUR,
        markerCategory: "RESTAURANT",
        // 맛집은 기존 음식점과 동일하게 카카오 음식점 카테고리 FD6으로 가져옴
        kakaoCategories: [{ groupCode: "FD6", baseCategory: "RESTAURANT" }],
        kakaoKeywords: [],
    },
    {
        value: "CAFE_TOUR",
        label: "카페투어",
        icon: MARKER_IMAGE_BY_THEME.CAFE_TOUR,
        markerCategory: "CAFE",
        kakaoCategories: [{ groupCode: "CE7", baseCategory: "CAFE" }],
        kakaoKeywords: ["디저트카페", "베이커리카페", "루프탑카페"],
    },
    {
        value: "SHOPPING_HOTPLACE",
        label: "쇼핑, 핫플",
        icon: MARKER_IMAGE_BY_THEME.SHOPPING_HOTPLACE,
        markerCategory: "TOUR",
        kakaoCategories: [],
        kakaoKeywords: ["쇼핑몰", "백화점", "시장", "편집샵", "핫플", "성수"],
    },
    {
        value: "NIGHT_VIEW",
        label: "야경",
        icon: MARKER_IMAGE_BY_THEME.NIGHT_VIEW,
        markerCategory: "TOUR",
        kakaoCategories: [],
        kakaoKeywords: ["야경", "전망대", "서울타워", "남산", "낙산공원", "한강공원"],
    },
    {
        value: "HOTEL_STAY",
        label: "숙소",
        icon: MARKER_IMAGE_BY_THEME.HOTEL_STAY,
        markerCategory: "HOTEL",
        kakaoCategories: [{ groupCode: "AD5", baseCategory: "HOTEL" }],
        kakaoKeywords: ["호텔", "숙소", "게스트하우스"],
    },
];

const SEARCH_THEMES = THEME_BUTTONS.filter((theme) => theme.value !== "ALL");

const THEME_CONFIG_BY_VALUE = THEME_BUTTONS.reduce((acc, theme) => {
    acc[theme.value] = theme;
    return acc;
}, {});

const DEFAULT_FOOD_SUBCATEGORY = "ALL";
const MAX_COUNT_KEYWORD_SEARCH = 120;
// 장소 검색 버튼/엔터 검색 후 이동할 때 사용하는 지도 레벨
const SEARCH_RESULT_MAP_LEVEL = 4;

// 장소 검색은 현재 지도 화면이 아니라 서울 전체 영역을 기준으로 수행합니다.
const SEOUL_KEYWORD_SEARCH_BOUNDS = {
    south: 37.4133,
    west: 126.7341,
    north: 37.7151,
    east: 127.2693,
};

const FOOD_SUBCATEGORY_BUTTONS = [
    {
        value: "ALL",
        label: "전체",
        keywords: [],
    },
    {
        value: "KOREAN",
        label: "한식",
        keywords: ["한식", "한식 맛집", "백반", "국밥", "찌개", "한정식", "고기집"],
    },
    {
        value: "WESTERN",
        label: "양식",
        keywords: ["양식", "양식 맛집", "파스타", "스테이크", "브런치", "피자"],
    },
    {
        value: "CHINESE",
        label: "중식",
        keywords: ["중식", "중국집", "짜장면", "짬뽕", "마라탕", "딤섬"],
    },
    {
        value: "JAPANESE",
        label: "일식",
        keywords: ["일식", "초밥", "라멘", "돈카츠", "이자카야", "우동"],
    },
    {
        value: "CAFE_DESSERT",
        label: "디저트",
        keywords: ["디저트", "베이커리", "케이크", "도넛", "아이스크림"],
    },
];

const FOOD_SUBCATEGORY_BY_VALUE = FOOD_SUBCATEGORY_BUTTONS.reduce((acc, subcategory) => {
    acc[subcategory.value] = subcategory;
    return acc;
}, {});

const wait = (milliseconds) => {
    return new Promise((resolve) => {
        window.setTimeout(resolve, milliseconds);
    });
};

const escapeHtml = (value) => {
    if (value === null || value === undefined) {
        return "";
    }

    return String(value)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
};

const getThemeLabel = (themeValue) => {
    return THEME_CONFIG_BY_VALUE[themeValue]?.label || themeValue || "";
};


const getFoodSubcategoryLabel = (subcategoryValue) => {
    return FOOD_SUBCATEGORY_BY_VALUE[subcategoryValue]?.label || "전체";
};

const normalizeSearchText = (value) => {
    return String(value || "")
        .trim()
        .replace(/\s+/g, "")
        .toLowerCase();
};

const isExactPlaceNameMatch = (place, keyword) => {
    return normalizeSearchText(place?.name) === normalizeSearchText(keyword);
};

const inferThemeValueFromSearchText = (text, fallbackThemeValue = "PALACE_CULTURE") => {
    const keyword = normalizeSearchText(text);

    if (!keyword) {
        return fallbackThemeValue;
    }

    if (
        keyword.includes("카페") ||
        keyword.includes("디저트") ||
        keyword.includes("베이커리") ||
        keyword.includes("보드게임카페") ||
        keyword.includes("보드카페")
    ) {
        return "CAFE_TOUR";
    }

    if (
        keyword.includes("맛집") ||
        keyword.includes("음식") ||
        keyword.includes("식당") ||
        keyword.includes("한식") ||
        keyword.includes("양식") ||
        keyword.includes("중식") ||
        keyword.includes("일식") ||
        keyword.includes("분식") ||
        keyword.includes("고기") ||
        keyword.includes("국밥") ||
        keyword.includes("파스타") ||
        keyword.includes("초밥")
    ) {
        return "FOOD_TOUR";
    }

    if (
        keyword.includes("호텔") ||
        keyword.includes("숙소") ||
        keyword.includes("게스트하우스") ||
        keyword.includes("모텔")
    ) {
        return "HOTEL_STAY";
    }

    if (
        keyword.includes("데이트") ||
        keyword.includes("루프탑") ||
        keyword.includes("전망")
    ) {
        return "DATE";
    }

    if (
        keyword.includes("야경") ||
        keyword.includes("서울타워") ||
        keyword.includes("낙산")
    ) {
        return "NIGHT_VIEW";
    }

    if (
        keyword.includes("한강") ||
        keyword.includes("공원") ||
        keyword.includes("산책") ||
        keyword.includes("숲")
    ) {
        return "NATURE_HANGANG";
    }

    if (
        keyword.includes("쇼핑") ||
        keyword.includes("백화점") ||
        keyword.includes("몰") ||
        keyword.includes("시장") ||
        keyword.includes("핫플") ||
        keyword.includes("팝업")
    ) {
        return "SHOPPING_HOTPLACE";
    }

    if (
        keyword.includes("궁") ||
        keyword.includes("궁궐") ||
        keyword.includes("문화") ||
        keyword.includes("박물관") ||
        keyword.includes("미술관") ||
        keyword.includes("전시") ||
        keyword.includes("전시관") ||
        keyword.includes("공연")
    ) {
        return "PALACE_CULTURE";
    }

    return fallbackThemeValue;
};

const inferThemeValueFromSearchPlace = (place, keyword, fallbackThemeValue = "PALACE_CULTURE") => {
    const baseCategory = normalizeBaseCategory(place?.category);
    const text = `${place?.name || ""} ${place?.apiCategory || ""} ${keyword || ""}`;

    if (baseCategory === "CAFE") {
        return "CAFE_TOUR";
    }

    if (baseCategory === "RESTAURANT") {
        return "FOOD_TOUR";
    }

    if (baseCategory === "HOTEL") {
        return "HOTEL_STAY";
    }

    return inferThemeValueFromSearchText(text, fallbackThemeValue);
};

const getBaseCategoryLabel = (category) => {
    if (category === "TOUR") return "관광지";
    if (category === "RESTAURANT") return "음식점";
    if (category === "CAFE") return "카페";
    if (category === "HOTEL") return "숙소";

    return category || "";
};

const getPlaceDisplayLabel = (place) => {
    if (place.themeCategory && place.themeCategory !== "ALL") {
        return getThemeLabel(place.themeCategory);
    }

    return getBaseCategoryLabel(place.category);
};

const getFocusThemeValueByPlace = (place) => {
    if (place?.themeCategory && place.themeCategory !== "ALL") {
        return place.themeCategory;
    }

    const baseCategory = normalizeBaseCategory(place?.category);

    if (baseCategory === "RESTAURANT") {
        return "FOOD_TOUR";
    }

    if (baseCategory === "CAFE") {
        return "CAFE_TOUR";
    }

    if (baseCategory === "HOTEL") {
        return "HOTEL_STAY";
    }

    return "PALACE_CULTURE";
};

const getIndoorOutdoorLabel = (place) => {
    if (place.indoorYn === "Y") {
        return "실내";
    }

    if (place.indoorYn === "N") {
        return "야외";
    }

    const themeCategory = place.themeCategory || "";
    const baseCategory = place.category || "";
    const text = `${place.name || ""} ${place.apiCategory || ""} ${place.address || ""}`;

    if (["HOTEL", "CAFE", "RESTAURANT"].includes(baseCategory)) {
        return "실내";
    }

    if (["HOTEL_STAY", "CAFE_TOUR", "FOOD_TOUR", "SHOPPING_HOTPLACE"].includes(themeCategory)) {
        return "실내";
    }

    if (["NATURE_HANGANG", "NIGHT_VIEW"].includes(themeCategory)) {
        return "야외";
    }

    if (text.includes("공원") || text.includes("한강") || text.includes("산") || text.includes("숲") || text.includes("광장")) {
        return "야외";
    }

    if (text.includes("박물관") || text.includes("미술관") || text.includes("전시") || text.includes("백화점") || text.includes("몰") || text.includes("카페") || text.includes("호텔")) {
        return "실내";
    }

    return "실내/야외";
};

const normalizeBaseCategory = (category) => {
    if (["TOUR", "RESTAURANT", "CAFE", "HOTEL"].includes(category)) {
        return category;
    }

    return "TOUR";
};

const inferBaseCategoryFromKakaoPlace = (item, fallbackBaseCategory) => {
    if (fallbackBaseCategory) {
        return fallbackBaseCategory;
    }

    const categoryText = item.category_name || "";

    if (categoryText.includes("카페")) {
        return "CAFE";
    }

    if (categoryText.includes("음식점")) {
        return "RESTAURANT";
    }

    if (
        categoryText.includes("숙박") ||
        categoryText.includes("호텔") ||
        categoryText.includes("모텔") ||
        categoryText.includes("펜션")
    ) {
        return "HOTEL";
    }

    return "TOUR";
};

const getMarkerImageUrl = (place) => {
    if (place.themeCategory && MARKER_IMAGE_BY_THEME[place.themeCategory]) {
        return MARKER_IMAGE_BY_THEME[place.themeCategory];
    }

    const baseCategory = normalizeBaseCategory(place.category);

    return FALLBACK_MARKER_IMAGE_BY_CATEGORY[baseCategory] || MARKER_IMAGE_BY_THEME.PALACE_CULTURE;
};

const createPlaceMarkerImage = (place) => {
    const kakao = window.kakao;
    const markerImageUrl = getMarkerImageUrl(place);

    const imageSize = new kakao.maps.Size(46, 46);

    const imageOption = {
        offset: new kakao.maps.Point(23, 46),
    };

    return new kakao.maps.MarkerImage(markerImageUrl, imageSize, imageOption);
};

const getRegionCenterInfo = (regionValue) => {
    return SEOUL_REGION_CENTERS[regionValue] || SEOUL_REGION_CENTERS[DEFAULT_REGION];
};

const getMapLevelByRegion = (regionValue) => {
    if (regionValue === DEFAULT_REGION) {
        return DEFAULT_MAP_LEVEL_SEOUL;
    }

    return DEFAULT_MAP_LEVEL_GU;
};

const extractRegion = (address, fallbackRegion) => {
    if (!address) {
        return fallbackRegion;
    }

    const parts = address.split(" ");
    const gu = parts.find((part) => part.endsWith("구"));

    return gu || fallbackRegion;
};

const isSeoulPlace = (item) => {
    const addressText = `${item.address_name || ""} ${item.road_address_name || ""}`;

    return addressText.includes("서울") || addressText.includes("서울특별시");
};

const getPlaceUniqueKey = (place) => {
    if (place.apiProvider && place.apiPlaceId) {
        return `${place.apiProvider}-${place.apiPlaceId}`;
    }

    if (place.placeId) {
        return `DB-${place.placeId}`;
    }

    return `${place.name}-${place.latitude}-${place.longitude}`;
};

const mergeUniquePlaces = (placeList) => {
    const placeMap = new Map();

    placeList.forEach((place) => {
        if (!place) {
            return;
        }

        const key = getPlaceUniqueKey(place);
        const existingPlace = placeMap.get(key);

        if (!existingPlace) {
            placeMap.set(key, place);
            return;
        }

        // DB에 이미 등록된 장소가 있으면 카카오 실시간 검색 결과보다 DB 장소를 우선함
        if (existingPlace.dataSource !== "DB" && place.dataSource === "DB") {
            placeMap.set(key, place);
        }
    });

    return Array.from(placeMap.values());
};

const createCourseItemId = () => {
    return `course-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
};

const getCourseItemKey = (place) => {
    return place.courseItemId || getPlaceUniqueKey(place);
};

const normalizeDayNo = (value) => {
    const numberValue = Number(value);

    if (!Number.isFinite(numberValue) || numberValue < 1) {
        return 1;
    }

    return Math.floor(numberValue);
};

const makeRouteCalculationKey = (placeList) => {
    if (!placeList || placeList.length < 2) {
        return "";
    }

    return placeList
        .map((place, index) => {
            const latitude = Number(place.latitude);
            const longitude = Number(place.longitude);
            const safeLatitude = Number.isFinite(latitude) ? latitude.toFixed(6) : "";
            const safeLongitude = Number.isFinite(longitude) ? longitude.toFixed(6) : "";

            return [
                index + 1,
                getCourseItemKey(place),
                normalizeDayNo(place.dayNo),
                safeLatitude,
                safeLongitude,
            ].join(":");
        })
        .join("|");
};

const normalizeStayMinutes = (value) => {
    if (value === null || value === undefined || value === "") {
        return null;
    }

    const numberValue = Number(value);

    if (!Number.isFinite(numberValue) || numberValue < 0) {
        return null;
    }

    return Math.floor(numberValue);
};

const resetRouteInfo = (placeList) => {
    return placeList.map((place) => ({
        ...place,
        moveDistanceM: null,
        moveDurationMin: null,
        routeStatusMessage: null,
        routeToPlaceName: null,
        routePoints: [],
    }));
};

const enrichPlaceForCourse = (place) => {
    return {
        ...place,
        courseItemId: createCourseItemId(),
        dayNo: 1,
        visitTime: "",
        stayMinutes: 60,
        moveDistanceM: null,
        moveDurationMin: null,
        routeStatusMessage: null,
        routeToPlaceName: null,
        routePoints: [],
    };
};

const formatDistanceMeter = (distanceMeter) => {
    if (distanceMeter === null || distanceMeter === undefined) {
        return "-";
    }

    if (distanceMeter >= 1000) {
        return `${(distanceMeter / 1000).toFixed(1)}km`;
    }

    return `${distanceMeter}m`;
};

const formatDurationMinute = (durationMinute) => {
    if (durationMinute === null || durationMinute === undefined) {
        return "-";
    }

    if (durationMinute >= 60) {
        const hour = Math.floor(durationMinute / 60);
        const minute = durationMinute % 60;

        if (minute === 0) {
            return `${hour}시간`;
        }

        return `${hour}시간 ${minute}분`;
    }

    return `${durationMinute}분`;
};

const normalizeRoutePoints = (routePoints) => {
    if (!Array.isArray(routePoints)) {
        return [];
    }

    return routePoints
        .map((point) => ({
            latitude: Number(point.latitude),
            longitude: Number(point.longitude),
        }))
        .filter(
            (point) =>
                Number.isFinite(point.latitude) &&
                Number.isFinite(point.longitude)
        );
};

const convertKakaoPlace = ({ item, themeValue, regionValue, fallbackBaseCategory }) => {
    const baseCategory = inferBaseCategoryFromKakaoPlace(item, fallbackBaseCategory);

    return {
        uid: `KAKAO-${themeValue}-${item.id}`,
        placeId: null,
        apiProvider: "KAKAO",
        apiPlaceId: item.id,
        contentId: null,
        name: item.place_name,
        category: baseCategory,
        themeCategory: themeValue,
        apiCategory: item.category_name || "",
        region: extractRegion(item.address_name || item.road_address_name, regionValue),
        address: item.road_address_name || item.address_name || "",
        roadAddress: item.road_address_name || "",
        latitude: Number(item.y),
        longitude: Number(item.x),
        phone: item.phone || "",
        placeUrl: item.place_url || "",
        rating: 0,
        reviewCount: 0,
        description: "",
        imageUrl: "",
        indoorYn: null,
        dataSource: "KAKAO",
    };
};

const convertDbPlace = (item, fallbackThemeValue) => {
    const baseCategory = normalizeBaseCategory(item.category);
    const themeCategory = item.themeCategory || fallbackThemeValue;

    return {
        uid: `DB-${item.placeId}`,
        placeId: item.placeId,
        apiProvider: item.apiProvider || null,
        apiPlaceId: item.apiPlaceId || null,
        contentId: item.contentId || null,
        name: item.name,
        category: baseCategory,
        themeCategory,
        apiCategory: item.apiCategory || "",
        region: item.region || DEFAULT_REGION,
        address: item.address || "",
        roadAddress: item.roadAddress || "",
        latitude: Number(item.latitude),
        longitude: Number(item.longitude),
        phone: item.phone || "",
        placeUrl: item.placeUrl || "",
        rating: item.rating || 0,
        reviewCount: item.reviewCount || 0,
        description: item.description || "",
        imageUrl: item.imageUrl || "",
        indoorYn: item.indoorYn || null,
        dataSource: "DB",
    };
};

const moveMapToRegion = async (map, regionValue) => {
    const kakao = window.kakao;
    const centerInfo = getRegionCenterInfo(regionValue);
    const center = new kakao.maps.LatLng(centerInfo.latitude, centerInfo.longitude);

    map.setLevel(getMapLevelByRegion(regionValue));
    map.setCenter(center);

    await wait(MOVE_MAP_WAIT_MS);
};

const moveMapToPlace = async (map, place) => {
    const kakao = window.kakao;
    const latitude = Number(place.latitude);
    const longitude = Number(place.longitude);

    if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
        throw new Error("장소 좌표가 없어 지도로 이동할 수 없습니다.");
    }

    const center = new kakao.maps.LatLng(latitude, longitude);

    map.setLevel(PLACE_FOCUS_MAP_LEVEL_30KM);
    map.setCenter(center);

    await wait(MOVE_MAP_WAIT_MS);
};

const moveMapToSearchResultPlace = async (map, place) => {
    const kakao = window.kakao;
    const latitude = Number(place.latitude);
    const longitude = Number(place.longitude);

    if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
        throw new Error("장소 좌표가 없어 지도로 이동할 수 없습니다.");
    }

    const center = new kakao.maps.LatLng(latitude, longitude);

    map.setLevel(SEARCH_RESULT_MAP_LEVEL);
    map.setCenter(center);

    await wait(MOVE_MAP_WAIT_MS);
};

const makeMapSearchKey = (map, regionValue, themeValue, foodSubcategoryValue = DEFAULT_FOOD_SUBCATEGORY) => {
    const center = map.getCenter();
    const level = map.getLevel();
    const latitude = center.getLat().toFixed(4);
    const longitude = center.getLng().toFixed(4);

    return `${regionValue}-${themeValue}-${foodSubcategoryValue}-${latitude}-${longitude}-${level}`;
};

const createCoordinateBoundsGrid = ({ south, west, north, east }, gridSize) => {
    const kakao = window.kakao;
    const latStep = (north - south) / gridSize;
    const lngStep = (east - west) / gridSize;
    const boundsList = [];

    for (let row = 0; row < gridSize; row += 1) {
        for (let col = 0; col < gridSize; col += 1) {
            const cellSouth = south + latStep * row;
            const cellNorth = south + latStep * (row + 1);
            const cellWest = west + lngStep * col;
            const cellEast = west + lngStep * (col + 1);

            const cellSouthWest = new kakao.maps.LatLng(cellSouth, cellWest);
            const cellNorthEast = new kakao.maps.LatLng(cellNorth, cellEast);

            boundsList.push(new kakao.maps.LatLngBounds(cellSouthWest, cellNorthEast));
        }
    }

    return boundsList;
};

const createBoundsGrid = (map, gridSize) => {
    const mapBounds = map.getBounds();
    const southWest = mapBounds.getSouthWest();
    const northEast = mapBounds.getNorthEast();

    return createCoordinateBoundsGrid(
        {
            south: southWest.getLat(),
            west: southWest.getLng(),
            north: northEast.getLat(),
            east: northEast.getLng(),
        },
        gridSize
    );
};

const createSeoulKeywordSearchBoundsGrid = (gridSize) => {
    return createCoordinateBoundsGrid(SEOUL_KEYWORD_SEARCH_BOUNDS, gridSize);
};

const getGridSearchOption = (maxCount) => {
    if (maxCount >= 100) {
        return {
            gridSize: 3,
            pageLimitPerCell: 2,
        };
    }

    return {
        gridSize: 2,
        pageLimitPerCell: 1,
    };
};

const isPlaceInsideMapBounds = (map, place) => {
    if (!place.latitude || !place.longitude) {
        return false;
    }

    const kakao = window.kakao;
    const bounds = map.getBounds();
    const position = new kakao.maps.LatLng(place.latitude, place.longitude);

    return bounds.contain(position);
};

const searchKakaoCategoryByBoundsCell = ({
                                             bounds,
                                             groupCode,
                                             themeValue,
                                             regionValue,
                                             fallbackBaseCategory,
                                             pageLimit,
                                         }) => {
    const kakao = window.kakao;

    return new Promise((resolve, reject) => {
        const placesService = new kakao.maps.services.Places();
        const collectedPlaces = [];

        const callback = (data, status, pagination) => {
            if (status === kakao.maps.services.Status.OK) {
                const filteredData = data.filter((item) => isSeoulPlace(item));

                const converted = filteredData.map((item) =>
                    convertKakaoPlace({
                        item,
                        themeValue,
                        regionValue,
                        fallbackBaseCategory,
                    })
                );

                collectedPlaces.push(...converted);

                if (pagination.hasNextPage && pagination.current < pageLimit) {
                    pagination.nextPage();
                    return;
                }

                resolve(mergeUniquePlaces(collectedPlaces));
                return;
            }

            if (status === kakao.maps.services.Status.ZERO_RESULT) {
                resolve([]);
                return;
            }

            reject(new Error(`카카오 카테고리 검색 실패: ${status}`));
        };

        placesService.categorySearch(groupCode, callback, {
            bounds,
            size: KAKAO_PAGE_SIZE,
            sort: kakao.maps.services.SortBy.ACCURACY,
        });
    });
};

const searchKakaoKeywordByBoundsCell = ({
                                            bounds,
                                            keyword,
                                            themeValue,
                                            regionValue,
                                            fallbackBaseCategory,
                                            pageLimit,
                                        }) => {
    const kakao = window.kakao;

    return new Promise((resolve, reject) => {
        const placesService = new kakao.maps.services.Places();
        const collectedPlaces = [];

        const callback = (data, status, pagination) => {
            if (status === kakao.maps.services.Status.OK) {
                const filteredData = data.filter((item) => isSeoulPlace(item));

                const converted = filteredData.map((item) =>
                    convertKakaoPlace({
                        item,
                        themeValue,
                        regionValue,
                        fallbackBaseCategory,
                    })
                );

                collectedPlaces.push(...converted);

                if (pagination.hasNextPage && pagination.current < pageLimit) {
                    pagination.nextPage();
                    return;
                }

                resolve(mergeUniquePlaces(collectedPlaces));
                return;
            }

            if (status === kakao.maps.services.Status.ZERO_RESULT) {
                resolve([]);
                return;
            }

            reject(new Error(`카카오 키워드 검색 실패: ${status}`));
        };

        placesService.keywordSearch(keyword, callback, {
            bounds,
            size: KAKAO_PAGE_SIZE,
            sort: kakao.maps.services.SortBy.ACCURACY,
        });
    });
};

const searchKakaoCategoryByMapGrid = async ({
                                                map,
                                                groupCode,
                                                themeValue,
                                                regionValue,
                                                fallbackBaseCategory,
                                                maxCount,
                                            }) => {
    const { gridSize, pageLimitPerCell } = getGridSearchOption(maxCount);
    const boundsList = createBoundsGrid(map, gridSize);
    let mergedPlaces = [];

    for (const bounds of boundsList) {
        if (mergedPlaces.length >= maxCount) {
            break;
        }

        const cellPlaces = await searchKakaoCategoryByBoundsCell({
            bounds,
            groupCode,
            themeValue,
            regionValue,
            fallbackBaseCategory,
            pageLimit: pageLimitPerCell,
        });

        mergedPlaces = mergeUniquePlaces([...mergedPlaces, ...cellPlaces]).slice(
            0,
            maxCount
        );

        await wait(70);
    }

    return mergedPlaces;
};

const searchKakaoKeywordByMapGrid = async ({
                                               map,
                                               keyword,
                                               themeValue,
                                               regionValue,
                                               fallbackBaseCategory,
                                               maxCount,
                                           }) => {
    const { gridSize, pageLimitPerCell } = getGridSearchOption(maxCount);
    const boundsList = createBoundsGrid(map, gridSize);
    let mergedPlaces = [];

    for (const bounds of boundsList) {
        if (mergedPlaces.length >= maxCount) {
            break;
        }

        const cellPlaces = await searchKakaoKeywordByBoundsCell({
            bounds,
            keyword,
            themeValue,
            regionValue,
            fallbackBaseCategory,
            pageLimit: pageLimitPerCell,
        });

        mergedPlaces = mergeUniquePlaces([...mergedPlaces, ...cellPlaces]).slice(
            0,
            maxCount
        );

        await wait(70);
    }

    return mergedPlaces;
};

const searchKakaoKeywordBySeoulGrid = async ({
                                                 keyword,
                                                 themeValue,
                                                 fallbackBaseCategory,
                                                 maxCount,
                                             }) => {
    const { gridSize, pageLimitPerCell } = getGridSearchOption(maxCount);
    const boundsList = createSeoulKeywordSearchBoundsGrid(gridSize);
    let mergedPlaces = [];

    for (const bounds of boundsList) {
        if (mergedPlaces.length >= maxCount) {
            break;
        }

        const cellPlaces = await searchKakaoKeywordByBoundsCell({
            bounds,
            keyword,
            themeValue,
            regionValue: DEFAULT_REGION,
            fallbackBaseCategory,
            pageLimit: pageLimitPerCell,
        });

        mergedPlaces = mergeUniquePlaces([...mergedPlaces, ...cellPlaces]).slice(
            0,
            maxCount
        );

        await wait(70);
    }

    return mergedPlaces;
};

const filterPlacesByFoodSubcategory = (placeList, foodSubcategoryValue) => {
    if (!foodSubcategoryValue || foodSubcategoryValue === DEFAULT_FOOD_SUBCATEGORY) {
        return placeList;
    }

    const subcategoryConfig = FOOD_SUBCATEGORY_BY_VALUE[foodSubcategoryValue];

    if (!subcategoryConfig) {
        return placeList;
    }

    const keywords = subcategoryConfig.keywords.map((keyword) => normalizeSearchText(keyword));

    return placeList.filter((place) => {
        const text = normalizeSearchText(
            `${place.name || ""} ${place.apiCategory || ""} ${place.address || ""} ${place.description || ""}`
        );

        return keywords.some((keyword) => keyword && text.includes(keyword));
    });
};

const searchKakaoPlacesForTheme = async ({
                                             map,
                                             themeValue,
                                             regionValue,
                                             maxCount,
                                             foodSubcategoryValue = DEFAULT_FOOD_SUBCATEGORY,
                                         }) => {
    const themeConfig = THEME_CONFIG_BY_VALUE[themeValue];

    if (!themeConfig) {
        return [];
    }

    const foodSubcategoryConfig = FOOD_SUBCATEGORY_BY_VALUE[foodSubcategoryValue];
    const isFoodSubcategorySearch =
        themeValue === "FOOD_TOUR" &&
        foodSubcategoryConfig &&
        foodSubcategoryConfig.value !== DEFAULT_FOOD_SUBCATEGORY;

    const kakaoCategories = isFoodSubcategorySearch ? [] : themeConfig.kakaoCategories || [];
    const kakaoKeywords = isFoodSubcategorySearch
        ? foodSubcategoryConfig.keywords
        : themeConfig.kakaoKeywords || [];
    const searchCount = Math.max(1, kakaoCategories.length + kakaoKeywords.length);

    const maxCountPerSearch =
        themeValue === "FOOD_TOUR" && !isFoodSubcategorySearch
            ? maxCount
            : Math.min(maxCount, Math.max(15, Math.ceil(maxCount / searchCount) + 10));

    let mergedPlaces = [];

    for (const categorySearch of kakaoCategories) {
        if (mergedPlaces.length >= maxCount) {
            break;
        }

        const categoryPlaces = await searchKakaoCategoryByMapGrid({
            map,
            groupCode: categorySearch.groupCode,
            themeValue,
            regionValue,
            fallbackBaseCategory: categorySearch.baseCategory,
            maxCount: maxCountPerSearch,
        });

        mergedPlaces = mergeUniquePlaces([...mergedPlaces, ...categoryPlaces]).slice(
            0,
            maxCount
        );
    }

    for (const keyword of kakaoKeywords) {
        if (mergedPlaces.length >= maxCount) {
            break;
        }

        const keywordPlaces = await searchKakaoKeywordByMapGrid({
            map,
            keyword,
            themeValue,
            regionValue,
            fallbackBaseCategory: themeConfig.markerCategory || "TOUR",
            maxCount: maxCountPerSearch,
        });

        mergedPlaces = mergeUniquePlaces([...mergedPlaces, ...keywordPlaces]).slice(
            0,
            maxCount
        );
    }

    return mergedPlaces;
};

const fetchDbPlacesForTheme = async ({
                                         map,
                                         themeValue,
                                         regionValue,
                                         maxCount,
                                         foodSubcategoryValue = DEFAULT_FOOD_SUBCATEGORY,
                                     }) => {
    const dbFetchLimit = Math.min(300, Math.max(maxCount * 3, 120));
    const dbPlaces = await fetchCourseBuilderDbPlaces({
        theme: themeValue,
        region: regionValue,
        limit: dbFetchLimit,
    });

    const convertedPlaces = dbPlaces
        .map((place) => convertDbPlace(place, themeValue))
        .filter((place) => isPlaceInsideMapBounds(map, place));

    if (themeValue !== "FOOD_TOUR") {
        return convertedPlaces;
    }

    return filterPlacesByFoodSubcategory(convertedPlaces, foodSubcategoryValue);
};

const searchPlacesForTheme = async ({
                                        map,
                                        themeValue,
                                        regionValue,
                                        maxCount,
                                        foodSubcategoryValue = DEFAULT_FOOD_SUBCATEGORY,
                                    }) => {
    const dbPlaces = await fetchDbPlacesForTheme({
        map,
        themeValue,
        regionValue,
        maxCount,
        foodSubcategoryValue,
    });

    const kakaoPlaces = await searchKakaoPlacesForTheme({
        map,
        themeValue,
        regionValue,
        maxCount,
        foodSubcategoryValue,
    });

    // DB 장소를 앞쪽에 둬서 직접 등록한 장소가 우선 보이게 함
    return mergeUniquePlaces([...dbPlaces, ...kakaoPlaces]).slice(0, maxCount);
};

const fetchDbPlacesByKeyword = async ({ keyword, maxCount }) => {
    const searchKeyword = keyword.trim();
    const normalizedKeyword = normalizeSearchText(searchKeyword);

    if (!normalizedKeyword) {
        return [];
    }

    const dbPlaces = await fetchCourseBuilderDbPlaces({
        theme: "ALL",
        region: DEFAULT_REGION,
        limit: Math.min(300, Math.max(maxCount * 3, 120)),
    });

    return dbPlaces
        .map((place) => convertDbPlace(place, inferThemeValueFromSearchText(searchKeyword, "PALACE_CULTURE")))
        .filter((place) => {
            const text = normalizeSearchText(
                `${place.name || ""} ${place.apiCategory || ""} ${place.region || ""} ${place.address || ""} ${place.description || ""}`
            );

            return text.includes(normalizedKeyword);
        })
        .map((place) => ({
            ...place,
            themeCategory: inferThemeValueFromSearchPlace(place, searchKeyword, place.themeCategory),
        }))
        .slice(0, maxCount);
};

const searchPlacesByKeyword = async ({ keyword, maxCount }) => {
    const searchKeyword = keyword.trim();
    const inferredThemeValue = inferThemeValueFromSearchText(searchKeyword, "PALACE_CULTURE");

    const dbPlaces = await fetchDbPlacesByKeyword({
        keyword: searchKeyword,
        maxCount,
    });

    const kakaoPlaces = await searchKakaoKeywordBySeoulGrid({
        keyword: searchKeyword,
        themeValue: inferredThemeValue,
        fallbackBaseCategory: null,
        maxCount,
    });

    return mergeUniquePlaces(
        [...dbPlaces, ...kakaoPlaces].map((place) => ({
            ...place,
            themeCategory: inferThemeValueFromSearchPlace(place, searchKeyword, place.themeCategory),
        }))
    ).slice(0, maxCount);
};

function CourseBuilderPage() {
    const mapContainerRef = useRef(null);
    const mapRef = useRef(null);
    const markerRefs = useRef([]);
    const routePolylineRefs = useRef([]);
    const infoWindowRefs = useRef([]);
    const markerInfoWindowMapRef = useRef(new Map());
    const pendingInfoWindowRequestRef = useRef(null);

    const autoSearchTimerRef = useRef(null);
    const activeThemeRef = useRef("ALL");
    const activeFoodSubcategoryRef = useRef(DEFAULT_FOOD_SUBCATEGORY);
    const regionRef = useRef(DEFAULT_REGION);
    const suppressNextIdleSearchRef = useRef(false);
    // 키워드 검색 결과는 사용자가 카테고리를 다시 선택하기 전까지
    // 지도 이동/확대/축소로 인한 자동 재검색에 덮어씌워지지 않게 유지합니다.
    const keepKeywordSearchResultRef = useRef(false);
    const lastSearchKeyRef = useRef("");
    const searchRequestIdRef = useRef(0);
    const autoRouteTimerRef = useRef(null);
    const lastRouteCalculationKeyRef = useRef("");
    const routeCalculationRequestIdRef = useRef(0);

    const [mapReady, setMapReady] = useState(false);
    const [mapStatus, setMapStatus] = useState("카카오 지도를 불러오는 중입니다.");

    const [region, setRegion] = useState(DEFAULT_REGION);
    const [places, setPlaces] = useState([]);
    const [markerSelectedPlaces, setMarkerSelectedPlaces] = useState([]);
    const [selectedPlaces, setSelectedPlaces] = useState([]);

    const [activeTheme, setActiveTheme] = useState("ALL");
    const [activeFoodSubcategory, setActiveFoodSubcategory] = useState(DEFAULT_FOOD_SUBCATEGORY);
    const [placeSearchKeyword, setPlaceSearchKeyword] = useState("");
    const [isLoadingPlaces, setIsLoadingPlaces] = useState(false);
    const [isCalculatingRoutes, setIsCalculatingRoutes] = useState(false);

    const [courseTitle, setCourseTitle] = useState("");
    const [courseDescription, setCourseDescription] = useState("");

    useEffect(() => {
        regionRef.current = region;
    }, [region]);

    useEffect(() => {
        activeThemeRef.current = activeTheme;
    }, [activeTheme]);

    useEffect(() => {
        activeFoodSubcategoryRef.current = activeFoodSubcategory;
    }, [activeFoodSubcategory]);


    const searchPlacesByCurrentMap = useCallback(async ({
                                                            regionValue,
                                                            themeValue,
                                                            foodSubcategoryValue = activeFoodSubcategoryRef.current,
                                                            moveMap = false,
                                                            triggeredByAuto = false,
                                                            requiredPlace = null,
                                                        }) => {
        const map = mapRef.current;

        if (!map) {
            return;
        }

        const requestId = searchRequestIdRef.current + 1;
        searchRequestIdRef.current = requestId;

        try {
            setIsLoadingPlaces(true);

            if (moveMap) {
                suppressNextIdleSearchRef.current = true;
                setMapStatus(`${regionValue} 지도로 이동하는 중입니다.`);
                await moveMapToRegion(map, regionValue);
            }

            const searchKey = makeMapSearchKey(map, regionValue, themeValue, foodSubcategoryValue);

            if (triggeredByAuto && lastSearchKeyRef.current === searchKey) {
                return;
            }

            lastSearchKeyRef.current = searchKey;

            const searchTargets = themeValue === "ALL" ? SEARCH_THEMES : [THEME_CONFIG_BY_VALUE[themeValue]];
            const maxCount = themeValue === "ALL" ? MAX_COUNT_PER_THEME_ALL : MAX_COUNT_SINGLE_THEME;
            const countText =
                themeValue === "ALL"
                    ? `테마별 최대 ${MAX_COUNT_PER_THEME_ALL}개`
                    : `최대 ${MAX_COUNT_SINGLE_THEME}개`;
            const foodSubcategoryText =
                themeValue === "FOOD_TOUR" && foodSubcategoryValue !== DEFAULT_FOOD_SUBCATEGORY
                    ? ` / ${getFoodSubcategoryLabel(foodSubcategoryValue)}`
                    : "";

            setMapStatus(
                `${regionValue} 현재 지도 화면에서 ${
                    themeValue === "ALL" ? "전체" : `${getThemeLabel(themeValue)}${foodSubcategoryText}`
                } 장소를 불러오는 중입니다. (${countText})`
            );

            const results = [];

            for (const themeConfig of searchTargets) {
                if (!themeConfig) {
                    continue;
                }

                const themePlaces = await searchPlacesForTheme({
                    map,
                    themeValue: themeConfig.value,
                    regionValue,
                    maxCount,
                    foodSubcategoryValue,
                });

                results.push(...themePlaces);
            }

            if (requestId !== searchRequestIdRef.current) {
                return;
            }

            // 이름 클릭으로 이동한 장소는 새 검색 결과에 없어도
            // 지도 정보창을 반드시 열 수 있도록 마커 목록에 강제로 포함합니다.
            const mergedPlaces = mergeUniquePlaces(
                requiredPlace ? [requiredPlace, ...results] : results
            );

            setPlaces(mergedPlaces);

            if (mergedPlaces.length === 0) {
                setMapStatus(
                    `${regionValue} 현재 지도 화면에서 검색 결과가 없습니다. 지도를 조금 이동하거나 확대/축소해보세요.`
                );
            } else {
                setMapStatus(
                    `${regionValue} 현재 지도 화면 ${
                        themeValue === "ALL" ? "전체" : `${getThemeLabel(themeValue)}${foodSubcategoryText}`
                    } 장소 ${mergedPlaces.length}개 표시 중 (${countText}, DB + 카카오 API)`
                );
            }
        } catch (error) {
            console.error(error);
            setMapStatus("장소 데이터를 불러오지 못했습니다.");
            alert(`장소 데이터를 불러오지 못했습니다.\n\n${error.message}`);
        } finally {
            if (requestId === searchRequestIdRef.current) {
                setIsLoadingPlaces(false);
            }
        }
    }, []);

    const addMarkerSelectedPlace = useCallback((place) => {
        setMarkerSelectedPlaces((prev) => {
            const exists = prev.some(
                (item) => getPlaceUniqueKey(item) === getPlaceUniqueKey(place)
            );

            if (exists) {
                return prev;
            }

            return [...prev, place];
        });
    }, []);

    const openPlaceInfoWindow = useCallback((place, options = {}) => {
        const { addToMarkerSelectedList = false } = options;
        const placeKey = getPlaceUniqueKey(place);
        const markerInfoWindow = markerInfoWindowMapRef.current.get(placeKey);

        if (!markerInfoWindow || !mapRef.current) {
            return false;
        }

        infoWindowRefs.current.forEach((item) => item.close());
        markerInfoWindow.infoWindow.open(mapRef.current, markerInfoWindow.marker);

        if (addToMarkerSelectedList) {
            addMarkerSelectedPlace(place);
        }

        return true;
    }, [addMarkerSelectedPlace]);

    const handleThemeClick = useCallback((themeValue) => {
        keepKeywordSearchResultRef.current = false;

        const nextFoodSubcategory =
            themeValue === "FOOD_TOUR"
                ? activeFoodSubcategoryRef.current
                : DEFAULT_FOOD_SUBCATEGORY;

        setActiveTheme(themeValue);
        activeThemeRef.current = themeValue;
        setActiveFoodSubcategory(nextFoodSubcategory);
        activeFoodSubcategoryRef.current = nextFoodSubcategory;

        void searchPlacesByCurrentMap({
            regionValue: regionRef.current,
            themeValue,
            foodSubcategoryValue: nextFoodSubcategory,
            moveMap: false,
        });
    }, [searchPlacesByCurrentMap]);

    const handleFoodSubcategoryClick = useCallback((subcategoryValue) => {
        keepKeywordSearchResultRef.current = false;

        setActiveTheme("FOOD_TOUR");
        activeThemeRef.current = "FOOD_TOUR";
        setActiveFoodSubcategory(subcategoryValue);
        activeFoodSubcategoryRef.current = subcategoryValue;

        void searchPlacesByCurrentMap({
            regionValue: regionRef.current,
            themeValue: "FOOD_TOUR",
            foodSubcategoryValue: subcategoryValue,
            moveMap: false,
        });
    }, [searchPlacesByCurrentMap]);

    const handleRegionChange = useCallback((event) => {
        keepKeywordSearchResultRef.current = false;

        const nextRegion = event.target.value;

        setRegion(nextRegion);
        regionRef.current = nextRegion;

        void searchPlacesByCurrentMap({
            regionValue: nextRegion,
            themeValue: activeThemeRef.current,
            foodSubcategoryValue: activeFoodSubcategoryRef.current,
            moveMap: true,
        });
    }, [searchPlacesByCurrentMap]);

    const handleReloadMapPlaces = useCallback(() => {
        keepKeywordSearchResultRef.current = false;

        void searchPlacesByCurrentMap({
            regionValue: regionRef.current,
            themeValue: activeThemeRef.current,
            foodSubcategoryValue: activeFoodSubcategoryRef.current,
            moveMap: false,
        });
    }, [searchPlacesByCurrentMap]);

    const handlePlaceKeywordSearch = useCallback(async () => {
        const map = mapRef.current;
        const keyword = placeSearchKeyword.trim();

        if (!map) {
            return;
        }

        if (!keyword) {
            alert("검색어를 입력해주세요.");
            return;
        }

        const requestId = searchRequestIdRef.current + 1;
        searchRequestIdRef.current = requestId;

        try {
            setIsLoadingPlaces(true);

            // 검색을 시작하면 카테고리 선택은 항상 전체로 바꿉니다.
            // 검색 결과는 카테고리를 다시 누르기 전까지 지도 이동으로 초기화되지 않습니다.
            keepKeywordSearchResultRef.current = true;
            setActiveTheme("ALL");
            activeThemeRef.current = "ALL";
            setActiveFoodSubcategory(DEFAULT_FOOD_SUBCATEGORY);
            activeFoodSubcategoryRef.current = DEFAULT_FOOD_SUBCATEGORY;

            setMapStatus(`서울 전체에서 '${keyword}' 장소를 검색하는 중입니다.`);

            const keywordPlaces = await searchPlacesByKeyword({
                keyword,
                maxCount: MAX_COUNT_KEYWORD_SEARCH,
            });

            if (requestId !== searchRequestIdRef.current) {
                return;
            }

            setPlaces(keywordPlaces);

            const exactMatch = keywordPlaces.find((place) => isExactPlaceNameMatch(place, keyword));

            if (exactMatch) {
                pendingInfoWindowRequestRef.current = {
                    place: exactMatch,
                    addToMarkerSelectedList: false,
                };

                suppressNextIdleSearchRef.current = true;
                await moveMapToSearchResultPlace(map, exactMatch);

                setMapStatus(
                    `'${keyword}' 정확한 장소를 찾았습니다. 전체 카테고리 상태로 지도 레벨 4로 이동하고 이름 클릭과 동일하게 지도 정보창을 엽니다. 카테고리를 다시 선택하기 전까지 검색 결과를 유지합니다.`
                );
                return;
            }

            if (keywordPlaces.length === 0) {
                setMapStatus(`서울 전체에서 '${keyword}' 검색 결과가 없습니다. 다른 검색어로 다시 시도해보세요.`);
                return;
            }

            suppressNextIdleSearchRef.current = true;
            await moveMapToSearchResultPlace(map, keywordPlaces[0]);

            setMapStatus(`서울 전체에서 '${keyword}' 검색 결과 ${keywordPlaces.length}개를 전체 카테고리 상태로 지도 레벨 4에 표시했습니다. 카테고리를 다시 선택하기 전까지 지도 이동 후에도 이 결과를 유지합니다.`);
        } catch (error) {
            console.error(error);
            setMapStatus("장소 검색에 실패했습니다.");
            alert(`장소 검색에 실패했습니다.

${error.message}`);
        } finally {
            if (requestId === searchRequestIdRef.current) {
                setIsLoadingPlaces(false);
            }
        }
    }, [placeSearchKeyword]);

    const handlePlaceSearchKeyDown = useCallback((event) => {
        if (event.key === "Enter") {
            event.preventDefault();
            void handlePlaceKeywordSearch();
        }
    }, [handlePlaceKeywordSearch]);

    const handleClearPlaceSearchKeyword = useCallback(() => {
        setPlaceSearchKeyword("");
    }, []);

    const handleMoveToPlace = useCallback(async (place) => {
        const map = mapRef.current;

        if (!map) {
            return;
        }

        const nextThemeValue = getFocusThemeValueByPlace(place);

        try {
            // 이름 클릭으로 카테고리 화면에 들어가면 키워드 검색 유지 모드는 해제합니다.
            keepKeywordSearchResultRef.current = false;

            setActiveTheme(nextThemeValue);
            activeThemeRef.current = nextThemeValue;

            // 이름 클릭은 오른쪽 선택 목록에 추가하지 않고,
            // 마커를 클릭했을 때처럼 지도 정보창만 열리게 합니다.
            // 클릭한 장소가 새 카테고리 검색 결과에 없어도 아래 requiredPlace로
            // places 목록에 강제로 포함되기 때문에 정보창이 안정적으로 열립니다.
            pendingInfoWindowRequestRef.current = {
                place,
                addToMarkerSelectedList: false,
            };

            suppressNextIdleSearchRef.current = true;
            setMapStatus(
                `${place.name} 위치로 이동 중입니다. ${getThemeLabel(nextThemeValue)} 카테고리만 표시합니다. 약 30km 범위로 맞춥니다.`
            );

            await moveMapToPlace(map, place);

            await searchPlacesByCurrentMap({
                regionValue: regionRef.current,
                themeValue: nextThemeValue,
                foodSubcategoryValue: nextThemeValue === "FOOD_TOUR" ? activeFoodSubcategoryRef.current : DEFAULT_FOOD_SUBCATEGORY,
                moveMap: false,
                requiredPlace: place,
            });
        } catch (error) {
            console.error(error);
            alert(error.message);
        }
    }, [searchPlacesByCurrentMap]);

    const removeMarkerSelectedPlace = useCallback((placeKey) => {
        setMarkerSelectedPlaces((prev) =>
            prev.filter((place) => getPlaceUniqueKey(place) !== placeKey)
        );
    }, []);

    const clearMarkerSelectedPlaces = useCallback(() => {
        setMarkerSelectedPlaces([]);
    }, []);

    const addPlaceToCourse = useCallback((place) => {
        setSelectedPlaces((prev) => {
            const exists = prev.some(
                (item) => getPlaceUniqueKey(item) === getPlaceUniqueKey(place)
            );

            if (exists) {
                alert("이미 코스에 추가된 장소입니다.");
                return prev;
            }

            return resetRouteInfo([...prev, enrichPlaceForCourse(place)]);
        });
    }, []);

    const removePlaceFromCourse = useCallback((placeKey) => {
        setSelectedPlaces((prev) =>
            resetRouteInfo(prev.filter((place) => getCourseItemKey(place) !== placeKey))
        );
    }, []);

    const movePlace = useCallback((index, direction) => {
        setSelectedPlaces((prev) => {
            const nextIndex = index + direction;

            if (nextIndex < 0 || nextIndex >= prev.length) {
                return prev;
            }

            const copied = [...prev];
            [copied[index], copied[nextIndex]] = [copied[nextIndex], copied[index]];

            return resetRouteInfo(copied);
        });
    }, []);

    const updateSelectedPlaceField = useCallback((index, fieldName, value) => {
        setSelectedPlaces((prev) => {
            const updatedPlaces = prev.map((place, placeIndex) => {
                if (placeIndex !== index) {
                    return place;
                }

                return {
                    ...place,
                    [fieldName]: value,
                };
            });

            if (fieldName === "dayNo") {
                return resetRouteInfo(updatedPlaces);
            }

            return updatedPlaces;
        });
    }, []);

    const calculateRoutesForPlaces = useCallback(async (targetPlaces) => {
        if (targetPlaces.length < 2) {
            return targetPlaces;
        }

        const routeRequestBody = {
            mode: "WALKING",
            places: targetPlaces.map((place, index) => ({
                clientPlaceId: getCourseItemKey(place),
                name: place.name,
                dayNo: normalizeDayNo(place.dayNo),
                placeOrder: index + 1,
                latitude: Number(place.latitude),
                longitude: Number(place.longitude),
            })),
        };

        const result = await calculateCourseBuilderRoutes(routeRequestBody);
        const segmentMap = new Map();

        (result.segments || []).forEach((segment) => {
            segmentMap.set(segment.fromClientPlaceId, segment);
        });

        return targetPlaces.map((place) => {
            const segment = segmentMap.get(getCourseItemKey(place));

            if (!segment) {
                return {
                    ...place,
                    moveDistanceM: null,
                    moveDurationMin: null,
                    routeStatusMessage: null,
                    routeToPlaceName: null,
                    routePoints: [],
                };
            }

            return {
                ...place,
                moveDistanceM: segment.distanceMeter ?? null,
                moveDurationMin: segment.durationMinute ?? null,
                routeStatusMessage: segment.statusMessage || null,
                routeToPlaceName: segment.toPlaceName || null,
                routePoints: normalizeRoutePoints(segment.routePoints),
            };
        });
    }, []);

    useEffect(() => {
        if (autoRouteTimerRef.current) {
            window.clearTimeout(autoRouteTimerRef.current);
        }

        const routeCalculationKey = makeRouteCalculationKey(selectedPlaces);

        if (!routeCalculationKey) {
            lastRouteCalculationKeyRef.current = "";
            setIsCalculatingRoutes(false);
            return undefined;
        }

        if (lastRouteCalculationKeyRef.current === routeCalculationKey) {
            return undefined;
        }

        lastRouteCalculationKeyRef.current = routeCalculationKey;

        const requestId = routeCalculationRequestIdRef.current + 1;
        routeCalculationRequestIdRef.current = requestId;

        autoRouteTimerRef.current = window.setTimeout(async () => {
            try {
                setIsCalculatingRoutes(true);

                const updatedPlaces = await calculateRoutesForPlaces(selectedPlaces);

                if (
                    requestId === routeCalculationRequestIdRef.current &&
                    lastRouteCalculationKeyRef.current === routeCalculationKey
                ) {
                    setSelectedPlaces(updatedPlaces);
                }
            } catch (error) {
                console.error(error);

                if (requestId === routeCalculationRequestIdRef.current) {
                    setMapStatus(`이동거리 자동 계산 실패: ${error.message}`);
                }
            } finally {
                if (requestId === routeCalculationRequestIdRef.current) {
                    setIsCalculatingRoutes(false);
                }
            }
        }, AUTO_ROUTE_CALC_DELAY_MS);

        return () => {
            if (autoRouteTimerRef.current) {
                window.clearTimeout(autoRouteTimerRef.current);
            }
        };
    }, [calculateRoutesForPlaces, selectedPlaces]);

    const handleSaveCourse = useCallback(async () => {
        if (!courseTitle.trim()) {
            alert("코스 제목을 입력해주세요.");
            return;
        }

        if (!courseDescription.trim()) {
            alert("코스 설명을 입력해주세요.");
            return;
        }

        if (selectedPlaces.length === 0) {
            alert("코스에 장소를 1개 이상 추가해주세요.");
            return;
        }

        let placesForSave = selectedPlaces;

        if (selectedPlaces.length >= 2) {
            try {
                setIsCalculatingRoutes(true);
                placesForSave = await calculateRoutesForPlaces(selectedPlaces);
                setSelectedPlaces(placesForSave);
            } catch (error) {
                console.error(error);
                const shouldContinue = window.confirm(
                    `이동거리 계산에 실패했습니다.\n\n${error.message}\n\n이동거리 없이 코스를 저장할까요?`
                );

                if (!shouldContinue) {
                    setIsCalculatingRoutes(false);
                    return;
                }
            } finally {
                setIsCalculatingRoutes(false);
            }
        }

        const requestBody = {
            memberId: TEST_MEMBER_ID,
            resultId: null,
            paymentId: null,

            title: courseTitle.trim(),
            description: courseDescription.trim(),

            travelCode: null,
            courseType: "CUSTOM",
            region,
            isPublic: "N",

            places: placesForSave.map((place, index) => ({
                // DB에서 불러온 장소면 기존 PLACE_ID를 그대로 저장
                // 카카오 API 장소면 null로 보내고 백엔드가 PLACES에 새로 저장
                placeId: place.placeId || null,

                apiProvider: place.apiProvider || "KAKAO",
                apiPlaceId: place.apiPlaceId || null,
                contentId: place.contentId || null,

                // DB CATEGORY에는 기존 TOUR/RESTAURANT/CAFE/HOTEL 값을 저장
                // 테마값은 화면 필터용으로만 사용
                name: place.name,
                category: normalizeBaseCategory(place.category),
                apiCategory: place.apiCategory || null,

                region: place.region,
                address: place.address,
                roadAddress: place.roadAddress || null,

                latitude: place.latitude,
                longitude: place.longitude,

                phone: place.phone || null,
                placeUrl: place.placeUrl || null,

                rating: place.rating || 0,
                reviewCount: place.reviewCount || 0,

                description: place.description || null,
                imageUrl: place.imageUrl || null,

                sourceType: place.dataSource === "DB" ? "RECOMMEND" : "USER_SELECTED",
                recommendYn: place.dataSource === "DB" ? "Y" : "N",
                approvalStatus: place.dataSource === "DB" ? "APPROVED" : "PENDING",

                dayNo: normalizeDayNo(place.dayNo),
                placeOrder: index + 1,
                memo: null,
                visitTime: place.visitTime || null,
                stayMinutes: normalizeStayMinutes(place.stayMinutes),
                moveDistanceM: place.moveDistanceM ?? null,
                moveDurationMin: place.moveDurationMin ?? null,
            })),
        };

        try {
            const result = await saveCourseBuilderCourse(requestBody);

            alert(`코스가 저장되었습니다. COURSE_ID: ${result.courseId}`);

            setCourseTitle("");
            setCourseDescription("");
            setSelectedPlaces([]);
        } catch (error) {
            console.error(error);
            alert(`코스 저장에 실패했습니다.\n\n${error.message}`);
        }
    }, [calculateRoutesForPlaces, courseDescription, courseTitle, region, selectedPlaces]);

    useEffect(() => {
        loadKakaoMap()
            .then(() => {
                const kakao = window.kakao;
                const centerInfo = getRegionCenterInfo(DEFAULT_REGION);
                const defaultCenter = new kakao.maps.LatLng(
                    centerInfo.latitude,
                    centerInfo.longitude
                );

                const map = new kakao.maps.Map(mapContainerRef.current, {
                    center: defaultCenter,
                    level: getMapLevelByRegion(DEFAULT_REGION),
                });

                mapRef.current = map;
                setMapReady(true);
                setMapStatus("카카오 지도 로딩 성공");
            })
            .catch((error) => {
                console.error(error);
                setMapStatus("카카오 지도 로딩 실패");
                alert("카카오 지도를 불러오지 못했습니다. JavaScript Key와 도메인 설정을 확인하세요.");
            });
    }, []);

    useEffect(() => {
        if (mapReady) {
            void searchPlacesByCurrentMap({
                regionValue: DEFAULT_REGION,
                themeValue: "ALL",
                foodSubcategoryValue: DEFAULT_FOOD_SUBCATEGORY,
                moveMap: false,
            });
        }
    }, [mapReady, searchPlacesByCurrentMap]);

    useEffect(() => {
        if (!mapReady || !mapRef.current) {
            return undefined;
        }

        const kakao = window.kakao;
        const map = mapRef.current;

        const handleIdle = () => {
            if (suppressNextIdleSearchRef.current) {
                suppressNextIdleSearchRef.current = false;
                return;
            }

            if (keepKeywordSearchResultRef.current) {
                return;
            }

            if (autoSearchTimerRef.current) {
                window.clearTimeout(autoSearchTimerRef.current);
            }

            autoSearchTimerRef.current = window.setTimeout(() => {
                void searchPlacesByCurrentMap({
                    regionValue: regionRef.current,
                    themeValue: activeThemeRef.current,
                    foodSubcategoryValue: activeFoodSubcategoryRef.current,
                    moveMap: false,
                    triggeredByAuto: true,
                });
            }, AUTO_SEARCH_DELAY_MS);
        };

        kakao.maps.event.addListener(map, "idle", handleIdle);

        return () => {
            if (autoSearchTimerRef.current) {
                window.clearTimeout(autoSearchTimerRef.current);
            }

            kakao.maps.event.removeListener(map, "idle", handleIdle);
        };
    }, [mapReady, searchPlacesByCurrentMap]);

    useEffect(() => {
        if (!mapReady || !mapRef.current) {
            return undefined;
        }

        const kakao = window.kakao;
        const map = mapRef.current;

        routePolylineRefs.current.forEach((polyline) => polyline.setMap(null));
        routePolylineRefs.current = [];

        if (selectedPlaces.length < 2) {
            return undefined;
        }

        selectedPlaces.forEach((place) => {
            const routePoints = normalizeRoutePoints(place.routePoints);

            if (routePoints.length < 2) {
                return;
            }

            const path = routePoints.map(
                (point) => new kakao.maps.LatLng(point.latitude, point.longitude)
            );

            const polyline = new kakao.maps.Polyline({
                map,
                path,
                strokeWeight: 5,
                strokeColor: "#2563eb",
                strokeOpacity: 0.85,
                strokeStyle: "solid",
            });

            routePolylineRefs.current.push(polyline);
        });

        return () => {
            routePolylineRefs.current.forEach((polyline) => polyline.setMap(null));
            routePolylineRefs.current = [];
        };
    }, [mapReady, selectedPlaces]);

    useEffect(() => {
        if (!mapReady || !mapRef.current) {
            return;
        }

        const kakao = window.kakao;
        const map = mapRef.current;

        markerRefs.current.forEach((marker) => marker.setMap(null));
        markerRefs.current = [];

        infoWindowRefs.current.forEach((infoWindow) => infoWindow.close());
        infoWindowRefs.current = [];
        markerInfoWindowMapRef.current.clear();

        if (places.length === 0) {
            return;
        }

        places.forEach((place) => {
            if (!place.latitude || !place.longitude) {
                return;
            }

            const position = new kakao.maps.LatLng(place.latitude, place.longitude);

            const marker = new kakao.maps.Marker({
                map,
                position,
                image: createPlaceMarkerImage(place),
            });

            const indoorOutdoorLabel = getIndoorOutdoorLabel(place);

            const infoWindow = new kakao.maps.InfoWindow({
                content: `
                    <div style="padding:10px; font-size:13px; min-width:220px; line-height:1.5;">
                        <strong>${escapeHtml(place.name)}</strong><br/>
                        ${escapeHtml(getPlaceDisplayLabel(place))} / ${escapeHtml(place.region)}<br/>
                        ${escapeHtml(place.address)}<br/>
                        <span style="color:#2563eb;">${escapeHtml(indoorOutdoorLabel)}</span><br/>
                        ${place.rating ? `평점 ${escapeHtml(place.rating)} / ` : ""}
                        ${place.reviewCount ? `리뷰 ${escapeHtml(place.reviewCount)}개<br/>` : ""}
                        ${place.phone ? `전화번호 ${escapeHtml(place.phone)}<br/>` : ""}
                        ${place.placeUrl ? `<a href="${escapeHtml(place.placeUrl)}" target="_blank" rel="noreferrer">상세보기</a>` : ""}
                    </div>
                `,
            });

            const placeKey = getPlaceUniqueKey(place);

            markerInfoWindowMapRef.current.set(placeKey, {
                marker,
                infoWindow,
            });

            kakao.maps.event.addListener(marker, "click", () => {
                openPlaceInfoWindow(place, {
                    addToMarkerSelectedList: true,
                });
            });

            markerRefs.current.push(marker);
            infoWindowRefs.current.push(infoWindow);
        });

        if (pendingInfoWindowRequestRef.current) {
            const { place: pendingPlace, addToMarkerSelectedList } = pendingInfoWindowRequestRef.current;
            const opened = openPlaceInfoWindow(pendingPlace, {
                addToMarkerSelectedList,
            });

            if (opened) {
                pendingInfoWindowRequestRef.current = null;
            }
        }
    }, [mapReady, openPlaceInfoWindow, places]);

    const visibleMarkerSelectedPlaces = markerSelectedPlaces;

    return (
        <div className="course-builder-page">
            <header className="course-builder-header">
                <h1>지도 기반 직접 코스 만들기</h1>
                <p>{mapStatus}</p>
            </header>

            <section className="course-builder-filter">
                <select value={region} onChange={handleRegionChange}>
                    {SEOUL_REGIONS.map((regionName) => (
                        <option key={regionName} value={regionName}>
                            {regionName === "서울" ? "서울 전체" : regionName}
                        </option>
                    ))}
                </select>

                <div style={{ position: "relative", flex: "1 1 320px", minWidth: "260px" }}>
                    <input
                        className="course-builder-search-input"
                        value={placeSearchKeyword}
                        onChange={(event) => setPlaceSearchKeyword(event.target.value)}
                        onKeyDown={handlePlaceSearchKeyDown}
                        placeholder="장소명 또는 키워드 검색 예: 전시관, 아지트보드게임카페"
                        style={{ width: "100%", paddingRight: placeSearchKeyword ? "40px" : undefined }}
                    />

                    {placeSearchKeyword && (
                        <button
                            type="button"
                            onClick={handleClearPlaceSearchKeyword}
                            aria-label="검색어 지우기"
                            title="검색어 지우기"
                            style={{
                                position: "absolute",
                                top: "50%",
                                right: "10px",
                                transform: "translateY(-50%)",
                                width: "24px",
                                height: "24px",
                                border: "0",
                                borderRadius: "50%",
                                background: "transparent",
                                color: "#666",
                                cursor: "pointer",
                                fontSize: "18px",
                                lineHeight: "24px",
                                padding: 0,
                            }}
                        >
                            ×
                        </button>
                    )}
                </div>

                <button type="button" onClick={handlePlaceKeywordSearch}>
                    장소 검색
                </button>

                <button type="button" onClick={handleReloadMapPlaces}>
                    현재 지도 새로고침
                </button>

                <div className="course-builder-category-buttons">
                    {THEME_BUTTONS.map((theme) => (
                        <button
                            key={theme.value}
                            type="button"
                            className={
                                activeTheme === theme.value
                                    ? "course-builder-category-button active"
                                    : "course-builder-category-button"
                            }
                            onClick={() => handleThemeClick(theme.value)}
                        >
                            {theme.label}
                        </button>
                    ))}
                </div>

                {activeTheme === "FOOD_TOUR" && (
                    <div className="course-builder-food-subcategory-buttons">
                        {FOOD_SUBCATEGORY_BUTTONS.map((subcategory) => (
                            <button
                                key={subcategory.value}
                                type="button"
                                className={
                                    activeFoodSubcategory === subcategory.value
                                        ? "course-builder-food-subcategory-button active"
                                        : "course-builder-food-subcategory-button"
                                }
                                onClick={() => handleFoodSubcategoryClick(subcategory.value)}
                            >
                                {subcategory.label}
                            </button>
                        ))}
                    </div>
                )}

                {isLoadingPlaces && (
                    <p className="course-builder-loading">장소를 불러오는 중입니다...</p>
                )}
            </section>

            <main className="course-builder-layout">
                <section className="course-builder-map-area">
                    <div ref={mapContainerRef} className="course-builder-map"></div>
                </section>

                <section className="course-builder-list-area">
                    <div className="course-builder-list-header">
                        <h2>지도에서 선택한 장소</h2>

                        {visibleMarkerSelectedPlaces.length > 0 && (
                            <button type="button" onClick={clearMarkerSelectedPlaces}>
                                선택 초기화
                            </button>
                        )}
                    </div>

                    {visibleMarkerSelectedPlaces.length === 0 && (
                        <p className="course-builder-empty">
                            지도에서 마커를 클릭하면 이곳에 선택한 장소만 표시됩니다.
                        </p>
                    )}

                    {visibleMarkerSelectedPlaces.map((place) => {
                        const placeKey = getPlaceUniqueKey(place);

                        return (
                            <article key={placeKey} className="course-builder-place-card">
                                <div>
                                    <button
                                        type="button"
                                        className="course-builder-place-name-button"
                                        onClick={() => handleMoveToPlace(place)}
                                    >
                                        {place.name}
                                    </button>
                                    <p>
                                        {getPlaceDisplayLabel(place)} / {place.region} / <span className="course-builder-place-type">{getIndoorOutdoorLabel(place)}</span>
                                    </p>
                                    <p>{place.address}</p>
                                    {place.phone && <p>{place.phone}</p>}
                                    {place.rating > 0 && (
                                        <p>
                                            평점 {place.rating} / 리뷰 {place.reviewCount || 0}개
                                        </p>
                                    )}
                                </div>

                                <div className="course-builder-place-actions">
                                    <button type="button" onClick={() => addPlaceToCourse(place)}>
                                        코스 추가
                                    </button>

                                    <button
                                        type="button"
                                        onClick={() => removeMarkerSelectedPlace(placeKey)}
                                    >
                                        선택 해제
                                    </button>
                                </div>
                            </article>
                        );
                    })}
                </section>

                <section className="course-builder-selected-area">
                    <h2>내 코스</h2>

                    <input
                        value={courseTitle}
                        onChange={(event) => setCourseTitle(event.target.value)}
                        placeholder="코스 제목을 입력해주세요"
                    />

                    <textarea
                        value={courseDescription}
                        onChange={(event) => setCourseDescription(event.target.value)}
                        placeholder="코스 설명을 입력해주세요"
                    />

                    {selectedPlaces.length === 0 && (
                        <p className="course-builder-empty">아직 추가한 장소가 없습니다.</p>
                    )}

                    {selectedPlaces.length >= 2 && (
                        <p className="course-builder-route-auto-status">
                            {isCalculatingRoutes
                                ? "장소가 변경되어 이동거리를 자동 계산하는 중입니다..."
                                : "장소를 추가하거나 삭제하거나 순서를 바꾸면 이동거리가 자동으로 다시 계산됩니다."}
                        </p>
                    )}

                    {selectedPlaces.map((place, index) => {
                        const placeKey = getCourseItemKey(place);
                        const nextPlace = selectedPlaces[index + 1];
                        const hasNextPlaceInSameDay =
                            nextPlace &&
                            normalizeDayNo(nextPlace.dayNo) === normalizeDayNo(place.dayNo);

                        return (
                            <article key={placeKey} className="course-builder-selected-card">
                                <button
                                    type="button"
                                    className="course-builder-place-name-button course-builder-selected-name-button"
                                    onClick={() => handleMoveToPlace(place)}
                                >
                                    {index + 1}. {place.name}
                                </button>
                                <p>
                                    {getPlaceDisplayLabel(place)} / {place.region} / <span className="course-builder-place-type">{getIndoorOutdoorLabel(place)}</span>
                                </p>

                                <div className="course-builder-schedule-fields">
                                    <label>
                                        방문 일차
                                        <input
                                            type="number"
                                            min="1"
                                            value={place.dayNo ?? 1}
                                            onChange={(event) =>
                                                updateSelectedPlaceField(
                                                    index,
                                                    "dayNo",
                                                    event.target.value
                                                )
                                            }
                                        />
                                    </label>

                                    <label>
                                        방문 시간
                                        <input
                                            type="time"
                                            value={place.visitTime || ""}
                                            onChange={(event) =>
                                                updateSelectedPlaceField(
                                                    index,
                                                    "visitTime",
                                                    event.target.value
                                                )
                                            }
                                        />
                                    </label>

                                    <label>
                                        체류 시간(분)
                                        <input
                                            type="number"
                                            min="0"
                                            step="10"
                                            value={place.stayMinutes ?? ""}
                                            onChange={(event) =>
                                                updateSelectedPlaceField(
                                                    index,
                                                    "stayMinutes",
                                                    event.target.value
                                                )
                                            }
                                        />
                                    </label>
                                </div>

                                {hasNextPlaceInSameDay && (
                                    <p className="course-builder-route-info">
                                        다음 장소까지: {formatDistanceMeter(place.moveDistanceM)} / {formatDurationMinute(place.moveDurationMin)}
                                        {place.routeToPlaceName ? ` → ${place.routeToPlaceName}` : ""}
                                    </p>
                                )}

                                {nextPlace && !hasNextPlaceInSameDay && (
                                    <p className="course-builder-route-muted">
                                        다음 장소는 다른 일차라 이동거리를 계산하지 않습니다.
                                    </p>
                                )}

                                {place.routeStatusMessage && !place.moveDistanceM && (
                                    <p className="course-builder-route-error">
                                        이동거리 계산 실패: {place.routeStatusMessage}
                                    </p>
                                )}

                                <div className="course-builder-order-buttons">
                                    <button type="button" onClick={() => movePlace(index, -1)}>
                                        위로
                                    </button>
                                    <button type="button" onClick={() => movePlace(index, 1)}>
                                        아래로
                                    </button>
                                    <button
                                        type="button"
                                        onClick={() => removePlaceFromCourse(placeKey)}
                                    >
                                        삭제
                                    </button>
                                </div>
                            </article>
                        );
                    })}

                    <button
                        type="button"
                        className="course-builder-save-button"
                        onClick={handleSaveCourse}
                        disabled={isCalculatingRoutes}
                    >
                        {isCalculatingRoutes ? "이동거리 계산 중..." : "코스 저장"}
                    </button>
                </section>
            </main>
        </div>
    );
}

export default CourseBuilderPage;

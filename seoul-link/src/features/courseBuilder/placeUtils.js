import { DEFAULT_REGION } from "./courseBuilderConstants";
import {
    DEFAULT_FOOD_SUBCATEGORY,
    FOOD_SUBCATEGORY_BY_VALUE,
    MARKER_ICON_BY_BASE_CATEGORY,
    MARKER_ICON_BY_THEME,
    THEME_BY_VALUE,
} from "./courseThemes";
import {
    COMMON_EXCLUDE_PLACE_WORDS,
    SHOPPING_INCLUDE_PLACE_WORDS,
    THEME_EXCLUDE_PLACE_WORDS,
} from "./placeFilterRules";
import { includesAnyWord, normalizeSearchText } from "./textUtils";

export const getPlaceSearchText = (place) =>
    `${place?.name || ""} ${place?.apiCategory || ""} ${place?.region || ""} ${place?.address || ""} ${place?.roadAddress || ""} ${place?.description || ""}`;

export const isValidPlaceForTheme = (place, themeValue) => {
    if (!place) return false;

    const searchText = getPlaceSearchText(place);

    if (includesAnyWord(searchText, COMMON_EXCLUDE_PLACE_WORDS)) return false;
    if (includesAnyWord(searchText, THEME_EXCLUDE_PLACE_WORDS[themeValue] || [])) return false;

    if (themeValue === "SHOPPING_HOTPLACE") {
        return includesAnyWord(searchText, SHOPPING_INCLUDE_PLACE_WORDS);
    }

    return true;
};

export const getThemeLabel = (themeValue) => THEME_BY_VALUE[themeValue]?.label || themeValue || "";
export const getFoodSubcategoryLabel = (subcategoryValue) => FOOD_SUBCATEGORY_BY_VALUE[subcategoryValue]?.label || "전체";

export const getBaseCategoryLabel = (category) => {
    if (category === "TOUR") return "관광지";
    if (category === "RESTAURANT") return "음식점";
    if (category === "CAFE") return "카페";
    if (category === "HOTEL") return "숙소";
    return category || "";
};

export const normalizeBaseCategory = (category) => {
    if (["TOUR", "RESTAURANT", "CAFE", "HOTEL"].includes(category)) {
        return category;
    }

    return "TOUR";
};

export const inferBaseCategoryFromKakaoPlace = (item, fallbackBaseCategory) => {
    if (fallbackBaseCategory) return fallbackBaseCategory;

    const categoryText = item.category_name || "";

    if (categoryText.includes("카페")) return "CAFE";
    if (categoryText.includes("음식점")) return "RESTAURANT";
    if (categoryText.includes("숙박") || categoryText.includes("호텔") || categoryText.includes("모텔") || categoryText.includes("펜션")) return "HOTEL";

    return "TOUR";
};

export const getPlaceDisplayLabel = (place) => {
    if (place.themeCategory && place.themeCategory !== "ALL") {
        return getThemeLabel(place.themeCategory);
    }

    return getBaseCategoryLabel(place.category);
};

export const getFocusThemeByPlace = (place) => {
    if (place?.themeCategory && place.themeCategory !== "ALL") return place.themeCategory;

    const baseCategory = normalizeBaseCategory(place?.category);

    if (baseCategory === "RESTAURANT") return "FOOD_TOUR";
    if (baseCategory === "CAFE") return "CAFE_TOUR";
    if (baseCategory === "HOTEL") return "HOTEL_STAY";

    return "PALACE_CULTURE";
};

export const inferThemeFromSearchText = (text, fallbackTheme = "PALACE_CULTURE") => {
    const keyword = normalizeSearchText(text);

    if (!keyword) return fallbackTheme;

    if (includesAnyWord(keyword, ["카페", "디저트", "베이커리", "보드게임카페", "보드카페"])) return "CAFE_TOUR";
    if (includesAnyWord(keyword, ["맛집", "음식", "식당", "한식", "양식", "중식", "일식", "분식", "고기", "국밥", "파스타", "초밥"])) return "FOOD_TOUR";
    if (includesAnyWord(keyword, ["호텔", "숙소", "게스트하우스", "모텔"])) return "HOTEL_STAY";
    if (includesAnyWord(keyword, ["데이트", "루프탑", "전망"])) return "DATE";
    if (includesAnyWord(keyword, ["야경", "서울타워", "낙산"])) return "NIGHT_VIEW";
    if (includesAnyWord(keyword, ["한강", "공원", "산책", "숲"])) return "NATURE_HANGANG";
    if (includesAnyWord(keyword, ["쇼핑", "백화점", "몰", "시장", "핫플", "팝업"])) return "SHOPPING_HOTPLACE";
    if (includesAnyWord(keyword, ["궁", "궁궐", "문화", "박물관", "미술관", "전시", "전시관", "공연"])) return "PALACE_CULTURE";

    return fallbackTheme;
};

export const inferThemeFromPlaceAndKeyword = (place, keyword, fallbackTheme = "PALACE_CULTURE") => {
    const baseCategory = normalizeBaseCategory(place?.category);

    if (baseCategory === "CAFE") return "CAFE_TOUR";
    if (baseCategory === "RESTAURANT") return "FOOD_TOUR";
    if (baseCategory === "HOTEL") return "HOTEL_STAY";

    return inferThemeFromSearchText(`${place?.name || ""} ${place?.apiCategory || ""} ${keyword || ""}`, fallbackTheme);
};

export const isExactPlaceNameMatch = (place, keyword) => normalizeSearchText(place?.name) === normalizeSearchText(keyword);

export const getIndoorOutdoorLabel = (place) => {
    if (place.indoorYn === "Y") return "실내";
    if (place.indoorYn === "N") return "야외";

    const themeCategory = place.themeCategory || "";
    const baseCategory = place.category || "";
    const text = `${place.name || ""} ${place.apiCategory || ""} ${place.address || ""}`;

    if (["HOTEL", "CAFE", "RESTAURANT"].includes(baseCategory)) return "실내";
    if (["HOTEL_STAY", "CAFE_TOUR", "FOOD_TOUR", "SHOPPING_HOTPLACE"].includes(themeCategory)) return "실내";
    if (["NATURE_HANGANG", "NIGHT_VIEW"].includes(themeCategory)) return "야외";
    if (includesAnyWord(text, ["공원", "한강", "산", "숲", "광장"])) return "야외";
    if (includesAnyWord(text, ["박물관", "미술관", "전시", "백화점", "몰", "카페", "호텔"])) return "실내";

    return "실내/야외";
};

export const getMarkerImageUrl = (place) => {
    if (place.themeCategory && MARKER_ICON_BY_THEME[place.themeCategory]) {
        return MARKER_ICON_BY_THEME[place.themeCategory];
    }

    return MARKER_ICON_BY_BASE_CATEGORY[normalizeBaseCategory(place.category)] || MARKER_ICON_BY_THEME.PALACE_CULTURE;
};

export const extractRegion = (address, fallbackRegion) => {
    if (!address) return fallbackRegion;
    return address.split(" ").find((part) => part.endsWith("구")) || fallbackRegion;
};

export const isSeoulKakaoPlace = (item) => {
    const addressText = `${item.address_name || ""} ${item.road_address_name || ""}`;
    return addressText.includes("서울") || addressText.includes("서울특별시");
};

export const getPlaceKey = (place) => {
    if (place.apiProvider && place.apiPlaceId) return `${place.apiProvider}-${place.apiPlaceId}`;
    if (place.placeId) return `DB-${place.placeId}`;
    return `${place.name}-${place.latitude}-${place.longitude}`;
};

export const mergeUniquePlaces = (placeList) => {
    const placeMap = new Map();

    placeList.forEach((place) => {
        if (!place) return;

        const key = getPlaceKey(place);
        const existingPlace = placeMap.get(key);

        if (!existingPlace || (existingPlace.dataSource !== "DB" && place.dataSource === "DB")) {
            placeMap.set(key, place);
        }
    });

    return Array.from(placeMap.values());
};

export const convertKakaoPlace = ({ item, themeValue, regionValue, fallbackBaseCategory }) => {
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

export const convertDbPlace = (item, fallbackThemeValue) => ({
    uid: `DB-${item.placeId}`,
    placeId: item.placeId,
    apiProvider: item.apiProvider || null,
    apiPlaceId: item.apiPlaceId || null,
    contentId: item.contentId || null,
    name: item.name,
    category: normalizeBaseCategory(item.category),
    themeCategory: item.themeCategory || fallbackThemeValue,
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
});

export const filterPlacesByFoodSubcategory = (placeList, foodSubcategoryValue) => {
    if (foodSubcategoryValue === DEFAULT_FOOD_SUBCATEGORY) return placeList;

    const subcategoryConfig = FOOD_SUBCATEGORY_BY_VALUE[foodSubcategoryValue];
    if (!subcategoryConfig) return placeList;

    const keywords = subcategoryConfig.keywords.map((keyword) => normalizeSearchText(keyword));

    return placeList.filter((place) => {
        const text = normalizeSearchText(getPlaceSearchText(place));
        return keywords.some((keyword) => keyword && text.includes(keyword));
    });
};

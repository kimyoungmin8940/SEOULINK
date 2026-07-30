import { DEFAULT_REGION } from "./courseBuilderConstants";
import { DEFAULT_FOOD_SUBCATEGORY } from "./courseThemes";
import { fetchCourseBuilderDbPlaces } from "./api/courseBuilderApi";
import { isPlaceInsideMapBounds } from "./mapUtils";
import {
    convertDbPlace,
    filterPlacesByFoodSubcategory,
    getPlaceSearchText,
    inferThemeFromPlaceAndKeyword,
    inferThemeFromSearchText,
    isExactPlaceNameMatch,
    isHardRejectedPlace,
    isValidPlaceForTheme,
    mergeUniquePlaces,
} from "./placeUtils";
import { normalizeSearchText } from "./textUtils";
import {
    searchKakaoKeywordBySeoulGrid,
    searchKakaoPlacesForTheme,
} from "./kakaoPlaceSearchService";

const fetchDbPlacesForTheme = async ({ map, themeValue, regionValue, maxCount, foodSubcategoryValue = DEFAULT_FOOD_SUBCATEGORY }) => {
    const dbFetchLimit = Math.min(300, Math.max(maxCount * 3, 120));
    const dbPlaces = await fetchCourseBuilderDbPlaces({
        theme: themeValue,
        region: regionValue,
        limit: dbFetchLimit,
    });

    const convertedPlaces = dbPlaces
        .map((place) => convertDbPlace(place, themeValue))
        .filter((place) => isPlaceInsideMapBounds(map, place))
        .filter((place) => isValidPlaceForTheme(place, themeValue));

    if (themeValue !== "FOOD_TOUR") return convertedPlaces;

    return filterPlacesByFoodSubcategory(convertedPlaces, foodSubcategoryValue);
};

export const searchPlacesForTheme = async ({ map, themeValue, regionValue, maxCount, foodSubcategoryValue = DEFAULT_FOOD_SUBCATEGORY }) => {
    const dbPlaces = await fetchDbPlacesForTheme({ map, themeValue, regionValue, maxCount, foodSubcategoryValue });
    const kakaoPlaces = await searchKakaoPlacesForTheme({ map, themeValue, regionValue, maxCount, foodSubcategoryValue });

    return mergeUniquePlaces([...dbPlaces, ...kakaoPlaces]).slice(0, maxCount);
};

const fetchDbPlacesByKeyword = async ({ keyword, maxCount }) => {
    const searchKeyword = keyword.trim();
    const normalizedKeyword = normalizeSearchText(searchKeyword);

    if (!normalizedKeyword) return [];

    const fallbackTheme = inferThemeFromSearchText(searchKeyword, "DATE");
    const dbPlaces = await fetchCourseBuilderDbPlaces({
        theme: "ALL",
        region: DEFAULT_REGION,
        limit: Math.min(300, Math.max(maxCount * 3, 120)),
    });

    return dbPlaces
        .map((place) => convertDbPlace(place, fallbackTheme))
        .filter((place) => normalizeSearchText(getPlaceSearchText(place)).includes(normalizedKeyword))
        .map((place) => ({
            ...place,
            themeCategory: inferThemeFromPlaceAndKeyword(place, searchKeyword, place.themeCategory),
        }))
        .filter((place) => isValidPlaceForTheme(place, place.themeCategory))
        .slice(0, maxCount);
};

export const searchPlacesByKeyword = async ({ keyword, maxCount }) => {
    const searchKeyword = keyword.trim();
    const inferredTheme = inferThemeFromSearchText(searchKeyword, "DATE");

    const dbPlaces = await fetchDbPlacesByKeyword({ keyword: searchKeyword, maxCount });
    const kakaoPlaces = await searchKakaoKeywordBySeoulGrid({
        keyword: searchKeyword,
        themeValue: inferredTheme,
        fallbackBaseCategory: null,
        maxCount,
    });

    return mergeUniquePlaces(
        [...dbPlaces, ...kakaoPlaces].map((place) => {
            const themeCategory = inferThemeFromPlaceAndKeyword(place, searchKeyword, place.themeCategory);
            return { ...place, themeCategory };
        })
    )
        .filter((place) => {
            if (isHardRejectedPlace(place)) return false;

            return (
                isExactPlaceNameMatch(place, searchKeyword)
                || isValidPlaceForTheme(place, place.themeCategory)
            );
        })
        .slice(0, maxCount);
};

import { DEFAULT_REGION, KAKAO_PAGE_SIZE } from "./courseBuilderConstants";
import { DEFAULT_FOOD_SUBCATEGORY, FOOD_SUBCATEGORY_BY_VALUE, THEME_BY_VALUE } from "./courseThemes";
import {
    createBoundsGrid,
    createSeoulSearchBoundsGrid,
    getGridSearchOption,
} from "./mapUtils";
import {
    convertKakaoPlace,
    isSeoulKakaoPlace,
    isValidPlaceForTheme,
    mergeUniquePlaces,
} from "./placeUtils";
import { wait } from "./textUtils";

const searchKakaoCategoryByBoundsCell = ({ bounds, groupCode, themeValue, regionValue, fallbackBaseCategory, pageLimit }) => {
    const kakao = window.kakao;

    return new Promise((resolve, reject) => {
        const placesService = new kakao.maps.services.Places();
        const collectedPlaces = [];

        const callback = (data, status, pagination) => {
            if (status === kakao.maps.services.Status.OK) {
                const convertedPlaces = data
                    .filter((item) => isSeoulKakaoPlace(item))
                    .map((item) => convertKakaoPlace({ item, themeValue, regionValue, fallbackBaseCategory }))
                    .filter(Boolean);

                collectedPlaces.push(...convertedPlaces);

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

const searchKakaoKeywordByBoundsCell = ({ bounds, keyword, themeValue, regionValue, fallbackBaseCategory, pageLimit }) => {
    const kakao = window.kakao;

    return new Promise((resolve, reject) => {
        const placesService = new kakao.maps.services.Places();
        const collectedPlaces = [];

        const callback = (data, status, pagination) => {
            if (status === kakao.maps.services.Status.OK) {
                const convertedPlaces = data
                    .filter((item) => isSeoulKakaoPlace(item))
                    .map((item) => convertKakaoPlace({ item, themeValue, regionValue, fallbackBaseCategory }))
                    .filter(Boolean);

                collectedPlaces.push(...convertedPlaces);

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

const searchKakaoCategoryByMapGrid = async ({ map, groupCode, themeValue, regionValue, fallbackBaseCategory, maxCount }) => {
    const { gridSize, pageLimitPerCell } = getGridSearchOption(maxCount);
    const boundsList = createBoundsGrid(map, gridSize);
    let mergedPlaces = [];

    for (const bounds of boundsList) {
        if (mergedPlaces.length >= maxCount) break;

        const cellPlaces = await searchKakaoCategoryByBoundsCell({
            bounds,
            groupCode,
            themeValue,
            regionValue,
            fallbackBaseCategory,
            pageLimit: pageLimitPerCell,
        });

        mergedPlaces = mergeUniquePlaces([...mergedPlaces, ...cellPlaces]).slice(0, maxCount);
        await wait(70);
    }

    return mergedPlaces;
};

const searchKakaoKeywordByGrid = async ({ boundsList, keyword, themeValue, regionValue, fallbackBaseCategory, maxCount }) => {
    const { pageLimitPerCell } = getGridSearchOption(maxCount);
    let mergedPlaces = [];

    for (const bounds of boundsList) {
        if (mergedPlaces.length >= maxCount) break;

        const cellPlaces = await searchKakaoKeywordByBoundsCell({
            bounds,
            keyword,
            themeValue,
            regionValue,
            fallbackBaseCategory,
            pageLimit: pageLimitPerCell,
        });

        mergedPlaces = mergeUniquePlaces([...mergedPlaces, ...cellPlaces]).slice(0, maxCount);
        await wait(70);
    }

    return mergedPlaces;
};

const searchKakaoKeywordByMapGrid = ({ map, keyword, themeValue, regionValue, fallbackBaseCategory, maxCount }) => {
    const { gridSize } = getGridSearchOption(maxCount);

    return searchKakaoKeywordByGrid({
        boundsList: createBoundsGrid(map, gridSize),
        keyword,
        themeValue,
        regionValue,
        fallbackBaseCategory,
        maxCount,
    });
};

export const searchKakaoKeywordBySeoulGrid = ({ keyword, themeValue, fallbackBaseCategory, maxCount }) => {
    const { gridSize } = getGridSearchOption(maxCount);

    return searchKakaoKeywordByGrid({
        boundsList: createSeoulSearchBoundsGrid(gridSize),
        keyword,
        themeValue,
        regionValue: DEFAULT_REGION,
        fallbackBaseCategory,
        maxCount,
    });
};

export const searchKakaoPlacesForTheme = async ({
    map,
    themeValue,
    regionValue,
    maxCount,
    foodSubcategoryValue = DEFAULT_FOOD_SUBCATEGORY,
}) => {
    const themeConfig = THEME_BY_VALUE[themeValue];
    if (!themeConfig) return [];

    const foodSubcategoryConfig = FOOD_SUBCATEGORY_BY_VALUE[foodSubcategoryValue];
    const isFoodSubcategorySearch =
        themeValue === "FOOD_TOUR" &&
        foodSubcategoryConfig &&
        foodSubcategoryConfig.value !== DEFAULT_FOOD_SUBCATEGORY;

    const kakaoCategories = isFoodSubcategorySearch ? [] : themeConfig.kakaoCategories || [];
    const kakaoKeywords = isFoodSubcategorySearch ? foodSubcategoryConfig.keywords : themeConfig.kakaoKeywords || [];
    const searchCount = Math.max(1, kakaoCategories.length + kakaoKeywords.length);
    const maxCountPerSearch =
        themeValue === "FOOD_TOUR" && !isFoodSubcategorySearch
            ? maxCount
            : Math.min(maxCount, Math.max(15, Math.ceil(maxCount / searchCount) + 10));

    let mergedPlaces = [];

    for (const categorySearch of kakaoCategories) {
        if (mergedPlaces.length >= maxCount) break;

        const categoryPlaces = await searchKakaoCategoryByMapGrid({
            map,
            groupCode: categorySearch.groupCode,
            themeValue,
            regionValue,
            fallbackBaseCategory: categorySearch.baseCategory,
            maxCount: maxCountPerSearch,
        });

        mergedPlaces = mergeUniquePlaces([...mergedPlaces, ...categoryPlaces]).slice(0, maxCount);
    }

    for (const keyword of kakaoKeywords) {
        if (mergedPlaces.length >= maxCount) break;

        const keywordPlaces = await searchKakaoKeywordByMapGrid({
            map,
            keyword,
            themeValue,
            regionValue,
            fallbackBaseCategory: themeConfig.markerCategory || "TOUR",
            maxCount: maxCountPerSearch,
        });

        mergedPlaces = mergeUniquePlaces([...mergedPlaces, ...keywordPlaces]).slice(0, maxCount);
    }

    return mergedPlaces.filter((place) => isValidPlaceForTheme(place, themeValue));
};

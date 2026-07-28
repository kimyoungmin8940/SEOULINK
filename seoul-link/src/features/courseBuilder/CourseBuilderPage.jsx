import { useCallback, useEffect, useRef, useState } from "react";
import { loadKakaoMap } from "./loadKakaoMap";
import { saveCourseBuilderCourse } from "./api/courseBuilderApi";
import CourseBuilderFilter from "./CourseBuilderFilter";
import CourseBuilderMap from "./CourseBuilderMap";
import CoursePlaceList from "./CoursePlaceList";
import SelectedMapPlaceList from "./SelectedMapPlaceList";
import {
    AUTO_SEARCH_DELAY_MS,
    DEFAULT_REGION,
    MAX_COUNT_KEYWORD_SEARCH,
    MAX_COUNT_PER_THEME_ALL,
    MAX_COUNT_SINGLE_THEME,
    PLACE_FOCUS_MAP_LEVEL,
    SEARCH_RESULT_MAP_LEVEL,
} from "./courseBuilderConstants";
import { DEFAULT_FOOD_SUBCATEGORY, SEARCHABLE_THEMES, THEME_BY_VALUE } from "./courseThemes";
import { useCourseRoutes } from "./useCourseRoutes";
import { searchPlacesByKeyword, searchPlacesForTheme } from "./placeSearchService";
import {
    calculateCourseTimeSummary,
    enrichPlaceForCourse,
    getCoursePlaceKey,
    normalizeDayNo,
    normalizeRoutePoints,
    normalizeStayMinutes,
    resetRouteInfo,
} from "./coursePlaceUtils";
import {
    createMarkerImage,
    getMapLevelByRegion,
    getRegionCenter,
    fitMapToPlaces,
    makeMapSearchKey,
    moveMapToPlace,
    moveMapToRegion,
} from "./mapUtils";
import {
    getFocusThemeByPlace,
    getFoodSubcategoryLabel,
    getIndoorOutdoorLabel,
    getPlaceDisplayLabel,
    getPlaceKey,
    getThemeLabel,
    isExactPlaceNameMatch,
    mergeUniquePlaces,
    normalizeBaseCategory,
} from "./placeUtils";
import { escapeHtml } from "./textUtils";
import { authStore } from "../../store/authStore";
import { formatDurationMinute } from "./formatUtils";
import "./CourseBuilder.css";

const clampCoursePlacesToDayCount = (coursePlaces, dayCount) => {
    let changed = false;
    const clampedPlaces = coursePlaces.map((place) => {
        const currentDayNo = normalizeDayNo(place.dayNo);
        const nextDayNo = Math.min(currentDayNo, dayCount);

        if (currentDayNo === nextDayNo) return place;

        changed = true;
        return { ...place, dayNo: nextDayNo };
    });

    return changed ? resetRouteInfo(clampedPlaces) : coursePlaces;
};

function CourseBuilderPage({ initialTheme = "ALL" }) {
    const normalizedInitialTheme = THEME_BY_VALUE[initialTheme] ? initialTheme : "ALL";
    const mapContainerRef = useRef(null);
    const mapRef = useRef(null);
    const markerRefs = useRef([]);
    const routePolylineRefs = useRef([]);
    const routeOrderOverlayRefs = useRef([]);
    const infoWindowRefs = useRef([]);
    const markerInfoWindowMapRef = useRef(new Map());
    const pendingInfoWindowRequestRef = useRef(null);

    const autoSearchTimerRef = useRef(null);
    const activeThemeRef = useRef(normalizedInitialTheme);
    const activeFoodSubcategoryRef = useRef(DEFAULT_FOOD_SUBCATEGORY);
    const regionRef = useRef(DEFAULT_REGION);
    const suppressNextIdleSearchRef = useRef(false);
    const keepKeywordSearchResultRef = useRef(false);
    const lastSearchKeyRef = useRef("");
    const searchRequestIdRef = useRef(0);

    const [mapReady, setMapReady] = useState(false);
    const [mapStatus, setMapStatus] = useState("카카오 지도를 불러오는 중입니다.");
    const [region, setRegion] = useState(DEFAULT_REGION);
    const [mapPlaces, setMapPlaces] = useState([]);
    const [selectedMapPlaces, setSelectedMapPlaces] = useState([]);
    const [coursePlaces, setCoursePlaces] = useState([]);
    const [activeTheme, setActiveTheme] = useState(normalizedInitialTheme);
    const [activeFoodSubcategory, setActiveFoodSubcategory] = useState(DEFAULT_FOOD_SUBCATEGORY);
    const [placeSearchKeyword, setPlaceSearchKeyword] = useState("");
    const [isLoadingPlaces, setIsLoadingPlaces] = useState(false);
    const [courseTitle, setCourseTitle] = useState("");
    const [courseDescription, setCourseDescription] = useState("");
    const [tripDayCount, setTripDayCount] = useState(1);
    const [activeDayNo, setActiveDayNo] = useState(1);

    const courseTimeSummary = calculateCourseTimeSummary(coursePlaces);
    const activeDayPlaces = coursePlaces.filter((place) => normalizeDayNo(place.dayNo) === activeDayNo);
    const activeDayTimeSummary = calculateCourseTimeSummary(activeDayPlaces);
    const totalDayCount = tripDayCount;
    const { isCalculatingRoutes, setIsCalculatingRoutes, calculateRoutesForPlaces } = useCourseRoutes({
        coursePlaces,
        setCoursePlaces,
        setMapStatus,
    });

    useEffect(() => {
        regionRef.current = region;
    }, [region]);

    useEffect(() => {
        activeThemeRef.current = activeTheme;
    }, [activeTheme]);

    useEffect(() => {
        activeFoodSubcategoryRef.current = activeFoodSubcategory;
    }, [activeFoodSubcategory]);

    const handleTripDayCountChange = useCallback((event) => {
        const nextDayCount = Math.min(7, Math.max(1, Number(event.target.value) || 1));

        setTripDayCount(nextDayCount);
        setActiveDayNo((currentDayNo) => Math.min(currentDayNo, nextDayCount));
        setCoursePlaces((currentPlaces) => clampCoursePlacesToDayCount(currentPlaces, nextDayCount));
    }, []);

    const handlePreviousDay = useCallback(() => {
        setActiveDayNo((currentDayNo) => Math.max(1, currentDayNo - 1));
    }, []);

    const handleNextDay = useCallback(() => {
        setActiveDayNo((currentDayNo) => Math.min(totalDayCount, currentDayNo + 1));
    }, [totalDayCount]);

    const searchPlacesByCurrentMap = useCallback(
        async ({
                   regionValue,
                   themeValue,
                   foodSubcategoryValue = activeFoodSubcategoryRef.current,
                   moveMap = false,
                   triggeredByAuto = false,
                   requiredPlace = null,
               }) => {
            const map = mapRef.current;
            if (!map) return;

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
                if (triggeredByAuto && lastSearchKeyRef.current === searchKey) return;

                lastSearchKeyRef.current = searchKey;

                const searchTargets = themeValue === "ALL" ? SEARCHABLE_THEMES : [THEME_BY_VALUE[themeValue]];
                const maxCount = themeValue === "ALL" ? MAX_COUNT_PER_THEME_ALL : MAX_COUNT_SINGLE_THEME;
                const countText = themeValue === "ALL" ? `테마별 최대 ${MAX_COUNT_PER_THEME_ALL}개` : `최대 ${MAX_COUNT_SINGLE_THEME}개`;
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
                    if (!themeConfig) continue;

                    const themePlaces = await searchPlacesForTheme({
                        map,
                        themeValue: themeConfig.value,
                        regionValue,
                        maxCount,
                        foodSubcategoryValue,
                    });

                    results.push(...themePlaces);
                }

                if (requestId !== searchRequestIdRef.current) return;

                const mergedPlaces = mergeUniquePlaces(requiredPlace ? [requiredPlace, ...results] : results);
                setMapPlaces(mergedPlaces);

                if (mergedPlaces.length === 0) {
                    setMapStatus(`${regionValue} 현재 지도 화면에서 검색 결과가 없습니다. 지도를 조금 이동하거나 확대/축소해보세요.`);
                    return;
                }

                setMapStatus(
                    `${regionValue} 현재 지도 화면 ${
                        themeValue === "ALL" ? "전체" : `${getThemeLabel(themeValue)}${foodSubcategoryText}`
                    } 장소 ${mergedPlaces.length}개 표시 중 (${countText}, DB + 카카오 API)`
                );
            } catch (error) {
                console.error(error);
                setMapStatus("장소 데이터를 불러오지 못했습니다.");
                alert(`장소 데이터를 불러오지 못했습니다.\n\n${error.message}`);
            } finally {
                if (requestId === searchRequestIdRef.current) {
                    setIsLoadingPlaces(false);
                }
            }
        },
        []
    );

    const addSelectedMapPlace = useCallback((place) => {
        setSelectedMapPlaces((prev) => {
            const exists = prev.some((item) => getPlaceKey(item) === getPlaceKey(place));
            return exists ? prev : [...prev, place];
        });
    }, []);

    const openPlaceInfoWindow = useCallback(
        (place, options = {}) => {
            const { addToSelectedMapPlaces = false } = options;
            const placeKey = getPlaceKey(place);
            const markerInfoWindow = markerInfoWindowMapRef.current.get(placeKey);

            if (!markerInfoWindow || !mapRef.current) return false;

            infoWindowRefs.current.forEach((item) => item.close());
            markerInfoWindow.infoWindow.open(mapRef.current, markerInfoWindow.marker);

            if (addToSelectedMapPlaces) {
                addSelectedMapPlace(place);
            }

            return true;
        },
        [addSelectedMapPlace]
    );

    const handleThemeClick = useCallback(
        (themeValue) => {
            keepKeywordSearchResultRef.current = false;

            const nextFoodSubcategory = themeValue === "FOOD_TOUR" ? activeFoodSubcategoryRef.current : DEFAULT_FOOD_SUBCATEGORY;

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
        },
        [searchPlacesByCurrentMap]
    );

    const handleFoodSubcategoryClick = useCallback(
        (subcategoryValue) => {
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
        },
        [searchPlacesByCurrentMap]
    );

    const handleRegionChange = useCallback(
        (event) => {
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
        },
        [searchPlacesByCurrentMap]
    );

    const handleReloadMapPlaces = useCallback(() => {
        keepKeywordSearchResultRef.current = false;

        void searchPlacesByCurrentMap({
            regionValue: regionRef.current,
            themeValue: activeThemeRef.current,
            foodSubcategoryValue: activeFoodSubcategoryRef.current,
            moveMap: false,
        });
    }, [searchPlacesByCurrentMap]);

    const handleZoomIn = useCallback(() => {
        const map = mapRef.current;
        if (!map) return;

        map.setLevel(Math.max(1, map.getLevel() - 1), { animate: true });
    }, []);

    const handleZoomOut = useCallback(() => {
        const map = mapRef.current;
        if (!map) return;

        map.setLevel(Math.min(14, map.getLevel() + 1), { animate: true });
    }, []);

    const handlePlaceKeywordSearch = useCallback(async () => {
        const map = mapRef.current;
        const keyword = placeSearchKeyword.trim();

        if (!map) return;
        if (!keyword) {
            alert("검색어를 입력해주세요.");
            return;
        }

        const requestId = searchRequestIdRef.current + 1;
        searchRequestIdRef.current = requestId;

        try {
            setIsLoadingPlaces(true);
            keepKeywordSearchResultRef.current = true;
            setActiveTheme("ALL");
            activeThemeRef.current = "ALL";
            setActiveFoodSubcategory(DEFAULT_FOOD_SUBCATEGORY);
            activeFoodSubcategoryRef.current = DEFAULT_FOOD_SUBCATEGORY;
            setMapStatus(`서울 전체에서 '${keyword}' 장소를 검색하는 중입니다.`);

            const keywordPlaces = await searchPlacesByKeyword({ keyword, maxCount: MAX_COUNT_KEYWORD_SEARCH });

            if (requestId !== searchRequestIdRef.current) return;

            if (keywordPlaces.length === 0) {
                setMapPlaces([]);
                pendingInfoWindowRequestRef.current = null;
                setMapStatus(`서울 전체에서 '${keyword}' 검색 결과가 없습니다. 다른 검색어로 다시 시도해보세요.`);
                return;
            }

            suppressNextIdleSearchRef.current = true;

            const exactMatch = keywordPlaces.find((place) =>
                isExactPlaceNameMatch(place, keyword)
            );

            if (exactMatch) {
                pendingInfoWindowRequestRef.current = {
                    place: exactMatch,
                    addToSelectedMapPlaces: false,
                };
                setMapPlaces([exactMatch]);

                await moveMapToPlace(map, exactMatch, SEARCH_RESULT_MAP_LEVEL);
                setMapStatus(
                    `'${keyword}'와 정확히 일치하는 장소를 찾았습니다. 지도 레벨 ${SEARCH_RESULT_MAP_LEVEL}로 이동하고 정보창을 엽니다.`
                );
                return;
            }

            if (keywordPlaces.length === 1) {
                const onlyPlace = keywordPlaces[0];

                pendingInfoWindowRequestRef.current = {
                    place: onlyPlace,
                    addToSelectedMapPlaces: false,
                };
                setMapPlaces([onlyPlace]);

                await moveMapToPlace(map, onlyPlace, SEARCH_RESULT_MAP_LEVEL);
                setMapStatus(
                    `'${keyword}' 검색 결과 1곳을 찾았습니다. 지도 레벨 ${SEARCH_RESULT_MAP_LEVEL}로 이동하고 정보창을 엽니다.`
                );
                return;
            }

            pendingInfoWindowRequestRef.current = null;
            setMapPlaces(keywordPlaces);
            await fitMapToPlaces(map, keywordPlaces);

            setMapStatus(`서울 전체에서 '${keyword}' 검색 결과 ${keywordPlaces.length}곳이 모두 보이도록 지도를 맞췄습니다.`);
        } catch (error) {
            console.error(error);
            setMapStatus("장소 검색에 실패했습니다.");
            alert(`장소 검색에 실패했습니다.\n\n${error.message}`);
        } finally {
            if (requestId === searchRequestIdRef.current) {
                setIsLoadingPlaces(false);
            }
        }
    }, [placeSearchKeyword]);

    const handlePlaceSearchKeyDown = useCallback(
        (event) => {
            if (event.key === "Enter") {
                event.preventDefault();
                void handlePlaceKeywordSearch();
            }
        },
        [handlePlaceKeywordSearch]
    );

    const handleClearPlaceSearchKeyword = useCallback(() => {
        setPlaceSearchKeyword("");
    }, []);

    const handleMoveToPlace = useCallback(
        async (place) => {
            const map = mapRef.current;
            if (!map) return;

            const nextThemeValue = getFocusThemeByPlace(place);

            try {
                keepKeywordSearchResultRef.current = false;
                setActiveTheme(nextThemeValue);
                activeThemeRef.current = nextThemeValue;

                pendingInfoWindowRequestRef.current = {
                    place,
                    addToSelectedMapPlaces: false,
                };

                suppressNextIdleSearchRef.current = true;
                setMapStatus(`${place.name} 위치로 이동 중입니다. ${getThemeLabel(nextThemeValue)} 카테고리만 표시합니다.`);

                await moveMapToPlace(map, place, PLACE_FOCUS_MAP_LEVEL);
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
        },
        [searchPlacesByCurrentMap]
    );

    const handleClickableNameKeyDown = useCallback(
        (event, place) => {
            if (event.key === "Enter" || event.key === " ") {
                event.preventDefault();
                void handleMoveToPlace(place);
            }
        },
        [handleMoveToPlace]
    );

    const removeSelectedMapPlace = useCallback((placeKey) => {
        setSelectedMapPlaces((prev) => prev.filter((place) => getPlaceKey(place) !== placeKey));
    }, []);

    const clearSelectedMapPlaces = useCallback(() => {
        setSelectedMapPlaces([]);
    }, []);

    const addPlaceToCourse = useCallback((place) => {
        const placeKey = getPlaceKey(place);
        const alreadyAddedToActiveDay = coursePlaces.some(
            (item) => normalizeDayNo(item.dayNo) === activeDayNo && getPlaceKey(item) === placeKey
        );

        if (alreadyAddedToActiveDay) {
            alert("이미 이 날짜의 코스에 추가된 장소입니다.");
            return;
        }

        setCoursePlaces((prev) => {
            const exists = prev.some(
                (item) => normalizeDayNo(item.dayNo) === activeDayNo && getPlaceKey(item) === placeKey
            );
            if (exists) return prev;

            const newCoursePlace = enrichPlaceForCourse(place, activeDayNo);
            const nextDayIndex = prev.findIndex((item) => normalizeDayNo(item.dayNo) > activeDayNo);
            const nextPlaces = [...prev];

            if (nextDayIndex === -1) {
                nextPlaces.push(newCoursePlace);
            } else {
                nextPlaces.splice(nextDayIndex, 0, newCoursePlace);
            }

            return resetRouteInfo(nextPlaces);
        });
    }, [activeDayNo, coursePlaces]);

    const removePlaceFromCourse = useCallback((placeKey) => {
        setCoursePlaces((prev) => resetRouteInfo(prev.filter((place) => getCoursePlaceKey(place) !== placeKey)));
    }, []);

    const moveCoursePlace = useCallback((index, direction) => {
        setCoursePlaces((prev) => {
            const selectedPlace = prev[index];
            if (!selectedPlace) return prev;

            const selectedDayNo = normalizeDayNo(selectedPlace.dayNo);
            const sameDayIndexes = prev.reduce((indexes, place, placeIndex) => {
                if (normalizeDayNo(place.dayNo) === selectedDayNo) indexes.push(placeIndex);
                return indexes;
            }, []);
            const sameDayPosition = sameDayIndexes.indexOf(index);
            const nextSameDayPosition = sameDayPosition + direction;

            if (nextSameDayPosition < 0 || nextSameDayPosition >= sameDayIndexes.length) return prev;

            const nextIndex = sameDayIndexes[nextSameDayPosition];

            const copied = [...prev];
            [copied[index], copied[nextIndex]] = [copied[nextIndex], copied[index]];

            return resetRouteInfo(copied);
        });
    }, []);

    const updateCoursePlaceField = useCallback((index, fieldName, value) => {
        setCoursePlaces((prev) => {
            const updatedPlaces = prev.map((place, placeIndex) => {
                if (placeIndex !== index) return place;
                return { ...place, [fieldName]: value };
            });

            return fieldName === "dayNo" ? resetRouteInfo(updatedPlaces) : updatedPlaces;
        });
    }, []);

    const handleSaveCourse = useCallback(async () => {
        const memberId = Number(authStore.getMember()?.memberId);

        if (!Number.isSafeInteger(memberId) || memberId <= 0) {
            alert("로그인 정보를 확인할 수 없습니다. 다시 로그인해주세요.");
            return;
        }
        if (!courseTitle.trim()) {
            alert("코스 제목을 입력해주세요.");
            return;
        }

        if (!courseDescription.trim()) {
            alert("코스 설명을 입력해주세요.");
            return;
        }

        if (coursePlaces.length === 0) {
            alert("코스에 장소를 1개 이상 추가해주세요.");
            return;
        }

        let placesForSave = coursePlaces;

        if (coursePlaces.length >= 2) {
            try {
                setIsCalculatingRoutes(true);
                placesForSave = await calculateRoutesForPlaces(coursePlaces);
                setCoursePlaces(placesForSave);
            } catch (error) {
                console.error(error);
                const shouldContinue = window.confirm(`이동거리 계산에 실패했습니다.\n\n${error.message}\n\n이동거리 없이 코스를 저장할까요?`);
                if (!shouldContinue) {
                    setIsCalculatingRoutes(false);
                    return;
                }
            } finally {
                setIsCalculatingRoutes(false);
            }
        }

        const dayOrderCounters = new Map();
        const placesForRequest = placesForSave.map((place) => {
            const dayNo = normalizeDayNo(place.dayNo);
            const placeOrder = (dayOrderCounters.get(dayNo) || 0) + 1;
            dayOrderCounters.set(dayNo, placeOrder);

            return {
                placeId: place.placeId || null,
                apiProvider: place.apiProvider || "KAKAO",
                apiPlaceId: place.apiPlaceId || null,
                contentId: place.contentId || null,
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
                dayNo,
                placeOrder,
                memo: null,
                visitTime: place.visitTime || null,
                stayMinutes: normalizeStayMinutes(place.stayMinutes),
                moveDistanceM: place.moveDistanceM ?? null,
                moveDurationMin: place.moveDurationMin ?? null,
            };
        });

        const requestBody = {
            memberId,
            resultId: null,
            paymentId: null,
            title: courseTitle.trim(),
            description: courseDescription.trim(),
            travelCode: null,
            courseType: "CUSTOM",
            region,
            isPublic: "N",
            places: placesForRequest,
        };

        try {
            await saveCourseBuilderCourse(requestBody);
            alert("코스가 저장되었습니다");
            setCourseTitle("");
            setCourseDescription("");
            setCoursePlaces([]);
            setSelectedMapPlaces([]);
            setTripDayCount(1);
            setActiveDayNo(1);
        } catch (error) {
            console.error(error);
            alert(`코스 저장에 실패했습니다.\n\n${error.message}`);
        }
    }, [calculateRoutesForPlaces, courseDescription, coursePlaces, courseTitle, region, setIsCalculatingRoutes]);

    useEffect(() => {
        loadKakaoMap()
            .then(() => {
                const kakao = window.kakao;
                const centerInfo = getRegionCenter(DEFAULT_REGION);
                const map = new kakao.maps.Map(mapContainerRef.current, {
                    center: new kakao.maps.LatLng(centerInfo.latitude, centerInfo.longitude),
                    level: getMapLevelByRegion(DEFAULT_REGION),
                });

                mapRef.current = map;
                kakao.maps.event.addListener(map, "click", () => {
                    infoWindowRefs.current.forEach((infoWindow) => infoWindow.close());
                });
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
        if (!mapReady) return undefined;

        const timerId = window.setTimeout(() => {
            void searchPlacesByCurrentMap({
                regionValue: DEFAULT_REGION,
                themeValue: normalizedInitialTheme,
                foodSubcategoryValue: DEFAULT_FOOD_SUBCATEGORY,
                moveMap: false,
            });
        }, 0);

        return () => window.clearTimeout(timerId);
    }, [mapReady, normalizedInitialTheme, searchPlacesByCurrentMap]);

    useEffect(() => {
        if (!mapReady || !mapRef.current) return undefined;

        const kakao = window.kakao;
        const map = mapRef.current;

        const handleIdle = () => {
            if (suppressNextIdleSearchRef.current) {
                suppressNextIdleSearchRef.current = false;
                return;
            }

            if (keepKeywordSearchResultRef.current) return;

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
        if (!mapReady || !mapRef.current) return undefined;

        const kakao = window.kakao;
        const map = mapRef.current;

        routePolylineRefs.current.forEach((polyline) => polyline.setMap(null));
        routePolylineRefs.current = [];
        routeOrderOverlayRefs.current.forEach((overlay) => overlay.setMap(null));
        routeOrderOverlayRefs.current = [];

        const activeDayPlaces = coursePlaces.filter((place) => normalizeDayNo(place.dayNo) === activeDayNo);

        activeDayPlaces.forEach((place, index) => {
            const latitude = Number(place.latitude);
            const longitude = Number(place.longitude);
            if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) return;

            const orderOverlay = new kakao.maps.CustomOverlay({
                map,
                position: new kakao.maps.LatLng(latitude, longitude),
                content: `<div class="course-builder-route-marker" aria-label="코스 ${index + 1}번">${index + 1}</div>`,
                xAnchor: 0.5,
                yAnchor: 0.5,
                zIndex: 9,
            });

            routeOrderOverlayRefs.current.push(orderOverlay);

            const nextPlace = activeDayPlaces[index + 1];
            if (!nextPlace) return;

            const routePoints = normalizeRoutePoints(place.routePoints);
            const nextLatitude = Number(nextPlace.latitude);
            const nextLongitude = Number(nextPlace.longitude);
            const fallbackPoints = Number.isFinite(nextLatitude) && Number.isFinite(nextLongitude)
                ? [
                    { latitude, longitude },
                    { latitude: nextLatitude, longitude: nextLongitude },
                ]
                : [];
            const pathPoints = routePoints.length >= 2 ? routePoints : fallbackPoints;
            if (pathPoints.length < 2) return;

            const path = pathPoints.map((point) => new kakao.maps.LatLng(point.latitude, point.longitude));
            const polyline = new kakao.maps.Polyline({
                map,
                path,
                strokeWeight: 4,
                strokeColor: "#0875E1",
                strokeOpacity: 0.9,
                strokeStyle: "solid",
            });

            routePolylineRefs.current.push(polyline);
        });

        return () => {
            routePolylineRefs.current.forEach((polyline) => polyline.setMap(null));
            routePolylineRefs.current = [];
            routeOrderOverlayRefs.current.forEach((overlay) => overlay.setMap(null));
            routeOrderOverlayRefs.current = [];
        };
    }, [activeDayNo, coursePlaces, mapReady]);

    useEffect(() => {
        if (!mapReady || !mapRef.current) return undefined;

        const kakao = window.kakao;
        const map = mapRef.current;

        markerRefs.current.forEach((marker) => marker.setMap(null));
        markerRefs.current = [];
        infoWindowRefs.current.forEach((infoWindow) => infoWindow.close());
        infoWindowRefs.current = [];
        markerInfoWindowMapRef.current.clear();

        if (mapPlaces.length === 0) return undefined;

        mapPlaces.forEach((place) => {
            if (!place.latitude || !place.longitude) return;

            const position = new kakao.maps.LatLng(place.latitude, place.longitude);
            const marker = new kakao.maps.Marker({
                map,
                position,
                image: createMarkerImage(place),
            });

            const infoWindow = new kakao.maps.InfoWindow({
                content: `
                    <div style="padding:10px; font-size:13px; min-width:220px; line-height:1.5;">
                        <strong>${escapeHtml(place.name)}</strong><br/>
                        ${escapeHtml(getPlaceDisplayLabel(place))} / ${escapeHtml(place.region)}<br/>
                        ${escapeHtml(place.address)}<br/>
                        <span style="color:#2563eb;">${escapeHtml(getIndoorOutdoorLabel(place))}</span><br/>
                        ${place.rating ? `평점 ${escapeHtml(place.rating)} / ` : ""}
                        ${place.reviewCount ? `리뷰 ${escapeHtml(place.reviewCount)}개<br/>` : ""}
                        ${place.phone ? `전화번호 ${escapeHtml(place.phone)}<br/>` : ""}
                        ${place.placeUrl ? `<a href="${escapeHtml(place.placeUrl)}" target="_blank" rel="noreferrer" style="color:#0875e1;font-weight:700;text-decoration:none;">상세보기</a>` : ""}
                    </div>
                `,
            });

            const placeKey = getPlaceKey(place);
            markerInfoWindowMapRef.current.set(placeKey, { marker, infoWindow });

            kakao.maps.event.addListener(marker, "click", () => {
                openPlaceInfoWindow(place, { addToSelectedMapPlaces: true });
            });

            markerRefs.current.push(marker);
            infoWindowRefs.current.push(infoWindow);
        });

        if (pendingInfoWindowRequestRef.current) {
            const { place: pendingPlace, addToSelectedMapPlaces } = pendingInfoWindowRequestRef.current;
            const opened = openPlaceInfoWindow(pendingPlace, { addToSelectedMapPlaces });

            if (opened) {
                pendingInfoWindowRequestRef.current = null;
            }
        }

        return undefined;
    }, [mapPlaces, mapReady, openPlaceInfoWindow]);

    return (
        <div className="course-builder-page">
            <header className="course-builder-title-bar">
                <div>
                    <span>SEOULINK COURSE MAKER</span>
                    <h1>서울 여행 코스 만들기</h1>
                </div>
                <div className="course-builder-total-time">
                    <span>총 예상 시간</span>
                    <strong>{formatDurationMinute(courseTimeSummary.totalMinutes)}</strong>
                </div>
            </header>

            <p className="course-builder-screen-reader-status" role="status">{mapStatus}</p>

            <main className="course-builder-layout">
                <CourseBuilderFilter
                    region={region}
                    activeTheme={activeTheme}
                    activeFoodSubcategory={activeFoodSubcategory}
                    tripDayCount={tripDayCount}
                    isLoadingPlaces={isLoadingPlaces}
                    onRegionChange={handleRegionChange}
                    onThemeClick={handleThemeClick}
                    onFoodSubcategoryClick={handleFoodSubcategoryClick}
                    onTripDayCountChange={handleTripDayCountChange}
                />

                <CourseBuilderMap
                    mapContainerRef={mapContainerRef}
                    placeSearchKeyword={placeSearchKeyword}
                    isLoadingPlaces={isLoadingPlaces}
                    onPlaceSearchKeywordChange={setPlaceSearchKeyword}
                    onPlaceSearchKeyDown={handlePlaceSearchKeyDown}
                    onClearPlaceSearchKeyword={handleClearPlaceSearchKeyword}
                    onPlaceKeywordSearch={handlePlaceKeywordSearch}
                    onReloadMapPlaces={handleReloadMapPlaces}
                    onZoomIn={handleZoomIn}
                    onZoomOut={handleZoomOut}
                >
                    <SelectedMapPlaceList
                        selectedMapPlaces={selectedMapPlaces}
                        onClearSelectedMapPlaces={clearSelectedMapPlaces}
                        onMoveToPlace={handleMoveToPlace}
                        onClickableNameKeyDown={handleClickableNameKeyDown}
                        onAddPlaceToCourse={addPlaceToCourse}
                        onRemoveSelectedMapPlace={removeSelectedMapPlace}
                    />
                </CourseBuilderMap>

                <CoursePlaceList
                    coursePlaces={coursePlaces}
                    activeDayNo={activeDayNo}
                    totalDayCount={totalDayCount}
                    courseTitle={courseTitle}
                    courseDescription={courseDescription}
                    dayTimeSummary={activeDayTimeSummary}
                    isCalculatingRoutes={isCalculatingRoutes}
                    onCourseTitleChange={setCourseTitle}
                    onCourseDescriptionChange={setCourseDescription}
                    onDayChange={setActiveDayNo}
                    onMoveToPlace={handleMoveToPlace}
                    onClickableNameKeyDown={handleClickableNameKeyDown}
                    onUpdateCoursePlaceField={updateCoursePlaceField}
                    onMoveCoursePlace={moveCoursePlace}
                    onRemovePlaceFromCourse={removePlaceFromCourse}
                    onSaveCourse={handleSaveCourse}
                />
            </main>
        </div>
    );
}

export default CourseBuilderPage;

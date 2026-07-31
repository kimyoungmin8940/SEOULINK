import { getPlaceKey } from "./placeUtils";

export const createCourseItemId = () => `course-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
export const getCoursePlaceKey = (place) => place.courseItemId || getPlaceKey(place);

export const normalizeDayNo = (value) => {
    const numberValue = Number(value);
    return Number.isFinite(numberValue) && numberValue >= 1 ? Math.floor(numberValue) : 1;
};

export const normalizeStayMinutes = (value) => {
    if (value === null || value === undefined || value === "") return null;

    const numberValue = Number(value);
    return Number.isFinite(numberValue) && numberValue >= 0 ? Math.floor(numberValue) : null;
};

export const resetRouteInfo = (placeList) =>
    placeList.map((place) => ({
        ...place,
        moveDistanceM: null,
        moveDurationMin: null,
        walkDistanceM: null,
        walkDurationMin: null,
        routeStatusMessage: null,
        routeToPlaceName: null,
        routePoints: [],
    }));

export const enrichPlaceForCourse = (place, dayNo = 1) => ({
    ...place,
    courseItemId: createCourseItemId(),
    dayNo: normalizeDayNo(dayNo),
    visitTime: "",
    stayMinutes: 0,
    moveDistanceM: null,
    moveDurationMin: null,
    walkDistanceM: null,
    walkDurationMin: null,
    routeStatusMessage: null,
    routeToPlaceName: null,
    routePoints: [],
});

export const normalizeRoutePoints = (routePoints) => {
    if (!Array.isArray(routePoints)) return [];

    return routePoints
        .map((point) => ({
            latitude: Number(point.latitude),
            longitude: Number(point.longitude),
        }))
        .filter((point) => Number.isFinite(point.latitude) && Number.isFinite(point.longitude));
};

export const makeRouteCalculationKey = (coursePlaces) => {
    if (!coursePlaces || coursePlaces.length < 2) return "";

    return coursePlaces
        .map((place, index) => {
            const latitude = Number(place.latitude);
            const longitude = Number(place.longitude);
            const safeLatitude = Number.isFinite(latitude) ? latitude.toFixed(6) : "";
            const safeLongitude = Number.isFinite(longitude) ? longitude.toFixed(6) : "";

            return [index + 1, getCoursePlaceKey(place), normalizeDayNo(place.dayNo), safeLatitude, safeLongitude].join(":");
        })
        .join("|");
};

export const calculateCourseTimeSummary = (coursePlaces) => {
    const stayTotalMinutes = coursePlaces.reduce((sum, place) => sum + (normalizeStayMinutes(place.stayMinutes) ?? 0), 0);

    const moveTotalMinutes = coursePlaces.reduce((sum, place, index) => {
        const nextPlace = coursePlaces[index + 1];
        if (!nextPlace || normalizeDayNo(place.dayNo) !== normalizeDayNo(nextPlace.dayNo)) return sum;

        const moveDurationMin = Number(place.moveDurationMin);
        return Number.isFinite(moveDurationMin) ? sum + moveDurationMin : sum;
    }, 0);

    const missingMoveCount = coursePlaces.reduce((count, place, index) => {
        const nextPlace = coursePlaces[index + 1];
        if (!nextPlace || normalizeDayNo(place.dayNo) !== normalizeDayNo(nextPlace.dayNo)) return count;

        return Number.isFinite(Number(place.moveDurationMin)) ? count : count + 1;
    }, 0);

    return {
        stayTotalMinutes,
        moveTotalMinutes,
        totalMinutes: stayTotalMinutes + moveTotalMinutes,
        missingMoveCount,
    };
};

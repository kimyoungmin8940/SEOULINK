import { useCallback, useEffect, useRef, useState } from "react";
import { AUTO_ROUTE_CALC_DELAY_MS } from "./courseBuilderConstants";
import { calculateCourseBuilderRoutes } from "./api/courseBuilderApi";
import {
    getCoursePlaceKey,
    makeRouteCalculationKey,
    normalizeDayNo,
    normalizeRoutePoints,
} from "./coursePlaceUtils";

export const useCourseRoutes = ({ coursePlaces, setCoursePlaces, setMapStatus }) => {
    const autoRouteTimerRef = useRef(null);
    const lastRouteCalculationKeyRef = useRef("");
    const routeCalculationRequestIdRef = useRef(0);
    const [isCalculatingRoutes, setIsCalculatingRoutes] = useState(false);

    const calculateRoutesForPlaces = useCallback(async (targetPlaces) => {
        if (targetPlaces.length < 2) return targetPlaces;

        const routeRequestBody = {
            mode: "DRIVING",
            places: targetPlaces.map((place, index) => ({
                clientPlaceId: getCoursePlaceKey(place),
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
            const segment = segmentMap.get(getCoursePlaceKey(place));

            if (!segment) {
                return {
                    ...place,
                    moveDistanceM: null,
                    moveDurationMin: null,
                    walkDistanceM: null,
                    walkDurationMin: null,
                    routeStatusMessage: null,
                    routeToPlaceName: null,
                    routePoints: [],
                };
            }

            return {
                ...place,
                moveDistanceM: segment.distanceMeter ?? null,
                moveDurationMin: segment.durationMinute ?? null,
                walkDistanceM: null,
                walkDurationMin: null,
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

        const routeCalculationKey = makeRouteCalculationKey(coursePlaces);

        if (!routeCalculationKey) {
            lastRouteCalculationKeyRef.current = "";
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
                const updatedPlaces = await calculateRoutesForPlaces(coursePlaces);

                if (requestId === routeCalculationRequestIdRef.current && lastRouteCalculationKeyRef.current === routeCalculationKey) {
                    setCoursePlaces(updatedPlaces);
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
    }, [calculateRoutesForPlaces, coursePlaces, setCoursePlaces, setMapStatus]);

    return {
        isCalculatingRoutes,
        setIsCalculatingRoutes,
        calculateRoutesForPlaces,
    };
};

import {
    useCallback,
    useEffect,
    useMemo,
    useRef,
    useState,
} from 'react';
import {
    ArrowLeft,
    Bookmark,
    Check,
    ChevronLeft,
    ChevronRight,
    GitCompareArrows,
    House,
    Info,
    Lightbulb,
    RefreshCw,
    Sparkles,
    Star,
    X,
} from 'lucide-react';

import Header from '../../components/common/Header';
import Footer from '../../components/common/Footer';
import RecommendationLoadingOverlay from '../../components/common/RecommendationLoadingOverlay';
import RecommendationSteps from '../../components/survey/RecommendationSteps';
import CourseRecommendationCard from '../../components/course/CourseRecommendationCard';
import CourseMapPreview from '../../components/course/CourseMapPreview';
import {
    recommendCourse,
    refreshCourseDraft,
    resolveCourseRouteDetails,
    getMyCourses,
    recordRecommendedCourses,
    saveCourse,
    saveCourses,
} from '../../api/courseApi';
import {
    applyRefreshedCourseDraft,
    buildCourseRecommendAgainRequest,
    COURSE_RECOMMEND_REQUEST_KEY,
    COURSE_RECOMMEND_RESPONSE_KEY,
    deriveRecommendationKey,
} from '../../utils/courseRecommendationHandoff';
import {
    getRouteCorrectionOverlapLimit,
    getTransportMeta,
    normalizeTransitPathType,
    normalizeTransportMode,
    resolveTransportMode,
} from '../../utils/courseTransport';
import { rememberCourseTravelCode } from '../../utils/courseTravelCode';
import { normalizeMyCourseList } from '../../utils/courseHistory';
import recommendationPreview from '../../mocks/courseRecommendation.json';
import heroSeoulImage from '../../assets/images/hero-seoul-main.png';

const COURSE_RECOMMEND_HISTORY_KEY = 'seoulinkCourseRecommendHistory';
const RECOMMENDATION_BASE_TIMEOUT_MS = 45_000;
const RECOMMENDATION_TIMEOUT_PER_EXTRA_DAY_MS = 15_000;
const RECOMMENDATION_MAX_TIMEOUT_MS = 150_000;
const ROUTE_DETAILS_REQUEST_TIMEOUT_MS = 30_000;
// 백엔드는 ODsay 50회/60초 호출 창이 가득 차면 실제 경로를 포기하지 않고
// 대기한 뒤 이어서 조회한다. 고정 90초에서 브라우저가 먼저 요청을 취소하면
// 정상 응답도 화면에 합쳐지지 않으므로 대기 창과 구간 수를 함께 반영한다.
const PUBLIC_TRANSIT_ROUTE_DETAILS_BASE_TIMEOUT_MS = 120_000;
const PUBLIC_TRANSIT_ROUTE_DETAILS_TIMEOUT_PER_LEG_MS = 15_000;
const PUBLIC_TRANSIT_ROUTE_DETAILS_MAX_TIMEOUT_MS = 240_000;
const ROUTE_RESULTS_IDLE_TIMEOUT_MS = 800;
// 개발 모드의 중복 effect나 빠른 재시도로 같은 추천 요청이 겹치지 않도록 Promise를 재사용합니다.
const recommendationPromiseCache = new Map();
const routeDetailsPromiseCache = new Map();
// 이전·다음 결과 전환이나 카드 재마운트에서도 같은 DAY 상세를 다시 요청하지 않습니다.
const routeDetailsResultCache = new Map();

// 대중교통 실제 경로가 40분 제한으로 장소를 줄이려 할 때만 중복 제한을
// 단계적으로 넓힌다. 같은 코스의 다른 DAY 중복과 완전히 같은 구성은 끝까지 금지한다.
const PUBLIC_TRANSIT_ROUTE_OVERLAP_TIERS = Object.freeze([
    Object.freeze({
        key: 'STRICT',
        sameDayOverlapLimit: 1,
        crossDayOverlapLimit: 0,
        alternativeLimit: 8,
        dayCandidateLimit: 24,
        allowPlaceReduction: false,
    }),
    Object.freeze({
        key: 'EXPAND_STRICT',
        sameDayOverlapLimit: 1,
        crossDayOverlapLimit: 0,
        alternativeLimit: 16,
        dayCandidateLimit: 48,
        allowPlaceReduction: false,
    }),
    Object.freeze({
        key: 'RELAX_SAME_DAY',
        sameDayOverlapLimit: 2,
        crossDayOverlapLimit: 0,
        alternativeLimit: 24,
        dayCandidateLimit: 64,
        allowPlaceReduction: false,
    }),
    Object.freeze({
        key: 'RELAX_CROSS_DAY',
        sameDayOverlapLimit: 2,
        crossDayOverlapLimit: 1,
        alternativeLimit: 32,
        dayCandidateLimit: 96,
        allowPlaceReduction: true,
    }),
]);

/** 브라우저 유휴 시간에 작업을 예약하고 필요하면 실행 전 취소할 수 있게 합니다. */
function scheduleBrowserIdleTask(callback, timeoutMs = ROUTE_RESULTS_IDLE_TIMEOUT_MS) {
    if (typeof window === 'undefined') {
        callback();
        return () => {};
    }

    let idleCallbackId = null;
    const delayId = window.setTimeout(() => {
        if (typeof window.requestIdleCallback === 'function') {
            idleCallbackId = window.requestIdleCallback(callback, {
                timeout: timeoutMs,
            });
            return;
        }
        callback();
    }, timeoutMs);

    return () => {
        window.clearTimeout(delayId);
        if (
            idleCallbackId != null
            && typeof window.cancelIdleCallback === 'function'
        ) {
            window.cancelIdleCallback(idleCallbackId);
        }
    };
}

/** 재추천 전·후에 optionNo가 반복되므로 저장 선택 상태는 결과 묶음까지 포함해 구분합니다. */
function getSaveSelectionKey(recommendationKey, optionNo) {
    return `${recommendationKey}:${optionNo}`;
}

/** 서버가 발급한 추천 이력 courseId를 우선 사용해 각 추천 카드를 정확히 식별합니다. */
function getSavedRecommendationIdentity(resultId, recommendationKey, courseId) {
    const normalizedCourseId = Number(courseId);
    if (Number.isInteger(normalizedCourseId) && normalizedCourseId > 0) {
        return `course:${normalizedCourseId}`;
    }

    const normalizedResultId = Number(resultId);
    const normalizedRecommendationKey = String(
        recommendationKey || '',
    ).trim();

    if (
        !Number.isInteger(normalizedResultId)
        || normalizedResultId < 1
        || !normalizedRecommendationKey
    ) {
        return null;
    }

    return `recommendation:${normalizedResultId}:${normalizedRecommendationKey}`;
}

/** 저장된 이전 요청에도 현재 로그인 회원 ID를 보완해 추천 이력이 누락되지 않게 합니다. */
function withCurrentMemberId(request, memberId) {
    if (!request || !memberId || Number(request.memberId) === memberId) {
        return request;
    }

    return {
        ...request,
        memberId,
    };
}

/** 실제 경로가 합쳐진 추천 응답을 서버 이력에 다시 동기화합니다. */
async function syncRecommendationSnapshot(response, memberId) {
    if (!response || !memberId) return;

    try {
        await recordRecommendedCourses(response, { memberId });
    } catch (error) {
        // 추천 화면 자체는 정상적으로 보여주되, 전체 목록 진입 시 한 번 더 복구합니다.
        console.error('추천 코스 이력 동기화 실패:', error);
    }
}

/** 기존 배치 저장 API의 한 번당 최대 3개 제한을 지키면서 최대 6개까지 순서대로 저장합니다. */
async function saveCourseRequests(requests) {
    const savedCourses = [];

    for (let index = 0; index < requests.length; index += 3) {
        const requestBatch = requests.slice(index, index + 3);

        try {
            const batchSavedCourses = requestBatch.length === 1
                ? [await saveCourse(requestBatch[0])]
                : (await saveCourses({ courses: requestBatch }))?.savedCourses;

            if (
                !Array.isArray(batchSavedCourses)
                || batchSavedCourses.length !== requestBatch.length
                || batchSavedCourses.some(
                    (savedCourse) => !Number.isInteger(savedCourse?.courseId),
                )
            ) {
                throw new Error('저장 결과의 코스 ID를 확인할 수 없습니다.');
            }

            savedCourses.push(...batchSavedCourses);
        } catch (error) {
            const saveError = error instanceof Error
                ? error
                : new Error('코스를 저장하지 못했습니다.');
            saveError.savedCourses = [...savedCourses];
            throw saveError;
        }
    }

    return savedCourses;
}

const travelTypeDimensions = [
    {
        options: {
            A: { label: '활동형', tone: 'blue' },
            H: { label: '휴식형', tone: 'blue' },
        },
    },
    {
        options: {
            T: { label: '역사형', tone: 'purple' },
            M: { label: '현대형', tone: 'purple' },
        },
    },
    {
        options: {
            L: { label: '럭셔리형', tone: 'green' },
            B: { label: '가성비형', tone: 'green' },
        },
    },
    {
        options: {
            S: { label: '안정형', tone: 'orange' },
            D: { label: '도전형', tone: 'orange' },
        },
    },
    {
        options: {
            P: { label: '빽빽한 일정형', tone: 'pink' },
            R: { label: '여유 일정형', tone: 'pink' },
        },
    },
];

/** 손상된 브라우저 저장값이 있어도 추천 화면 자체는 계속 열리도록 안전하게 파싱합니다. */
function safelyParse(value) {
    if (!value || typeof value !== 'string') {
        return null;
    }

    try {
        return JSON.parse(value);
    } catch {
        return null;
    }
}

/** 화면 간 전달 키가 통합되기 전의 구버전 키까지 세션→로컬 순서로 탐색합니다. */
function readStoredObject(keys) {
    for (const storage of [sessionStorage, localStorage]) {
        for (const key of keys) {
            const parsed = safelyParse(storage.getItem(key));

            if (parsed) {
                return parsed;
            }
        }
    }

    return null;
}

function isHotelCategory(category) {
    const normalized = String(category || '').trim().toUpperCase();
    return ['HOTEL', '숙소', '호텔', 'ACCOMMODATION', 'LODGING'].includes(normalized);
}

/**
 * 숙소는 해당 DAY의 마지막 도착 지점이므로 별도 체류시간을 잡지 않습니다.
 * 구버전 응답이나 브라우저에 남은 결과도 0분으로 보정해 합계에서 제외합니다.
 */
function normalizeOptionStayTimes(option) {
    if (!Array.isArray(option?.days)) {
        return option;
    }

    const days = option.days.map((day) => {
        const places = (Array.isArray(day?.places) ? day.places : []).map((place) => ({
            ...place,
            expectedVisitMinutes: isHotelCategory(place?.category)
                ? 0
                : Math.max(0, Number(place?.expectedVisitMinutes) || 0),
        }));
        const dailyTravelTimeMinutes = day?.dailyTravelTimeMinutes != null
            && Number.isFinite(Number(day.dailyTravelTimeMinutes))
            ? Number(day.dailyTravelTimeMinutes)
            : sumPlaces(places, 'travelTimeFromPreviousMinutes');
        const dailyVisitTimeMinutes = sumPlaces(places, 'expectedVisitMinutes');

        return {
            ...day,
            places,
            dailyTravelTimeMinutes,
            dailyVisitTimeMinutes,
            dailyCourseTimeMinutes: dailyTravelTimeMinutes + dailyVisitTimeMinutes,
        };
    });
    const totalTravelTimeMinutes = days.reduce(
        (sum, day) => sum + (Number(day.dailyTravelTimeMinutes) || 0),
        0,
    );
    const totalVisitTimeMinutes = days.reduce(
        (sum, day) => sum + (Number(day.dailyVisitTimeMinutes) || 0),
        0,
    );

    return {
        ...option,
        days,
        totalTravelTimeMinutes,
        totalVisitTimeMinutes,
        totalCourseTimeMinutes: totalTravelTimeMinutes + totalVisitTimeMinutes,
    };
}


/**
 * 최초 추천 뒤 실제 경로 보정에서 장소가 삭제돼도 장소 수 조정 안내가 사라지지 않게
 * 현재 장소 수와 P/R 기본 목표 수를 기준으로 DAY 메타데이터를 다시 계산합니다.
 */
function normalizeDayPlaceCountAdjustment(day, travelCode, baselinePlaces = null) {
    const places = Array.isArray(day?.places) ? day.places : [];
    const comparisonPlaces = Array.isArray(baselinePlaces)
        ? baselinePlaces
        : places;
    const actualPlaceCount = places.filter(
        (place) => !isHotelCategory(place?.category),
    ).length;
    const baselinePlaceCount = comparisonPlaces.filter(
        (place) => !isHotelCategory(place?.category),
    ).length;
    const storedRequestedPlaceCount = Number(day?.requestedPlaceCount);
    const densityCode = String(travelCode || '')
        .trim()
        .toUpperCase()
        .slice(-1);
    const densityTargetPlaceCount = densityCode === 'P'
        ? 6
        : densityCode === 'R'
            ? 4
            : 0;
    const requestedPlaceCount = Math.max(
        Number.isFinite(storedRequestedPlaceCount)
            ? storedRequestedPlaceCount
            : 0,
        baselinePlaceCount,
        densityTargetPlaceCount,
    );
    const placeCountAdjusted = Boolean(day?.placeCountAdjusted)
        || actualPlaceCount < requestedPlaceCount;

    return {
        ...day,
        requestedPlaceCount,
        actualPlaceCount,
        placeCountAdjusted,
        adjustmentReason: placeCountAdjusted
            ? day?.adjustmentReason || 'ROUTE_OR_DUPLICATE_LIMIT'
            : null,
        adjustmentNotice: placeCountAdjusted
            ? day?.adjustmentNotice
                || `이동시간과 중복 제한을 만족하는 장소가 부족해 이 DAY는 ${actualPlaceCount}곳으로 조정했어요.`
            : null,
    };
}

/** 현재의 3개 옵션 응답과 이전 단일 코스 응답을 화면 공통 구조로 맞춥니다. */
function normalizeRecommendationResponse(data) {
    if (Array.isArray(data?.courseOptions)) {
        const normalizedTransportMode = normalizeTransportMode(
            data.transportMode,
        );
        const courseOptions = data.courseOptions.map((option) => {
            const normalizedOption = normalizeOptionStayTimes(option);
            const days = (normalizedOption.days || []).map((day) => {
                const normalizedDay = normalizeDayPlaceCountAdjustment(
                    day,
                    data.travelCode,
                );
                const places = Array.isArray(normalizedDay?.places)
                    ? normalizedDay.places
                    : [];
                const hasEstimatedLeg = places.some((place, index) => (
                    index > 0 && Boolean(place?.routeEstimated)
                ));

                // 실패·추정 상세 결과를 세션에 저장했더라도 다음 화면 진입에서는
                // 실제 ODsay 경로를 다시 시도할 수 있도록 완료 표시를 해제합니다.
                if (hasEstimatedLeg || normalizedDay?.routeDetailsError) {
                    return {
                        ...normalizedDay,
                        routeDetailsAttempted: false,
                    };
                }
                return normalizedDay;
            });
            const optionWithNormalizedDays = {
                ...normalizedOption,
                days,
                estimatedTravelTimes: option?.estimatedTravelTimes == null
                    ? Boolean(data.estimatedTravelTimes)
                    : Boolean(option.estimatedTravelTimes),
            };

            return {
                ...optionWithNormalizedDays,
                recommendationKey: deriveRecommendationKey(
                    optionWithNormalizedDays,
                    normalizedTransportMode,
                ),
            };
        });

        return {
            ...data,
            transportMode: normalizedTransportMode,
            estimatedTravelTimes: Boolean(
                data.estimatedTravelTimes
                || courseOptions.some((option) => option.estimatedTravelTimes),
            ),
            courseOptions,
        };
    }

    // 이전 백엔드 응답(단일 저장 코스)도 화면에서 한 개 옵션으로 볼 수 있게 유지합니다.
    if (Array.isArray(data?.days)) {
        const normalizedTransportMode = normalizeTransportMode(
            data.transportMode,
        );
        const normalizedOption = normalizeOptionStayTimes({
            optionNo: 1,
            optionType: 'BALANCED',
            optionName: '균형 추천 코스',
            title: data.title,
            description: data.description,
            totalDistanceKm: data.totalDistanceKm,
            totalTravelTimeMinutes: data.totalTravelTimeMinutes,
            totalVisitTimeMinutes: data.totalVisitTimeMinutes,
            totalCourseTimeMinutes: data.totalCourseTimeMinutes,
            estimatedTravelTimes: Boolean(data.estimatedTravelTimes),
            days: data.days,
        });
        const optionWithRecommendationKey = {
            ...normalizedOption,
            recommendationKey: deriveRecommendationKey(
                normalizedOption,
                normalizedTransportMode,
            ),
        };

        return {
            resultId: data.resultId ?? null,
            travelCode: data.travelCode ?? null,
            transportMode: normalizedTransportMode,
            estimatedTravelTimes: Boolean(data.estimatedTravelTimes),
            courseOptions: [optionWithRecommendationKey],
        };
    }

    return null;
}

/** 옵션·일차 배열만 있고 실제 장소가 없는 응답은 빈 추천 결과로 처리합니다. */
function hasRecommendationPlaces(data) {
    return Array.isArray(data?.courseOptions) && data.courseOptions.some((option) => (
        Array.isArray(option?.days) && option.days.some((day) => (
            Array.isArray(day?.places) && day.places.length > 0
        ))
    ));
}

/** 여행 일수에 맞춰 추천 요청 제한 시간을 계산합니다. */
function getRecommendationRequestTimeoutMs(request) {
    const dayCount = Math.max(
        1,
        Array.isArray(request?.dailyPlans) ? request.dailyPlans.length : 1,
    );

    return Math.min(
        RECOMMENDATION_MAX_TIMEOUT_MS,
        RECOMMENDATION_BASE_TIMEOUT_MS
        + ((dayCount - 1) * RECOMMENDATION_TIMEOUT_PER_EXTRA_DAY_MS),
    );
}

/** 동일 요청은 진행 중인 Promise를 공유하고 일정 일수에 맞춘 제한 시간까지 기다립니다. */
function requestRecommendationOnce(request) {
    const cacheKey = JSON.stringify(request);

    if (!recommendationPromiseCache.has(cacheKey)) {
        const controller = new AbortController();
        const timeoutMs = getRecommendationRequestTimeoutMs(request);
        let timedOut = false;
        const timeoutId = window.setTimeout(() => {
            timedOut = true;
            controller.abort();
        }, timeoutMs);
        const pendingRequest = recommendCourse(request, {
            signal: controller.signal,
        })
            .catch((error) => {
                if (timedOut || error?.name === 'AbortError') {
                    throw new Error(
                        `추천 계산이 ${Math.round(timeoutMs / 1000)}초를 넘겨 중단됐어요. 잠시 후 다시 시도해 주세요.`,
                    );
                }
                throw error;
            })
            .finally(() => {
                window.clearTimeout(timeoutId);
                recommendationPromiseCache.delete(cacheKey);
            });
        recommendationPromiseCache.set(cacheKey, pendingRequest);
    }

    return recommendationPromiseCache.get(cacheKey);
}

/** 이동수단과 실제 조회할 인접 구간 수에 맞춰 상세 요청 제한시간을 계산합니다. */
function getRouteDetailsRequestTimeoutMs(request) {
    if (normalizeTransportMode(request?.transportMode) !== 'PUBLIC_TRANSIT') {
        return ROUTE_DETAILS_REQUEST_TIMEOUT_MS;
    }

    const placeCount = Array.isArray(request?.placeCandidates)
        ? request.placeCandidates.length
        : 0;
    const routeLegCount = Math.max(1, placeCount - 1);

    return Math.min(
        PUBLIC_TRANSIT_ROUTE_DETAILS_MAX_TIMEOUT_MS,
        PUBLIC_TRANSIT_ROUTE_DETAILS_BASE_TIMEOUT_MS
            + (routeLegCount
                * PUBLIC_TRANSIT_ROUTE_DETAILS_TIMEOUT_PER_LEG_MS),
    );
}

function hasEstimatedRouteDetailsResponse(routeDetails) {
    const resolvedPlaces = Array.isArray(routeDetails?.optimizedPlaces)
        ? routeDetails.optimizedPlaces
        : [];

    return Boolean(routeDetails?.estimatedTravelTimes)
        || resolvedPlaces.some((place, index) => (
            index > 0 && Boolean(place?.routeEstimated)
        ));
}

/** 실제 대중교통 응답도 30분 우선·40분 절대 상한을 지키는지 마지막으로 검증합니다. */
function hasPublicTransitRouteLimitViolation(routeDetails) {
    const resolvedPlaces = Array.isArray(routeDetails?.optimizedPlaces)
        ? routeDetails.optimizedPlaces
        : [];
    let longLegCount = 0;

    for (let index = 1; index < resolvedPlaces.length; index += 1) {
        const place = resolvedPlaces[index];
        if (Boolean(place?.routeEstimated)) continue;

        const travelMinutes = Number(place?.travelTimeFromPreviousMinutes);
        if (!Number.isFinite(travelMinutes)) continue;
        if (travelMinutes > 40) return true;
        if (travelMinutes > 30) {
            longLegCount += 1;
            if (longLegCount > 1) return true;
        }
    }

    return false;
}

/** 동일 코스·DAY 상세 요청과 완료 결과를 공유하고 이동수단별 제한 시간까지 기다립니다. */
function requestRouteDetailsOnce(cacheKey, request) {
    if (routeDetailsResultCache.has(cacheKey)) {
        const cachedResult = routeDetailsResultCache.get(cacheKey);
        if (!hasEstimatedRouteDetailsResponse(cachedResult)) {
            return Promise.resolve(cachedResult);
        }
        routeDetailsResultCache.delete(cacheKey);
    }

    if (!routeDetailsPromiseCache.has(cacheKey)) {
        const controller = new AbortController();
        const timeoutMs = getRouteDetailsRequestTimeoutMs(request);
        let timedOut = false;
        const timeoutId = window.setTimeout(() => {
            timedOut = true;
            controller.abort();
        }, timeoutMs);
        const pendingRequest = resolveCourseRouteDetails(request, {
            signal: controller.signal,
        })
            .catch((error) => {
                if (timedOut || error?.name === 'AbortError') {
                    throw new Error(
                        `교통편 확인이 ${Math.round(timeoutMs / 1000)}초를 넘겨 중단됐어요.`,
                    );
                }
                throw error;
            })
            .then((routeDetails) => {
                // 예상값 응답을 캐시하면 ODsay가 복구된 뒤에도 같은 DAY가
                // 영구적으로 예상값만 재사용되므로 실제 경로 응답만 보관합니다.
                if (hasEstimatedRouteDetailsResponse(routeDetails)) {
                    routeDetailsResultCache.delete(cacheKey);
                } else {
                    routeDetailsResultCache.set(cacheKey, routeDetails);
                }
                return routeDetails;
            })
            .finally(() => {
                window.clearTimeout(timeoutId);
                routeDetailsPromiseCache.delete(cacheKey);
            });
        routeDetailsPromiseCache.set(cacheKey, pendingRequest);
    }

    return routeDetailsPromiseCache.get(cacheKey);
}

/** 전달된 응답, 저장 응답, 새 요청, UI 미리보기 순서로 최초 화면 상태를 결정합니다. */
function getInitialRecommendationState() {
    const navigationResponse = normalizeRecommendationResponse(
        window.history.state?.courseRecommendResponse,
    );
    const storedResponse = normalizeRecommendationResponse(
        readStoredObject([
            COURSE_RECOMMEND_RESPONSE_KEY,
            'courseRecommendResponse',
            'courseRecommendationResponse',
        ]),
    );
    const navigationRequest = window.history.state?.courseRecommendRequest || null;
    const storedRequest = readStoredObject([
        COURSE_RECOMMEND_REQUEST_KEY,
        'courseRecommendRequest',
        'courseRecommendationRequest',
    ]);
    const request = navigationRequest || storedRequest;

    if (navigationResponse) {
        return {
            response: navigationResponse,
            request,
            source: 'api',
            status: hasRecommendationPlaces(navigationResponse) ? 'success' : 'empty',
        };
    }

    // 앞 단계가 새 요청을 직접 전달했다면 이전 세션 결과보다 새 요청을 우선합니다.
    if (navigationRequest) {
        return { response: null, request: navigationRequest, source: 'api', status: 'loading' };
    }

    const responseMatchesRequest = !storedRequest
        || !storedResponse?.resultId
        || !storedRequest?.resultId
        || storedResponse.resultId === storedRequest.resultId;

    if (storedResponse && responseMatchesRequest) {
        return {
            response: storedResponse,
            request: storedRequest,
            source: 'api',
            status: hasRecommendationPlaces(storedResponse) ? 'success' : 'empty',
        };
    }

    if (request) {
        return { response: null, request, source: 'api', status: 'loading' };
    }

    return {
        response: recommendationPreview,
        request: null,
        source: 'preview',
        status: 'success',
    };
}

/** 재추천 전·후 두 결과를 세션에 보관해 새로고침해도 추가 재추천을 막습니다. */
function getInitialRecommendationPageState() {
    const baseState = getInitialRecommendationState();
    const storedHistory = safelyParse(sessionStorage.getItem(COURSE_RECOMMEND_HISTORY_KEY));
    const currentResultId = baseState.response?.resultId ?? baseState.request?.resultId ?? null;

    if (!storedHistory || storedHistory.resultId !== currentResultId) {
        return { ...baseState, history: null };
    }

    const previousResponse = normalizeRecommendationResponse(storedHistory.previous?.response);
    const nextResponse = normalizeRecommendationResponse(storedHistory.next?.response);
    if (!previousResponse || !nextResponse) {
        sessionStorage.removeItem(COURSE_RECOMMEND_HISTORY_KEY);
        return { ...baseState, history: null };
    }

    const previous = {
        response: previousResponse,
        request: storedHistory.previous?.request || null,
        source: storedHistory.previous?.source || 'api',
    };
    const next = {
        response: nextResponse,
        request: storedHistory.next?.request || null,
        source: storedHistory.next?.source || 'api',
    };
    const showingNext = Boolean(storedHistory.showingNext);
    const current = showingNext ? next : previous;

    return {
        ...baseState,
        response: current.response,
        request: current.request,
        source: current.source,
        status: hasRecommendationPlaces(current.response) ? 'success' : 'empty',
        history: { previous, next, showingNext },
    };
}

function persistRecommendationHistory(previous, next, showingNext) {
    if (!previous?.response || !next?.response) return;

    sessionStorage.setItem(COURSE_RECOMMEND_HISTORY_KEY, JSON.stringify({
        resultId: previous.response.resultId ?? next.response.resultId ?? null,
        previous,
        next,
        showingNext,
    }));
}

/** 로그인 모듈 통합 전 memberId 확인용으로 JWT payload만 안전하게 읽습니다. */
function decodeTokenPayload(token) {
    try {
        const payload = token?.split('.')[1];
        if (!payload) return null;

        const normalized = payload.replace(/-/g, '+').replace(/_/g, '/');
        const decoded = decodeURIComponent(
            atob(normalized)
                .split('')
                .map((character) => `%${character.charCodeAt(0).toString(16).padStart(2, '0')}`)
                .join(''),
        );
        return JSON.parse(decoded);
    } catch {
        return null;
    }
}

/** 여러 로그인 저장 형식을 임시 지원해 저장 요청에 필요한 회원 정보를 찾습니다. */
function getUserProfile() {
    // 회원 API가 준비되기 전까지 로그인 담당 화면이 저장한 브라우저 값만 사용합니다.
    const storedUser = readStoredObject(['user', 'member', 'loginUser']) || {};
    const tokenPayload = decodeTokenPayload(localStorage.getItem('accessToken')) || {};
    const directName = localStorage.getItem('nickname')
        || localStorage.getItem('userName')
        || localStorage.getItem('memberName')
        || localStorage.getItem('name');
    const memberId = Number(
        storedUser.memberId
        ?? storedUser.id
        ?? tokenPayload.memberId
        ?? tokenPayload.userId
        ?? localStorage.getItem('memberId'),
    );

    return {
        name: directName
            || storedUser.nickname
            || storedUser.userName
            || storedUser.memberName
            || storedUser.name
            || '회원',
        memberId: Number.isInteger(memberId) && memberId > 0 ? memberId : null,
    };
}

/** 추천 응답을 우선하고 설문 저장값을 보조로 사용해 5자리 여행 유형을 결정합니다. */
function getStoredTravelCode(response) {
    const surveyResult = readStoredObject([
        'seoulinkSurveyResult',
        'surveyResult',
        'travelTypeResult',
    ]) || {};
    const code = response?.travelCode
        || surveyResult.travelCode
        || surveyResult.typeCode
        || localStorage.getItem('travelCode')
        || sessionStorage.getItem('travelCode')
        || recommendationPreview.travelCode;

    return /^[A-Z]{5}$/.test(code || '') ? code : 'ATBSP';
}

/** 5자리 여행 유형 코드를 화면에 표시할 성향 배지 다섯 개로 변환합니다. */
function getTravelTypeBadges(travelCode) {
    return travelTypeDimensions.map(({ options }, index) => {
        const letter = travelCode[index];
        const metadata = options[letter] || { label: '맞춤형', tone: 'blue' };
        return { letter, ...metadata };
    });
}

function formatMinutes(value) {
    const minutes = Math.max(0, Math.round(Number(value) || 0));
    const hours = Math.floor(minutes / 60);
    const rest = minutes % 60;

    if (hours === 0) return `${rest}분`;
    return rest === 0 ? `${hours}시간` : `${hours}시간 ${rest}분`;
}

/** 모든 코스 카드·지도·비교 패널이 같은 DAY를 바라보도록 첫 일차 번호를 구합니다. */
function getFirstDayNo(data) {
    const firstDayNo = data?.courseOptions?.find(
        (option) => Array.isArray(option?.days) && option.days.length > 0,
    )?.days?.[0]?.dayNo;
    const normalizedDayNo = Number(firstDayNo);

    return Number.isFinite(normalizedDayNo) ? normalizedDayNo : 1;
}

/** 결과를 전환할 때 현재 DAY가 있으면 유지하고, 없을 때만 첫 DAY로 보정합니다. */
function getAvailableDayNo(data, preferredDayNo) {
    const days = data?.courseOptions?.find(
        (option) => Array.isArray(option?.days) && option.days.length > 0,
    )?.days || [];
    const preferred = Number(preferredDayNo);

    return days.some((day) => Number(day?.dayNo) === preferred)
        ? preferred
        : getFirstDayNo(data);
}

/** 옵션에서 현재 선택한 일차를 찾고 없으면 첫 일차를 사용합니다. */
function getOptionDay(option, activeDayNo) {
    const days = Array.isArray(option?.days) ? option.days : [];
    return days.find(
        (day) => Number(day?.dayNo) === Number(activeDayNo),
    ) || days[0] || null;
}

/** 지정한 DAY에 실제 경로 조회가 필요한 옵션이 하나라도 있는지 확인합니다. */
function hasUnresolvedRoutesForDay(responseData, requestedDayNo) {
    return (responseData?.courseOptions || []).some((option) => {
        const day = (option?.days || []).find(
            (candidate) => Number(candidate?.dayNo) === Number(requestedDayNo),
        );
        return Boolean(day && !day.routeDetailsAttempted);
    });
}

/**
 * 현재 DAY 다음 일차부터 아직 실제 경로를 받지 않은 DAY를 찾습니다.
 * 마지막 DAY까지 준비됐으면 앞쪽의 미완료 DAY로 돌아가 한 번에 하나씩 처리합니다.
 */
function getNextUnresolvedDayNo(responseData, activeDayNo) {
    const orderedDayNos = [
        ...new Set(
            (responseData?.courseOptions || []).flatMap((option) => (
                (option?.days || [])
                    .map((day) => Number(day?.dayNo))
                    .filter(Number.isFinite)
            )),
        ),
    ].sort((left, right) => left - right);

    if (orderedDayNos.length <= 1) {
        return null;
    }

    const activeIndex = orderedDayNos.findIndex(
        (dayNo) => dayNo === Number(activeDayNo),
    );
    const prioritizedDayNos = activeIndex >= 0
        ? [
            ...orderedDayNos.slice(activeIndex + 1),
            ...orderedDayNos.slice(0, activeIndex),
        ]
        : orderedDayNos;

    return prioritizedDayNos.find(
        (dayNo) => hasUnresolvedRoutesForDay(responseData, dayNo),
    ) ?? null;
}

function getPreviousDayHotelOrigin(option, day) {
    const days = Array.isArray(option?.days) ? option.days : [];
    const dayIndex = days.findIndex(
        (candidate) => Number(candidate?.dayNo) === Number(day?.dayNo),
    );

    if (dayIndex <= 0) {
        return null;
    }

    const previousPlaces = Array.isArray(days[dayIndex - 1]?.places)
        ? days[dayIndex - 1].places
        : [];

    return [...previousPlaces].reverse().find(
        (place) => isHotelCategory(place?.category),
    ) || null;
}

/** 현재 카드들에 실제로 표시 중인 DAY의 추정 경로 개수만 셉니다. */
function countEstimatedLegs(courseOptions, activeDayNo) {
    return (Array.isArray(courseOptions) ? courseOptions : []).reduce(
        (total, option) => {
            const day = getOptionDay(option, activeDayNo);
            return total + (day?.places || []).filter(
                (place, index) => (
                    (index > 0 || Boolean(day?.routeOriginPlace))
                    && Boolean(place.routeEstimated)
                ),
            ).length;
        },
        0,
    );
}

/** 경로 상세 후보를 비교할 때 사용하는 카테고리 키입니다. */
function normalizeRouteCategory(category) {
    return String(category || '').trim().toUpperCase();
}

/** 같은 DAY의 원본 후보 풀을 추천 요청에서 다시 찾습니다. */
function getRouteDailyPlanCandidates(recommendRequest, day) {
    const dailyPlans = Array.isArray(recommendRequest?.dailyPlans)
        ? recommendRequest.dailyPlans
        : [];
    const dailyPlan = dailyPlans.find(
        (candidateDay) => candidateDay?.visitDate === day?.visitDate,
    );

    return Array.isArray(dailyPlan?.placeCandidates)
        ? dailyPlan.placeCandidates
        : [];
}

/** 장소 ID 기준으로 후보를 중복 제거하며 입력 순서를 유지합니다. */
function uniqueRouteCandidates(candidates) {
    const unique = new Map();

    (Array.isArray(candidates) ? candidates : []).forEach((candidate) => {
        const placeId = Number(candidate?.placeId);
        if (Number.isInteger(placeId) && !unique.has(placeId)) {
            unique.set(placeId, candidate);
        }
    });

    return [...unique.values()];
}

/** 후보 교체가 불가능하면 P/R형 모두 일반 장소 3곳까지 단계적으로 줄입니다. */
function getMinimumResolvedOrdinaryPlaceCount(originalPlaces) {
    const originalOrdinaryPlaceCount = (Array.isArray(originalPlaces)
        ? originalPlaces
        : []
    ).filter((place) => !isHotelCategory(place?.category)).length;
    return Math.min(originalOrdinaryPlaceCount, 3);
}

function getRouteOverlapTiers(transportMode) {
    if (normalizeTransportMode(transportMode) === 'PUBLIC_TRANSIT') {
        return PUBLIC_TRANSIT_ROUTE_OVERLAP_TIERS;
    }

    const overlapLimit = getRouteCorrectionOverlapLimit(transportMode);
    return [{
        key: 'DEFAULT',
        sameDayOverlapLimit: overlapLimit,
        crossDayOverlapLimit: overlapLimit,
    }];
}

function isSameRecommendationDay(leftDay, rightDay) {
    if (leftDay?.dayNo != null && rightDay?.dayNo != null) {
        return Number(leftDay.dayNo) === Number(rightDay.dayNo);
    }

    return Boolean(leftDay?.visitDate)
        && leftDay.visitDate === rightDay?.visitDate;
}

function countPlaceIdOverlap(leftIds, rightIds) {
    return [...leftIds].filter((placeId) => rightIds.has(placeId)).length;
}

/**
 * 현재 장소를 후보로 교체했을 때 단계별 중복 상한을 넘는지 미리 확인합니다.
 * 기존 추천 결과가 이미 가진 중복은 그대로 인정하되 새 교체로 더 늘리지는 않습니다.
 */
function canUseRouteAlternative({
    candidate,
    replacedPlace,
    responseData,
    currentOption,
    currentDay,
    overlapTier,
}) {
    const candidateId = Number(candidate?.placeId);
    const replacedPlaceId = Number(replacedPlace?.placeId);
    if (!Number.isInteger(candidateId) || !Number.isInteger(replacedPlaceId)) {
        return false;
    }

    const originalCurrentIds = ordinaryRoutePlaceIds(currentDay?.places);
    const tentativeIds = new Set(originalCurrentIds);
    tentativeIds.delete(replacedPlaceId);
    tentativeIds.add(candidateId);

    for (const option of responseData?.courseOptions || []) {
        for (const day of option?.days || []) {
            if (
                Number(option?.optionNo) === Number(currentOption?.optionNo)
                && Number(day?.dayNo) === Number(currentDay?.dayNo)
            ) {
                continue;
            }

            const otherIds = ordinaryRoutePlaceIds(day?.places);
            if (otherIds.size === 0) continue;

            const overlap = countPlaceIdOverlap(tentativeIds, otherIds);
            const sameOption = Number(option?.optionNo)
                === Number(currentOption?.optionNo);
            if (sameOption && overlap > 0) {
                return false;
            }

            const identicalComposition = tentativeIds.size === otherIds.size
                && overlap === tentativeIds.size;
            if (!sameOption && identicalComposition) {
                return false;
            }

            if (!sameOption) {
                const baselineOverlap = countPlaceIdOverlap(
                    originalCurrentIds,
                    otherIds,
                );
                const configuredLimit = isSameRecommendationDay(
                    currentDay,
                    day,
                )
                    ? overlapTier.sameDayOverlapLimit
                    : overlapTier.crossDayOverlapLimit;
                const allowedOverlap = Math.max(
                    baselineOverlap,
                    configuredLimit,
                );
                if (overlap > allowedOverlap) {
                    return false;
                }
            }
        }
    }

    return true;
}

function canCandidateReplaceAnyRoutePlace(
    candidate,
    responseData,
    currentOption,
    currentDay,
    overlapTier,
) {
    const candidateCategory = normalizeRouteCategory(candidate?.category);
    return (currentDay?.places || []).some((place) => (
        !isHotelCategory(place?.category)
        && normalizeRouteCategory(place?.category) === candidateCategory
        && canUseRouteAlternative({
            candidate,
            replacedPlace: place,
            responseData,
            currentOption,
            currentDay,
            overlapTier,
        })
    ));
}

/**
 * 실제 ODsay 경로가 제한을 넘을 때 사용할 같은 날짜·카테고리 대체 후보를 만듭니다.
 * 원래 후보에 연결된 alternativeCandidates를 먼저 사용하고, 부족하면 같은 DAY 후보 풀로
 * 보완합니다. 같은 옵션의 다른 DAY 중복은 끝까지 금지하고, 다른 옵션과의 중복은
 * 현재 완화 단계의 상한 안에서만 허용합니다.
 */
function buildRouteAlternatives(
    place,
    dailyCandidates,
    currentRoutePlaceIds,
    responseData,
    currentOption,
    currentDay,
    overlapTier,
) {
    const placeId = Number(place?.placeId);
    const category = normalizeRouteCategory(place?.category);
    if (!Number.isInteger(placeId) || !category || isHotelCategory(category)) {
        return [];
    }

    const sourceCandidate = dailyCandidates.find(
        (candidate) => Number(candidate?.placeId) === placeId,
    );
    const explicitAlternatives = Array.isArray(
        sourceCandidate?.alternativeCandidates,
    )
        ? sourceCandidate.alternativeCandidates
        : [];
    const sameCategoryCandidates = dailyCandidates.filter(
        (candidate) => (
            normalizeRouteCategory(candidate?.category) === category
            && Number(candidate?.placeId) !== placeId
        ),
    );
    const byRecommendationScore = (left, right) => (
        (Number(right?.recommendationScore) || 0)
        - (Number(left?.recommendationScore) || 0)
    );

    return uniqueRouteCandidates([
        ...explicitAlternatives,
        ...sameCategoryCandidates,
    ])
        .filter((candidate) => (
            !currentRoutePlaceIds.has(Number(candidate?.placeId))
            && canUseRouteAlternative({
                candidate,
                replacedPlace: place,
                responseData,
                currentOption,
                currentDay,
                overlapTier,
            })
        ))
        .sort(byRecommendationScore)
        .slice(0, overlapTier?.alternativeLimit ?? 8);
}

/** 추천 응답의 장소 한 건을 고정 순서 경로 조회 입력으로 변환합니다. */
function toRouteDetailsCandidate(place, visitDate, alternatives = []) {
    return {
        placeId: Number(place.placeId),
        placeName: place.placeName,
        category: place.category,
        region: place.region ?? null,
        address: place.address ?? null,
        roadAddress: place.roadAddress ?? null,
        imageUrl: place.imageUrl ?? null,
        recommendationScore: Number(place.recommendationScore) || 0,
        latitude: Number(place.latitude),
        longitude: Number(place.longitude),
        visitDate: place.visitDate || visitDate,
        themePalaceCultureYn: place.themePalaceCultureYn || 'N',
        themeNatureHangangYn: place.themeNatureHangangYn || 'N',
        themeDateYn: place.themeDateYn || 'N',
        themeFoodTourYn: place.themeFoodTourYn || 'N',
        themeCafeTourYn: place.themeCafeTourYn || 'N',
        themeShoppingHotplaceYn: place.themeShoppingHotplaceYn || 'N',
        themeNightViewYn: place.themeNightViewYn || 'N',
        themeHotelStayYn: place.themeHotelStayYn || 'N',
        alternativeCandidates: uniqueRouteCandidates(alternatives).map(
            (alternative) => toRouteDetailsCandidate(
                alternative,
                visitDate,
                [],
            ),
        ),
    };
}

/** 카드에 표시할 옵션의 한 DAY만 백엔드 고정 순서 경로 조회 요청으로 만듭니다. */
function buildRouteDetailsRequest(
    option,
    day,
    response,
    recommendRequest,
    transportMode,
    overlapTier,
) {
    const places = Array.isArray(day?.places) ? day.places : [];

    if (!option || !day || places.length === 0) {
        throw new Error('교통편을 확인할 코스 정보가 없습니다.');
    }

    const previousHotel = getPreviousDayHotelOrigin(option, day);
    const routePlaces = previousHotel
        ? [
            {
                ...previousHotel,
                visitDate: day.visitDate,
            },
            ...places,
        ]
        : places;
    const dailyCandidates = getRouteDailyPlanCandidates(
        recommendRequest,
        day,
    );
    const currentRoutePlaceIds = new Set(
        routePlaces.map((place) => Number(place?.placeId)),
    );
    const byRecommendationScore = (left, right) => (
        (Number(right?.recommendationScore) || 0)
        - (Number(left?.recommendationScore) || 0)
    );
    const remainingDayCandidates = uniqueRouteCandidates(dailyCandidates)
        .filter((candidate) => (
            !currentRoutePlaceIds.has(Number(candidate?.placeId))
            && canCandidateReplaceAnyRoutePlace(
                candidate,
                response,
                option,
                day,
                overlapTier,
            )
        ));
    const eligibleDayCandidates = [...remainingDayCandidates]
        .sort(byRecommendationScore)
        .slice(0, overlapTier?.dayCandidateLimit ?? 24);

    return {
        resultId: response?.resultId ?? null,
        travelCode: response?.travelCode ?? null,
        transportMode,
        enforcePublicTransitLimit: true,
        allowPublicTransitPlaceReduction:
            Boolean(overlapTier?.allowPlaceReduction),
        dailyStartTime: response?.dailyStartTime
            ?? recommendRequest?.dailyStartTime
            ?? null,
        placeCandidates: routePlaces.map((place, index) => {
            const isHotelOrigin = Boolean(previousHotel) && index === 0;
            const alternatives = isHotelOrigin
                ? []
                : buildRouteAlternatives(
                    place,
                    dailyCandidates,
                    currentRoutePlaceIds,
                    response,
                    option,
                    day,
                    overlapTier,
                );

            return toRouteDetailsCandidate(
                place,
                day.visitDate,
                alternatives,
            );
        }),
        // 각 교체 대상과 같은 카테고리이면서 현재 단계의 중복 상한을 지킬 수 있는
        // 후보만 최상위 호환 필드에도 전달합니다.
        alternativeCandidates: eligibleDayCandidates.map(
            (candidate) => toRouteDetailsCandidate(
                candidate,
                day.visitDate,
                [],
            ),
        ),
    };
}

/**
 * 모든 교체 후보를 검토해도 대중교통 제한을 만족하지 못했을 때,
 * 현재 화면의 장소와 순서를 그대로 유지하고 원래 인접 구간만 실제 경로로 조회합니다.
 */
function buildOriginalPublicTransitRouteRequest(
    option,
    day,
    response,
    recommendRequest,
    transportMode,
) {
    const strictTier = PUBLIC_TRANSIT_ROUTE_OVERLAP_TIERS[0];
    const request = buildRouteDetailsRequest(
        option,
        day,
        response,
        recommendRequest,
        transportMode,
        strictTier,
    );

    return {
        ...request,
        enforcePublicTransitLimit: false,
        allowPublicTransitPlaceReduction: false,
        placeCandidates: (request.placeCandidates || []).map((candidate) => ({
            ...candidate,
            alternativeCandidates: [],
        })),
        alternativeCandidates: [],
    };
}

/** 완화 단계만 달라졌지만 실제 후보 구성이 같으면 ODsay를 다시 호출하지 않습니다. */
function getRouteDetailsRequestSignature(request) {
    const summarizeCandidates = (candidates) => (
        Array.isArray(candidates) ? candidates : []
    ).map((candidate) => ({
        placeId: Number(candidate?.placeId) || null,
        alternatives: (candidate?.alternativeCandidates || [])
            .map((alternative) => Number(alternative?.placeId) || null),
    }));

    return JSON.stringify({
        enforcePublicTransitLimit:
            request?.enforcePublicTransitLimit !== false,
        allowPlaceReduction:
            Boolean(request?.allowPublicTransitPlaceReduction),
        places: summarizeCandidates(request?.placeCandidates),
        alternatives: summarizeCandidates(request?.alternativeCandidates),
    });
}

function sumPlaces(places, fieldName) {
    return (Array.isArray(places) ? places : []).reduce(
        (sum, place) => sum + (Number(place?.[fieldName]) || 0),
        0,
    );
}

function parseTimeToMinutes(value) {
    const match = /^([01]\d|2[0-3]):([0-5]\d)$/.exec(String(value || ''));
    return match ? Number(match[1]) * 60 + Number(match[2]) : null;
}

function formatClockMinutes(value) {
    const minutesInDay = 24 * 60;
    const normalized = ((Math.round(value) % minutesInDay) + minutesInDay)
        % minutesInDay;

    return `${String(Math.floor(normalized / 60)).padStart(2, '0')}:${String(normalized % 60).padStart(2, '0')}`;
}

/**
 * 실제 인접 구간 이동시간을 누적해 카드 아래에 보이는 장소별 도착 시각도 다시 계산합니다.
 * 첫 장소의 기존 시각을 우선해 사용하므로 백엔드 추천 당시의 일정 시작 시각을 유지합니다.
 */
function recalculateVisitTimes(
    places,
    dailyStartTime,
    startsFromHiddenHotel = false,
) {
    if (!Array.isArray(places) || places.length === 0) {
        return [];
    }

    const firstVisitTime = places[0]?.expectedVisitTimeHHmm
        || places[0]?.visitTime
        || dailyStartTime
        || '10:00';
    let currentMinutes = parseTimeToMinutes(firstVisitTime)
        ?? parseTimeToMinutes(dailyStartTime)
        ?? 10 * 60;

    return places.map((place, index) => {
        if (index > 0 || (index === 0 && startsFromHiddenHotel)) {
            currentMinutes += Math.round(
                Number(place.travelTimeFromPreviousMinutes) || 0,
            );
        }

        const visitTime = formatClockMinutes(currentMinutes);
        currentMinutes += Math.max(
            0,
            Math.round(Number(place.expectedVisitMinutes) || 0),
        );

        return {
            ...place,
            visitTime,
            expectedVisitTimeHHmm: visitTime,
        };
    });
}

/** 실제 경로 응답에서 DAY2 이후 숨겨진 출발 숙소를 제외한 표시 장소를 분리합니다. */
function getVisibleResolvedRoutePlaces(routeDetails, routeOriginPlace = null) {
    const resolvedPlaces = Array.isArray(routeDetails?.optimizedPlaces)
        ? routeDetails.optimizedPlaces
        : [];
    const routeOriginPlaceId = Number(routeOriginPlace?.placeId);
    const hasPlannedHotelOrigin = Number.isFinite(routeOriginPlaceId);
    const responseIncludesHotelOrigin = hasPlannedHotelOrigin
        && Number(resolvedPlaces[0]?.placeId) === routeOriginPlaceId;

    return {
        resolvedPlaces,
        hasPlannedHotelOrigin,
        responseIncludesHotelOrigin,
        visibleResolvedPlaces: responseIncludesHotelOrigin
            ? resolvedPlaces.slice(1)
            : resolvedPlaces,
    };
}

function ordinaryRoutePlaceIds(places) {
    return new Set(
        (Array.isArray(places) ? places : [])
            .filter((place) => !isHotelCategory(place?.category))
            .map((place) => Number(place?.placeId))
            .filter(Number.isInteger),
    );
}

/**
 * 실제 경로 보정이 추천 생성 단계의 중복 제한을 다시 깨지 않는지 확인합니다.
 * 같은 옵션의 다른 DAY 중복과 완전히 동일한 장소 구성은 끝까지 금지합니다.
 * 대중교통은 장소가 부족할 때만 같은 DAY 2곳, 다른 DAY 1곳 순으로 완화합니다.
 */
function getRouteDetailOverlapViolation(
    responseData,
    currentOption,
    currentDay,
    visibleResolvedPlaces,
    transportMode,
    overlapTier,
    originalPlaces,
) {
    const currentIds = ordinaryRoutePlaceIds(visibleResolvedPlaces);
    if (currentIds.size === 0) return '';
    const originalCurrentIds = ordinaryRoutePlaceIds(originalPlaces);
    const fallbackOverlapLimit = getRouteCorrectionOverlapLimit(transportMode);

    for (const option of responseData?.courseOptions || []) {
        for (const day of option?.days || []) {
            if (
                Number(option?.optionNo) === Number(currentOption?.optionNo)
                && Number(day?.dayNo) === Number(currentDay?.dayNo)
            ) {
                continue;
            }

            const otherIds = ordinaryRoutePlaceIds(day?.places);
            if (otherIds.size === 0) continue;
            const overlap = countPlaceIdOverlap(currentIds, otherIds);
            const sameOption = Number(option?.optionNo)
                === Number(currentOption?.optionNo);

            if (sameOption && overlap > 0) {
                return `같은 코스의 다른 DAY와 장소 ${overlap}곳이 겹쳐 장소 교체 결과를 적용하지 않았습니다.`;
            }

            const identicalComposition = currentIds.size === otherIds.size
                && overlap === currentIds.size;
            if (!sameOption && identicalComposition) {
                return '다른 추천 코스의 DAY와 장소 구성이 완전히 같아 장소 교체 결과를 적용하지 않았습니다.';
            }

            if (!sameOption) {
                const baselineOverlap = countPlaceIdOverlap(
                    originalCurrentIds,
                    otherIds,
                );
                const configuredLimit = overlapTier
                    ? (isSameRecommendationDay(currentDay, day)
                        ? overlapTier.sameDayOverlapLimit
                        : overlapTier.crossDayOverlapLimit)
                    : fallbackOverlapLimit;
                const allowedOverlap = Math.max(
                    baselineOverlap,
                    configuredLimit,
                );
                if (overlap > allowedOverlap) {
                    return `다른 추천 코스의 DAY와 장소 ${overlap}곳이 겹쳐 장소 교체 결과를 적용하지 않았습니다.`;
                }
            }
        }
    }

    return '';
}

function routeLegKey(fromPlace, toPlace) {
    const fromPlaceId = Number(fromPlace?.placeId);
    const toPlaceId = Number(toPlace?.placeId);

    return Number.isInteger(fromPlaceId) && Number.isInteger(toPlaceId)
        ? `${fromPlaceId}>${toPlaceId}`
        : null;
}

/**
 * 중복을 만든 장소 교체 결과는 적용하지 않되, 원래 코스와 출발지·도착지가
 * 정확히 같은 구간에서 이미 받은 실제 ODsay 값은 보존합니다.
 * 장소가 다른 구간의 시간은 원래 장소에 잘못 붙이지 않고 기존 예상값을 유지합니다.
 */
function preserveActualLegsForOriginalPlaces(
    routeDetails,
    originalPlaces,
    routeOriginPlace = null,
) {
    const resolvedPlaces = Array.isArray(routeDetails?.optimizedPlaces)
        ? routeDetails.optimizedPlaces
        : [];
    const actualLegByKey = new Map();

    for (let index = 1; index < resolvedPlaces.length; index += 1) {
        const resolvedDestination = resolvedPlaces[index];
        const key = routeLegKey(
            resolvedPlaces[index - 1],
            resolvedDestination,
        );

        if (
            key
            && !Boolean(resolvedDestination?.routeEstimated)
            && !actualLegByKey.has(key)
        ) {
            actualLegByKey.set(key, resolvedDestination);
        }
    }

    const originalRoutePlaces = routeOriginPlace
        ? [
            {
                ...routeOriginPlace,
                routeOrigin: true,
                expectedVisitMinutes: 0,
                distanceFromPreviousKm: 0,
                travelTimeFromPreviousMinutes: 0,
                transitPathType: null,
                routeEstimated: false,
            },
            ...(Array.isArray(originalPlaces) ? originalPlaces : []),
        ]
        : (Array.isArray(originalPlaces) ? originalPlaces : []);
    let preservedActualLegCount = 0;
    const optimizedPlaces = originalRoutePlaces.map((place, index) => {
        if (index === 0) {
            return {
                ...place,
                visitOrder: 1,
                distanceFromPreviousKm: 0,
                travelTimeFromPreviousMinutes: 0,
                transitPathType: null,
                routeEstimated: false,
            };
        }

        const actualLeg = actualLegByKey.get(
            routeLegKey(originalRoutePlaces[index - 1], place),
        );
        if (!actualLeg) {
            return {
                ...place,
                visitOrder: index + 1,
                routeEstimated: place?.routeEstimated == null
                    ? true
                    : Boolean(place.routeEstimated),
            };
        }

        preservedActualLegCount += 1;
        return {
            ...place,
            visitOrder: index + 1,
            distanceFromPreviousKm:
                Number(actualLeg.distanceFromPreviousKm) || 0,
            travelTimeFromPreviousMinutes:
                Number(actualLeg.travelTimeFromPreviousMinutes) || 0,
            transitPathType: normalizeTransitPathType(
                actualLeg.transitPathType,
            ),
            routeEstimated: false,
        };
    });
    const estimatedTravelTimes = optimizedPlaces.some(
        (place, index) => index > 0 && Boolean(place?.routeEstimated),
    );

    return {
        routeDetails: {
            ...routeDetails,
            optimizedPlaces,
            estimatedTravelTimes,
        },
        preservedActualLegCount,
        estimatedTravelTimes,
    };
}

/** 실제 경로 응답을 해당 옵션·DAY에만 합치고 시각·합계·예상 여부를 다시 계산합니다. */
function mergeRouteDetails(
    currentResponse,
    optionNo,
    dayNo,
    routeDetails,
    dailyStartTime,
    routeOriginPlace = null,
    errorMessage = '',
) {
    if (!currentResponse) return currentResponse;

    const {
        visibleResolvedPlaces,
        hasPlannedHotelOrigin,
        responseIncludesHotelOrigin,
    } = getVisibleResolvedRoutePlaces(routeDetails, routeOriginPlace);
    const courseOptions = (currentResponse.courseOptions || []).map((option) => {
        if (Number(option.optionNo) !== Number(optionNo)) return option;

        const days = (option.days || []).map((day) => {
            if (Number(day.dayNo) !== Number(dayNo)) return day;

            const originalPlaces = Array.isArray(day.places)
                ? day.places
                : [];
            const originalByPlaceId = new Map(
                originalPlaces.map((place) => [Number(place.placeId), place]),
            );
            const hasResolvedRoute = Array.isArray(
                routeDetails?.optimizedPlaces,
            );
            const mergedPlaces = hasResolvedRoute
                ? visibleResolvedPlaces.map((resolved, index) => {
                    const original = originalByPlaceId.get(
                        Number(resolved.placeId),
                    ) || originalPlaces[index] || {};

                    return {
                        ...original,
                        ...resolved,
                        transitPathType: normalizeTransitPathType(
                            resolved.transitPathType,
                        ),
                        routeEstimated: Boolean(resolved.routeEstimated),
                    };
                })
                : originalPlaces;
            const places = recalculateVisitTimes(
                mergedPlaces,
                dailyStartTime,
                responseIncludesHotelOrigin,
            );
            const dailyDistanceKm = sumPlaces(
                places,
                'distanceFromPreviousKm',
            );
            const dailyTravelTimeMinutes = sumPlaces(
                places,
                'travelTimeFromPreviousMinutes',
            );
            const dailyVisitTimeMinutes = sumPlaces(
                places,
                'expectedVisitMinutes',
            );
            return normalizeDayPlaceCountAdjustment({
                ...day,
                places,
                dailyDistanceKm,
                dailyTravelTimeMinutes,
                dailyVisitTimeMinutes,
                dailyCourseTimeMinutes:
                    dailyVisitTimeMinutes + dailyTravelTimeMinutes,
                routeOriginPlace: hasPlannedHotelOrigin
                    ? {
                        ...routeOriginPlace,
                        routeOrigin: true,
                        expectedVisitMinutes: 0,
                        expectedVisitTimeHHmm: dailyStartTime || '10:00',
                        visitTime: dailyStartTime || '10:00',
                        travelTimeFromPreviousMinutes: 0,
                        distanceFromPreviousKm: 0,
                    }
                    : null,
                routeDetailsAttempted: true,
                routeDetailsError: errorMessage || null,
            }, currentResponse?.travelCode, originalPlaces);
        });
        const allPlaces = days.flatMap((day) => day.places || []);
        const totalDistanceKm = days.reduce(
            (sum, day) => sum + (Number(day.dailyDistanceKm) || 0),
            0,
        );
        const totalTravelTimeMinutes = days.reduce(
            (sum, day) => sum + (Number(day.dailyTravelTimeMinutes) || 0),
            0,
        );
        const totalVisitTimeMinutes = days.reduce(
            (sum, day) => sum + (Number(day.dailyVisitTimeMinutes) || 0),
            0,
        );

        const updatedOption = {
            ...option,
            days,
            totalDistanceKm,
            totalTravelTimeMinutes,
            totalVisitTimeMinutes,
            totalCourseTimeMinutes:
                totalVisitTimeMinutes + totalTravelTimeMinutes,
            estimatedTravelTimes: allPlaces.some(
                (place) => Boolean(place.routeEstimated),
            ),
        };
        return {
            ...updatedOption,
            // 실제 경로 보정으로 교체·삭제된 장소를 반영하고 숙소는 제외한다.
            recommendationKey: deriveRecommendationKey(
                updatedOption,
                currentResponse.transportMode,
            ),
        };
    });

    return {
        ...currentResponse,
        courseOptions,
        estimatedTravelTimes: courseOptions.some(
            (option) => Boolean(option.estimatedTravelTimes),
        ),
    };
}

function getRouteDetailsKey(
    responseData,
    option,
    day,
    transportMode,
    overlapTierKey = 'DEFAULT',
) {
    const orderedPlaceIds = (day?.places || [])
        .map((place) => Number(place?.placeId) || 'place')
        .join('>');
    const routeOriginPlace = getPreviousDayHotelOrigin(option, day);
    const originKey = routeOriginPlace?.placeId
        ? `hotel-${routeOriginPlace.placeId}`
        : 'daily-start';

    return [
        responseData?.resultId ?? 'result',
        transportMode || 'transport',
        overlapTierKey,
        option?.recommendationKey || option?.optionNo || 'option',
        day?.dayNo || 'day',
        day?.visitDate || 'date',
        originKey,
        orderedPlaceIds || 'route',
    ].join(':');
}


/**
 * 요청한 DAY의 세 옵션 경로를 순차 조회해 응답에 합칩니다.
 * 앞 옵션의 장소 교체 결과를 다음 옵션의 중복 회피에 즉시 반영하고,
 * 완료된 경로는 Promise·결과 캐시에 남겨 같은 DAY를 다시 열어도 재호출하지 않습니다.
 */
async function prepareVisibleRoutesBeforeDisplay(
    responseData,
    recommendRequest,
    requestedDayNo,
    transportMode,
    shouldContinue = null,
) {
    if (!responseData || !transportMode) {
        return {
            response: responseData,
            buildError: '',
            interrupted: false,
        };
    }

    let preparedResponse = responseData;
    let firstBuildError = '';
    let interrupted = false;
    const optionNos = (responseData.courseOptions || [])
        .map((option) => option?.optionNo)
        .filter((optionNo) => optionNo != null);

    // 요청을 미리 전부 만들지 않고 옵션별로 하나씩 생성·병합한다.
    // 앞 옵션에서 실제 경로 보정으로 교체된 장소도 다음 옵션의 예약 장소에 즉시 반영된다.
    for (const optionNo of optionNos) {
        if (
            typeof shouldContinue === 'function'
            && !shouldContinue()
        ) {
            interrupted = true;
            break;
        }

        const option = (preparedResponse.courseOptions || []).find(
            (candidate) => Number(candidate?.optionNo) === Number(optionNo),
        );
        const day = getOptionDay(option, requestedDayNo);
        if (!day || day.routeDetailsAttempted) {
            continue;
        }

        const routeOriginPlace = getPreviousDayHotelOrigin(option, day);
        const expectedPlaceCount = day.places.length
            + (routeOriginPlace ? 1 : 0);
        const minimumOrdinaryPlaceCount =
            getMinimumResolvedOrdinaryPlaceCount(
                day.places,
            );
        const overlapTiers = getRouteOverlapTiers(transportMode);
        let routeApplied = false;
        let lastRouteError = '';
        let lastRejectedRouteDetails = null;
        let constraintFallback = false;
        const attemptedRequestSignatures = new Set();

        for (const overlapTier of overlapTiers) {
            let request;
            try {
                request = buildRouteDetailsRequest(
                    option,
                    day,
                    preparedResponse,
                    recommendRequest,
                    transportMode,
                    overlapTier,
                );
            } catch (error) {
                lastRouteError = error?.message
                    || '교통편 요청을 만들지 못했습니다.';
                firstBuildError ||= lastRouteError;
                break;
            }

            const requestSignature = getRouteDetailsRequestSignature(request);
            if (attemptedRequestSignatures.has(requestSignature)) {
                continue;
            }
            attemptedRequestSignatures.add(requestSignature);

            const cacheKey = getRouteDetailsKey(
                preparedResponse,
                option,
                day,
                transportMode,
                overlapTier.key,
            );

            try {
                const routeDetails = await requestRouteDetailsOnce(
                    cacheKey,
                    request,
                );
                if (
                    !Array.isArray(routeDetails?.optimizedPlaces)
                    || routeDetails.optimizedPlaces.length === 0
                    || routeDetails.optimizedPlaces.length > expectedPlaceCount
                ) {
                    throw new Error('교통편 응답의 장소 구성을 확인할 수 없습니다.');
                }
                if (
                    normalizeTransportMode(transportMode) === 'PUBLIC_TRANSIT'
                    && hasPublicTransitRouteLimitViolation(routeDetails)
                ) {
                    constraintFallback = true;
                    lastRouteError = '대중교통 실제 경로가 30분 우선·40분 제한을 벗어났습니다.';
                    continue;
                }

                const { visibleResolvedPlaces } = getVisibleResolvedRoutePlaces(
                    routeDetails,
                    routeOriginPlace,
                );
                const resolvedOrdinaryPlaceCount = ordinaryRoutePlaceIds(
                    visibleResolvedPlaces,
                ).size;
                if (
                    normalizeTransportMode(transportMode) === 'PUBLIC_TRANSIT'
                    && resolvedOrdinaryPlaceCount < minimumOrdinaryPlaceCount
                ) {
                    lastRejectedRouteDetails = routeDetails;
                    constraintFallback = true;
                    lastRouteError = `대중교통 40분 제한을 지키는 과정에서 일반 장소가 ${resolvedOrdinaryPlaceCount}곳으로 줄어 최소 ${minimumOrdinaryPlaceCount}곳을 충족하지 못했습니다.`;
                    continue;
                }

                const overlapViolation = getRouteDetailOverlapViolation(
                    preparedResponse,
                    option,
                    day,
                    visibleResolvedPlaces,
                    transportMode,
                    overlapTier,
                    day.places,
                );
                if (overlapViolation) {
                    lastRejectedRouteDetails = routeDetails;
                    constraintFallback = true;
                    lastRouteError = overlapViolation;
                    continue;
                }

                preparedResponse = mergeRouteDetails(
                    preparedResponse,
                    option.optionNo,
                    day.dayNo,
                    routeDetails,
                    preparedResponse?.dailyStartTime
                        ?? recommendRequest?.dailyStartTime,
                    routeOriginPlace,
                    '',
                );
                routeApplied = true;
                break;
            } catch (error) {
                lastRouteError = error?.message
                    || '교통편을 일시적으로 확인할 수 없습니다.';
                const retryableMinimumFailure =
                    normalizeTransportMode(transportMode) === 'PUBLIC_TRANSIT'
                    && (
                        error?.code
                            === 'PUBLIC_TRANSIT_MINIMUM_PLACES_REQUIRED'
                        || /최소 장소 수|minimumOrdinaryPlaces/.test(
                            lastRouteError,
                        )
                    );
                if (retryableMinimumFailure) {
                    constraintFallback = true;
                    continue;
                }
                break;
            }
        }

        if (
            !routeApplied
            && normalizeTransportMode(transportMode) === 'PUBLIC_TRANSIT'
            && constraintFallback
        ) {
            try {
                const originalRouteRequest =
                    buildOriginalPublicTransitRouteRequest(
                        option,
                        day,
                        preparedResponse,
                        recommendRequest,
                        transportMode,
                    );
                const originalRouteCacheKey = getRouteDetailsKey(
                    preparedResponse,
                    option,
                    day,
                    transportMode,
                    'ORIGINAL_ACTUAL_ROUTE',
                );
                const originalRouteDetails = await requestRouteDetailsOnce(
                    originalRouteCacheKey,
                    originalRouteRequest,
                );

                if (
                    !Array.isArray(originalRouteDetails?.optimizedPlaces)
                    || originalRouteDetails.optimizedPlaces.length
                        !== expectedPlaceCount
                ) {
                    throw new Error(
                        '현재 장소 구성의 실제 대중교통 경로를 확인할 수 없습니다.',
                    );
                }

                const fallbackMessage = hasEstimatedRouteDetailsResponse(
                    originalRouteDetails,
                )
                    ? '대중교통 40분 이내로 교체할 장소를 찾지 못해 현재 장소 구성을 유지했습니다. 실제 경로를 찾지 못한 일부 구간만 예상값으로 표시합니다.'
                    : '대중교통 40분 이내로 교체할 장소를 찾지 못해 현재 장소 구성을 유지했습니다. 아래 이동시간은 현재 장소 사이의 실제 대중교통 경로 기준입니다.';

                preparedResponse = mergeRouteDetails(
                    preparedResponse,
                    option.optionNo,
                    day.dayNo,
                    originalRouteDetails,
                    preparedResponse?.dailyStartTime
                        ?? recommendRequest?.dailyStartTime,
                    routeOriginPlace,
                    fallbackMessage,
                );
                routeApplied = true;
            } catch (error) {
                lastRouteError = error?.message
                    || '현재 장소 구성의 실제 대중교통 경로를 확인할 수 없습니다.';
            }
        }

        if (!routeApplied) {
            const publicTransitFallbackMessage =
                normalizeTransportMode(transportMode) === 'PUBLIC_TRANSIT'
                    && constraintFallback
                    ? '대중교통 40분 이내로 교체할 장소를 찾지 못해 현재 장소 구성을 유지했습니다. 실제 경로 조회에 실패한 구간은 예상값으로 표시합니다.'
                    : lastRouteError
                        || '교통편을 일시적으로 확인할 수 없습니다.';

            if (lastRejectedRouteDetails) {
                const preserved = preserveActualLegsForOriginalPlaces(
                    lastRejectedRouteDetails,
                    day.places,
                    routeOriginPlace,
                );
                preparedResponse = mergeRouteDetails(
                    preparedResponse,
                    option.optionNo,
                    day.dayNo,
                    preserved.routeDetails,
                    preparedResponse?.dailyStartTime
                        ?? recommendRequest?.dailyStartTime,
                    routeOriginPlace,
                    publicTransitFallbackMessage,
                );
            } else {
                preparedResponse = mergeRouteDetails(
                    preparedResponse,
                    option.optionNo,
                    day.dayNo,
                    null,
                    preparedResponse?.dailyStartTime
                        ?? recommendRequest?.dailyStartTime,
                    routeOriginPlace,
                    publicTransitFallbackMessage || lastRouteError,
                );
            }
        }
    }

    return {
        response: preparedResponse,
        buildError: firstBuildError,
        interrupted,
    };
}

function getCourseTip(transport, travelCode) {
    const densityTip = travelCode.endsWith('P')
        ? '빽빽한 일정형 성향에 맞춰 볼거리를 알차게 담았어요.'
        : '여유 일정형 성향에 맞춰 장소별 체류 시간을 넉넉하게 잡았어요.';

    return transport ? `${transport.tip} ${densityTip}` : densityTip;
}

/**
 * 사용자가 고른 옵션에서 COURSE와 COURSE_DETAILS 저장에 필요한 값만 추립니다.
 *
 * <p>DAY 2 이후 화면은 전날 숙소를 지도용 출발점(H)으로만 덧붙이기 때문에
 * 실제 장소의 표시 순서가 2부터 시작할 수 있다. 출발 숙소는 저장 장소가
 * 아니므로, 저장 요청에서는 날짜별 실제 장소를 다시 1부터 연속 번호로
 * 정규화하고 새 첫 장소의 이전 구간 값도 비운다.</p>
 */
function buildSaveRequest(option, response, profile, travelCode, transportMode) {
    if (!transportMode) {
        throw new Error('추천 결과의 이동수단을 확인할 수 없습니다. 다시 추천받아 주세요.');
    }

    // 구간별 경로 종류도 도착 장소에 귀속된 값이므로 거리·시간과 함께 저장 요청에 유지합니다.
    const places = (option.days || []).flatMap((day) => (
        (day.places || [])
            .filter((place) => !place?.routeOrigin)
            .map((place, index) => {
                const isFirstSavedPlace = index === 0;

                return {
                    placeId: place.placeId,
                    category: place.category,
                    visitDate: place.visitDate || day.visitDate,
                    visitOrder: index + 1,
                    visitTime: place.visitTime || place.expectedVisitTimeHHmm || null,
                    expectedVisitMinutes: Number(place.expectedVisitMinutes) || 0,
                    distanceFromPreviousKm: isFirstSavedPlace
                        ? 0
                        : Number(place.distanceFromPreviousKm) || 0,
                    travelTimeFromPreviousMinutes: isFirstSavedPlace
                        ? 0
                        : Number(place.travelTimeFromPreviousMinutes) || 0,
                    transitPathType: isFirstSavedPlace
                        ? null
                        : normalizeTransitPathType(place.transitPathType),
                    routeEstimated: isFirstSavedPlace
                        ? false
                        : Boolean(place.routeEstimated),
                };
            })
    ));

    const historyCourseId = Number(option?.courseId);

    return {
        ...(Number.isInteger(historyCourseId) && historyCourseId > 0
            ? { courseId: historyCourseId }
            : {}),
        routeDetailsResolved: (option.days || []).some(
            (day) => Boolean(day?.routeDetailsAttempted),
        ),
        ...(profile.memberId ? { memberId: profile.memberId } : {}),
        ...(response?.resultId ? { resultId: response.resultId } : {}),
        title: option.title || option.optionName || '서울 맞춤 추천 코스',
        description: option.description || `${option.optionName || '맞춤'} 방식으로 구성한 서울 여행 코스`,
        travelCode,
        transportMode,
        courseType: 'SURVEY',
        region: option.region || null,
        publicCourse: false,
        places,
    };
}

/** 추천 API 응답을 기다리는 동안 세 개 옵션의 자리와 비슷한 스켈레톤을 표시합니다. */
function LoadingCards() {
    return (
        <div className="course-result-loading" aria-label="추천 코스를 불러오는 중">
            {[1, 2, 3].map((item) => (
                <div className="course-result-loading-card" key={item}>
                    <span className="course-result-loading-image" />
                    <span className="course-result-loading-lines">
                        <i />
                        <i />
                        <i />
                    </span>
                </div>
            ))}
        </div>
    );
}

/** 최초 추천과 재추천을 합쳐 최대 여섯 개 코스의 거리·시간·장소 수를 비교합니다. */
function ComparisonPanel({ options, selectedCourseKeys, activeDayNo, onRemove, onReset }) {
    const selectedOptions = options.filter((option) => selectedCourseKeys.includes(option.comparisonKey));
    const activeVisitDate = options
        .map((option) => getOptionDay(option, activeDayNo)?.visitDate)
        .find(Boolean);

    return (
        <section className="course-result-compare-card" id="course-comparison-panel">
            <div className="course-result-side-heading">
                <div>
                    <span className="course-result-side-icon"><GitCompareArrows size={17} aria-hidden="true" /></span>
                    <h2>코스 비교</h2>
                </div>
                <span>DAY {activeDayNo}{activeVisitDate ? ` · ${activeVisitDate}` : ''}</span>
            </div>

            {selectedOptions.length > 0 && (
                <div className="course-result-compare-reset-row">
                    <span>선택한 날짜만 비교 중</span>
                    <button type="button" onClick={onReset}>초기화</button>
                </div>
            )}

            {selectedOptions.length === 0 ? (
                <div className="course-result-compare-empty">
                    <GitCompareArrows size={25} strokeWidth={1.7} aria-hidden="true" />
                    <p>비교할 코스를 담아보세요.</p>
                    <small>거리와 예상 시간을 한눈에 볼 수 있어요.</small>
                </div>
            ) : (
                <div className="course-result-compare-list">
                    {selectedOptions.map((option) => {
                        const day = getOptionDay(option, activeDayNo);
                        const placeCount = Array.isArray(day?.places) ? day.places.length : 0;

                        return (
                            <div className="course-result-compare-item" key={option.comparisonKey}>
                                <span>
                                    <small className="course-result-compare-source">{option.recommendationLabel}</small>
                                    {option.optionName}
                                </span>
                                <button type="button" aria-label={`${option.recommendationLabel} ${option.optionName} 비교에서 제거`} onClick={() => onRemove(option.comparisonKey)}>
                                    <X size={14} aria-hidden="true" />
                                </button>
                                <strong>{Number(day?.dailyDistanceKm || 0).toFixed(1)}km</strong>
                                <small>{formatMinutes(day?.dailyCourseTimeMinutes)} · {placeCount}곳</small>
                            </div>
                        );
                    })}
                </div>
            )}

            <p className="course-result-compare-guide">
                {selectedOptions.length < 2
                    ? `두 개 이상 담으면 DAY ${activeDayNo}의 차이를 더 쉽게 비교할 수 있어요.`
                    : `${selectedOptions.length}개 코스의 DAY ${activeDayNo}를 비교 중이에요.`}
            </p>
        </section>
    );
}

/** 최초 추천 3개와 재추천 3개를 조회·비교하고, 사용자가 선택한 최대 6개 코스를 저장하는 결과 화면입니다. */
function CourseRecommendPage() {
    const [initialState] = useState(getInitialRecommendationPageState);
    const [response, setResponse] = useState(initialState.response);
    const responseRef = useRef(initialState.response);
    const [status, setStatus] = useState(initialState.status);
    const [source, setSource] = useState(initialState.source);
    const [requestError, setRequestError] = useState('');
    const [comparedCourseKeys, setComparedCourseKeys] = useState([]);
    const [selectedSaveCourseKeys, setSelectedSaveCourseKeys] = useState([]);
    const [focusedOptionNo, setFocusedOptionNo] = useState(null);
    const [isSavingSelected, setIsSavingSelected] = useState(false);
    const [savedCourseIdsBySelectionKey, setSavedCourseIdsBySelectionKey] = useState({});
    const [lastSavedCourseIds, setLastSavedCourseIds] = useState([]);
    const [notice, setNotice] = useState(null);
    const [recommendRequest, setRecommendRequest] = useState(initialState.request);
    const [isRecommendingAgain, setIsRecommendingAgain] = useState(false);
    // 최초 추천·재추천은 현재 DAY의 실제 경로 상세까지 합쳐진 뒤 로딩창을 닫습니다.
    const [isPreparingVisibleRoutes, setIsPreparingVisibleRoutes] = useState(() => {
        if (initialState.status === 'loading') {
            return true;
        }
        if (initialState.status !== 'success' || initialState.source === 'preview') {
            return false;
        }

        const initialDayNo = getFirstDayNo(initialState.response);
        return (initialState.response?.courseOptions || []).some((option) => {
            const day = getOptionDay(option, initialDayNo);
            return Boolean(day && !day.routeDetailsAttempted);
        });
    });
    const [pendingDayNo, setPendingDayNo] = useState(null);
    const dayRouteRequestRef = useRef(null);
    const queuedDayActivationRef = useRef(null);
    const foregroundRoutePriorityRef = useRef(false);
    const pageMountedRef = useRef(true);
    const [routePreparationRevision, setRoutePreparationRevision] = useState(0);
    const [activeDayNo, setActiveDayNo] = useState(() => getFirstDayNo(initialState.response));
    const [previousRecommendation, setPreviousRecommendation] = useState(() => (
        initialState.history?.previous || (initialState.response
            ? { response: initialState.response, request: initialState.request, source: initialState.source }
            : null)
    ));
    const [nextRecommendation, setNextRecommendation] = useState(
        initialState.history?.next || null,
    );
    const [showingNextRecommendation, setShowingNextRecommendation] = useState(
        Boolean(initialState.history?.showingNext),
    );
    const profile = useMemo(() => getUserProfile(), []);
    const options = Array.isArray(response?.courseOptions) ? response.courseOptions : [];
    const activeRecommendationKey = nextRecommendation && showingNextRecommendation
        ? 'next'
        : 'previous';
    const currentRecommendationEntry = {
        response,
        request: recommendRequest,
        source,
    };
    const saveRecommendationEntries = [
        {
            key: 'previous',
            entry: activeRecommendationKey === 'previous'
                ? currentRecommendationEntry
                : previousRecommendation,
        },
        ...(nextRecommendation ? [{
            key: 'next',
            entry: activeRecommendationKey === 'next'
                ? currentRecommendationEntry
                : nextRecommendation,
        }] : []),
    ].filter(({ entry }) => Array.isArray(entry?.response?.courseOptions));
    const savedStatusTargetJson = JSON.stringify(
        saveRecommendationEntries.flatMap(({ key, entry }) => (
            entry.response.courseOptions.flatMap((option) => {
                const identity = getSavedRecommendationIdentity(
                    entry.response.resultId,
                    option.recommendationKey,
                    option.courseId,
                );

                return identity ? [{
                    selectionKey: getSaveSelectionKey(key, option.optionNo),
                    identity,
                }] : [];
            })
        )),
    );
    const availableSaveCourseCount = saveRecommendationEntries.reduce(
        (count, { entry }) => count + entry.response.courseOptions.length,
        0,
    );
    const comparisonOptions = saveRecommendationEntries.flatMap(({ key, entry }) => (
        entry.response.courseOptions.map((option) => ({
            ...option,
            comparisonKey: getSaveSelectionKey(key, option.optionNo),
            recommendationLabel: key === 'previous' ? '첫 추천' : '다시 추천',
        }))
    ));
    const displayRecommendationCount = availableSaveCourseCount || 3;
    const travelCode = getStoredTravelCode(response);
    const travelBadges = getTravelTypeBadges(travelCode);
    // 실제 응답을 우선하고, 값이 없을 때만 요청 또는 UI 미리보기의 이동수단으로 보완합니다.
    const transportMode = resolveTransportMode(
        response?.transportMode,
        recommendRequest?.transportMode,
        source === 'preview' ? recommendationPreview.transportMode : null,
    );
    const transport = getTransportMeta(transportMode);
    const isWalkingRerecommendDisabled = transportMode === 'WALKING';
    const estimatedLegCount = countEstimatedLegs(options, activeDayNo);
    const hasEstimatedTravelTimes = estimatedLegCount > 0;
    const focusedOption = options.find((option) => option.optionNo === focusedOptionNo) || options[0];

    /** 비동기 경로 조회가 화면 전환 뒤의 응답을 덮어쓰지 않도록 최신 응답도 함께 기록합니다. */
    const updateResponse = useCallback((nextResponse) => {
        responseRef.current = nextResponse;
        setResponse(nextResponse);
    }, []);

    const requestRecommendation = useCallback(async () => {
        const storedRequest = recommendRequest
            || window.history.state?.courseRecommendRequest
            || readStoredObject([COURSE_RECOMMEND_REQUEST_KEY, 'courseRecommendRequest']);
        const request = withCurrentMemberId(storedRequest, profile.memberId);

        if (!request) {
            updateResponse(recommendationPreview);
            setSource('preview');
            setStatus('success');
            setRequestError('');
            setIsPreparingVisibleRoutes(false);
            return;
        }

        setStatus('loading');
        setRequestError('');
        setIsPreparingVisibleRoutes(true);

        try {
            const data = normalizeRecommendationResponse(await requestRecommendationOnce(request));

            if (!data) {
                throw new Error('추천 결과 형식을 확인할 수 없습니다.');
            }

            const hasPlaces = hasRecommendationPlaces(data);
            if (!hasPlaces) {
                sessionStorage.setItem(
                    COURSE_RECOMMEND_RESPONSE_KEY,
                    JSON.stringify(data),
                );
                sessionStorage.removeItem(COURSE_RECOMMEND_HISTORY_KEY);
                updateResponse(data);
                setSource('api');
                setFocusedOptionNo(data.courseOptions[0]?.optionNo ?? null);
                setActiveDayNo(getFirstDayNo(data));
                setPreviousRecommendation({
                    response: data,
                    request,
                    source: 'api',
                });
                setNextRecommendation(null);
                setShowingNextRecommendation(false);
                setStatus('empty');
                setIsPreparingVisibleRoutes(false);
                return;
            }

            const resolvedTransportMode = resolveTransportMode(
                data?.transportMode,
                request?.transportMode,
            );
            const initialDayNo = getFirstDayNo(data);
            const preparedResult = await prepareVisibleRoutesBeforeDisplay(
                data,
                request,
                initialDayNo,
                resolvedTransportMode,
            );
            const preparedData = preparedResult.response;

            await syncRecommendationSnapshot(preparedData, profile.memberId);

            sessionStorage.setItem(
                COURSE_RECOMMEND_RESPONSE_KEY,
                JSON.stringify(preparedData),
            );
            sessionStorage.setItem(
                COURSE_RECOMMEND_REQUEST_KEY,
                JSON.stringify(request),
            );
            sessionStorage.removeItem(COURSE_RECOMMEND_HISTORY_KEY);
            updateResponse(preparedData);
            setRecommendRequest(request);
            setSource('api');
            setFocusedOptionNo(
                preparedData.courseOptions[0]?.optionNo ?? null,
            );
            setActiveDayNo(initialDayNo);
            setPreviousRecommendation({
                response: preparedData,
                request,
                source: 'api',
            });
            setNextRecommendation(null);
            setShowingNextRecommendation(false);
            setStatus('success');
            setIsPreparingVisibleRoutes(false);

            if (preparedResult.buildError) {
                setNotice({
                    tone: 'error',
                    message: preparedResult.buildError,
                });
            }
        } catch (error) {
            setStatus('error');
            setRequestError(error?.message || '추천 코스를 불러오지 못했습니다.');
            setIsPreparingVisibleRoutes(false);
        }
    }, [profile.memberId, recommendRequest, updateResponse]);

    // 추천 요청 데이터가 앞 단계에서 전달된 경우에만 백엔드 추천 API를 실행합니다.
    useEffect(() => {
        if (initialState.status !== 'loading' || status !== 'loading') {
            return undefined;
        }

        let cancelled = false;
        queueMicrotask(() => {
            if (!cancelled) {
                requestRecommendation();
            }
        });

        return () => {
            cancelled = true;
        };
    }, [initialState.status, requestRecommendation, status]);

    useEffect(() => {
        pageMountedRef.current = true;

        return () => {
            pageMountedRef.current = false;
        };
    }, []);

    /**
     * 실제 경로가 합쳐진 추천 응답의 직렬화도 렌더 프레임과 분리합니다.
     * 응답이 다시 바뀌면 이전 예약을 취소해 가장 최신 결과만 저장합니다.
     */
    useEffect(() => {
        if (!response || status !== 'success' || source === 'preview') {
            return undefined;
        }

        return scheduleBrowserIdleTask(() => {
            sessionStorage.setItem(
                COURSE_RECOMMEND_RESPONSE_KEY,
                JSON.stringify(response),
            );
        }, 1_200);
    }, [response, source, status]);

    /**
     * 내 코스 목록의 resultId·recommendationKey를 현재 추천 옵션과 대조해
     * 새로고침하거나 이전·다음 추천을 전환해도 저장됨 상태를 복원합니다.
     */
    useEffect(() => {
        const targets = JSON.parse(savedStatusTargetJson);

        if (!profile.memberId || targets.length === 0) {
            setSavedCourseIdsBySelectionKey({});
            return undefined;
        }

        const controller = new AbortController();

        getMyCourses(profile.memberId, { signal: controller.signal })
            .then((myCoursesResponse) => {
                const savedCourseIdByIdentity = new Map(
                    normalizeMyCourseList(myCoursesResponse)
                        .map((savedCourse) => [
                            getSavedRecommendationIdentity(
                                savedCourse.resultId,
                                savedCourse.recommendationKey,
                                savedCourse.courseId,
                            ),
                            savedCourse.courseId,
                        ])
                        .filter(([identity, savedCourseId]) => (
                            identity
                            && Number.isInteger(Number(savedCourseId))
                            && Number(savedCourseId) > 0
                        )),
                );
                const nextSavedCourseIds = {};

                targets.forEach(({ selectionKey, identity }) => {
                    const savedCourseId = Number(
                        savedCourseIdByIdentity.get(identity),
                    );
                    if (Number.isInteger(savedCourseId) && savedCourseId > 0) {
                        nextSavedCourseIds[selectionKey] = savedCourseId;
                    }
                });

                setSavedCourseIdsBySelectionKey(nextSavedCourseIds);
                setSelectedSaveCourseKeys((previous) => previous.filter(
                    (selectionKey) => !nextSavedCourseIds[selectionKey],
                ));
            })
            .catch((error) => {
                if (error?.name !== 'AbortError') {
                    // 저장 여부 조회 실패만으로 추천 결과 화면을 막지는 않습니다.
                    setSavedCourseIdsBySelectionKey({});
                }
            });

        return () => controller.abort();
    }, [profile.memberId, savedStatusTargetJson]);

    const toggleCompare = (comparisonKey) => {
        setComparedCourseKeys((previous) => (
            previous.includes(comparisonKey)
                ? previous.filter((currentKey) => currentKey !== comparisonKey)
                : [...previous, comparisonKey].slice(-6)
        ));
    };

    /**
     * 대상 DAY의 세 옵션 경로를 순차 조회해 합칩니다.
     * 사용자가 누른 DAY는 준비 후 전환하고, 백그라운드 선조회는 현재 화면을 막지 않습니다.
     */
    const prepareDayBeforeDisplay = useCallback(async (
        requestedDayNo,
        activateAfterLoad = false,
        backgroundPrefetch = false,
    ) => {
        if (status !== 'success' || !response) {
            return;
        }

        const availableDayNo = getAvailableDayNo(response, requestedDayNo);
        const shouldResolveRoutes = source !== 'preview'
            && Boolean(transportMode)
            && hasUnresolvedRoutesForDay(response, availableDayNo);

        if (!shouldResolveRoutes) {
            if (activateAfterLoad) {
                if (
                    Number(queuedDayActivationRef.current)
                    === Number(availableDayNo)
                ) {
                    queuedDayActivationRef.current = null;
                }
                setActiveDayNo(availableDayNo);
            }
            if (!backgroundPrefetch) {
                setPendingDayNo(null);
                setIsPreparingVisibleRoutes(false);
            }
            return;
        }

        if (activateAfterLoad) {
            queuedDayActivationRef.current = availableDayNo;
            setPendingDayNo(availableDayNo);
            setIsPreparingVisibleRoutes(true);
        }

        if (dayRouteRequestRef.current) {
            return;
        }

        const requestToken = Symbol(`day-${availableDayNo}`);
        const responseAtRequestStart = response;
        let resolveCompletion;
        const completion = new Promise((resolve) => {
            resolveCompletion = resolve;
        });
        dayRouteRequestRef.current = {
            token: requestToken,
            dayNo: availableDayNo,
            backgroundPrefetch,
            completion,
        };

        if (!backgroundPrefetch) {
            setPendingDayNo(availableDayNo);
            setIsPreparingVisibleRoutes(true);
        }

        try {
            const preparedResult = await prepareVisibleRoutesBeforeDisplay(
                response,
                recommendRequest,
                availableDayNo,
                transportMode,
                backgroundPrefetch
                    ? () => {
                        const queuedDay = queuedDayActivationRef.current;
                        if (queuedDay != null) {
                            // 사용자가 지금 선조회 중인 DAY를 눌렀다면
                            // 진행 중 요청을 그대로 전면 요청으로 승격합니다.
                            return Number(queuedDay)
                                === Number(availableDayNo);
                        }

                        // 재추천이 시작되면 현재 옵션까지만 마치고
                        // 남은 백그라운드 옵션은 다음 유휴 시간으로 미룹니다.
                        return !foregroundRoutePriorityRef.current;
                    }
                    : null,
            );

            if (
                !pageMountedRef.current
                || dayRouteRequestRef.current?.token !== requestToken
                || responseRef.current !== responseAtRequestStart
            ) {
                return;
            }

            const preparedResponse = preparedResult.response;
            // 화면이 열린 뒤 선조회한 DAY 2+ 실제 경로도 같은 courseId의
            // 추천 이력에 다시 저장해 상세 화면이 예상값을 읽지 않게 합니다.
            await syncRecommendationSnapshot(
                preparedResponse,
                profile.memberId,
            );

            if (
                !pageMountedRef.current
                || dayRouteRequestRef.current?.token !== requestToken
                || responseRef.current !== responseAtRequestStart
            ) {
                return;
            }

            const queuedDayNo = Number(queuedDayActivationRef.current);
            const shouldActivatePreparedDay = Number.isFinite(queuedDayNo)
                && queuedDayNo === Number(availableDayNo);
            const preparedEntry = {
                response: preparedResponse,
                request: recommendRequest,
                source,
            };
            let updatedPreviousRecommendation = previousRecommendation;
            let updatedNextRecommendation = nextRecommendation;

            if (activeRecommendationKey === 'next') {
                updatedNextRecommendation = preparedEntry;
                setNextRecommendation(preparedEntry);
            } else {
                updatedPreviousRecommendation = preparedEntry;
                setPreviousRecommendation(preparedEntry);
            }

            updateResponse(preparedResponse);
            if (shouldActivatePreparedDay) {
                queuedDayActivationRef.current = null;
                setActiveDayNo(availableDayNo);
            }
            if (
                shouldActivatePreparedDay
                || (!backgroundPrefetch && queuedDayActivationRef.current == null)
            ) {
                setPendingDayNo(null);
                setIsPreparingVisibleRoutes(false);
            }

            sessionStorage.setItem(
                COURSE_RECOMMEND_RESPONSE_KEY,
                JSON.stringify(preparedResponse),
            );
            persistRecommendationHistory(
                updatedPreviousRecommendation,
                updatedNextRecommendation,
                showingNextRecommendation,
            );

            if (
                preparedResult.buildError
                && (!backgroundPrefetch || shouldActivatePreparedDay)
            ) {
                setNotice({
                    tone: 'error',
                    message: preparedResult.buildError,
                });
            }
        } catch (error) {
            if (
                pageMountedRef.current
                && dayRouteRequestRef.current?.token === requestToken
            ) {
                const queuedDayNo = Number(queuedDayActivationRef.current);
                const failedRequestedDay = Number.isFinite(queuedDayNo)
                    && queuedDayNo === Number(availableDayNo);

                if (failedRequestedDay) {
                    queuedDayActivationRef.current = null;
                }
                if (
                    failedRequestedDay
                    || (!backgroundPrefetch && queuedDayActivationRef.current == null)
                ) {
                    setPendingDayNo(null);
                    setIsPreparingVisibleRoutes(false);
                }
                if (!backgroundPrefetch || failedRequestedDay) {
                    setNotice({
                        tone: 'error',
                        message: error?.message
                            || `DAY ${availableDayNo}의 교통편을 확인하지 못했습니다.`,
                    });
                }
            }
        } finally {
            if (dayRouteRequestRef.current?.token === requestToken) {
                dayRouteRequestRef.current = null;
            }
            resolveCompletion();
            if (pageMountedRef.current) {
                setRoutePreparationRevision((current) => current + 1);
            }
        }
    }, [
        activeRecommendationKey,
        nextRecommendation,
        previousRecommendation,
        profile.memberId,
        recommendRequest,
        response,
        showingNextRecommendation,
        source,
        status,
        transportMode,
        updateResponse,
    ]);

    /** 이전 저장본에서 현재 DAY 경로가 비어 있을 때도 화면을 보여주기 전에 한 번만 준비합니다. */
    useEffect(() => {
        if (
            status !== 'success'
            || pendingDayNo !== null
            || dayRouteRequestRef.current
        ) {
            return;
        }

        if (
            source === 'preview'
            || !transportMode
            || !hasUnresolvedRoutesForDay(response, activeDayNo)
        ) {
            if (isPreparingVisibleRoutes) {
                setIsPreparingVisibleRoutes(false);
            }
            return;
        }

        void prepareDayBeforeDisplay(activeDayNo);
    }, [
        activeDayNo,
        activeRecommendationKey,
        isPreparingVisibleRoutes,
        pendingDayNo,
        prepareDayBeforeDisplay,
        response,
        source,
        status,
        transportMode,
    ]);

    /**
     * 현재 화면이 열린 뒤 다음 DAY부터 실제 경로를 하나씩 미리 준비합니다.
     * 사용자가 준비 중인 DAY를 누르면 해당 요청을 재사용하고, 다른 DAY를 누르면 다음 순서로 우선 처리합니다.
     */
    useEffect(() => {
        if (
            status !== 'success'
            || !response
            || source === 'preview'
            || !transportMode
            || dayRouteRequestRef.current
        ) {
            return undefined;
        }

        if (queuedDayActivationRef.current != null) {
            void prepareDayBeforeDisplay(
                queuedDayActivationRef.current,
                true,
            );
            return undefined;
        }

        if (isPreparingVisibleRoutes || isRecommendingAgain) {
            return undefined;
        }

        const nextDayNo = getNextUnresolvedDayNo(response, activeDayNo);
        if (nextDayNo == null) {
            return undefined;
        }

        return scheduleBrowserIdleTask(() => {
            if (
                !pageMountedRef.current
                || dayRouteRequestRef.current
                || queuedDayActivationRef.current != null
                || responseRef.current !== response
            ) {
                return;
            }

            void prepareDayBeforeDisplay(nextDayNo, false, true);
        });
    }, [
        activeDayNo,
        activeRecommendationKey,
        isPreparingVisibleRoutes,
        isRecommendingAgain,
        prepareDayBeforeDisplay,
        response,
        routePreparationRevision,
        source,
        status,
        transportMode,
    ]);

    const handleActiveDayChange = (requestedDayNo) => {
        const availableDayNo = getAvailableDayNo(response, requestedDayNo);
        if (availableDayNo === activeDayNo) {
            return;
        }

        void prepareDayBeforeDisplay(availableDayNo, true);
    };

    const handleRecommendAgain = async () => {
        // 도보는 짧은 이동시간과 코스 간 중복 제한을 동시에 만족하는 추가 후보가 부족해
        // 장소 데이터가 확장되기 전까지 재추천 API를 호출하지 않습니다.
        if (isWalkingRerecommendDisabled) {
            setNotice({
                tone: 'info',
                message: '도보 코스 추가 추천은 장소 데이터 확장 후 제공됩니다. 현재 추천된 코스 중에서 선택해 주세요.',
            });
            return;
        }

        // 같은 결과 묶음에서는 재추천 API를 한 번만 호출하고 이후에는 앞·뒤 결과만 전환합니다.
        if (nextRecommendation) {
            return;
        }

        const storedRequest = recommendRequest
            || window.history.state?.courseRecommendRequest
            || readStoredObject([COURSE_RECOMMEND_REQUEST_KEY, 'courseRecommendRequest']);
        const request = withCurrentMemberId(storedRequest, profile.memberId);

        if (!request || source === 'preview') {
            setNotice({
                tone: 'info',
                message: '현재는 화면 미리보기입니다. 취향 검사 결과와 장소 후보가 전달되면 같은 취향으로 다른 코스를 다시 추천합니다.',
            });
            return;
        }

        // 현재 세 조합을 제외 목록에 추가한 뒤, 직전 장소를 우선 제외해
        // 설문 기준 후보 풀을 DB에서 다시 조회합니다.
        let nextRequest;
        try {
            nextRequest = buildCourseRecommendAgainRequest(request, options);
        } catch (error) {
            setNotice({
                tone: 'error',
                message: error?.message || '재추천 요청을 만들지 못했습니다.',
            });
            return;
        }

        /*
         * 재추천 API와 실제 경로 조회가 끝날 때까지 우선 DAY를 고정합니다.
         * 기존 DAY 전환이 아직 진행 중이면 activeDayNo는 이전 DAY일 수 있으므로,
         * 사용자가 마지막으로 누른 대기 DAY를 우선합니다.
         */
        const preferredDayNo = getAvailableDayNo(
            responseRef.current || response,
            queuedDayActivationRef.current ?? activeDayNo,
        );

        queuedDayActivationRef.current = null;
        foregroundRoutePriorityRef.current = true;
        setPendingDayNo(null);
        setIsRecommendingAgain(true);
        setIsPreparingVisibleRoutes(true);
        setNotice(null);

        try {
            const refreshedDraft = await refreshCourseDraft(
                nextRequest.surveyId,
                nextRequest.previouslyRecommendedPlaceIds,
            );
            nextRequest = applyRefreshedCourseDraft(
                nextRequest,
                refreshedDraft,
            );

            const data = normalizeRecommendationResponse(
                await requestRecommendationOnce(nextRequest),
            );

            if (!data || !hasRecommendationPlaces(data)) {
                throw new Error(
                    '현재 장소 후보로는 이전 추천과 다른 새 코스를 만들 수 없어요. '
                    + '같은 코스를 반복해서 보여드리지는 않았습니다.',
                );
            }

            const nextActiveDayNo = getAvailableDayNo(data, preferredDayNo);
            const nextTransportMode = resolveTransportMode(
                data?.transportMode,
                nextRequest?.transportMode,
            );
            const activeBackgroundRequest = dayRouteRequestRef.current;
            if (activeBackgroundRequest?.backgroundPrefetch) {
                // 진행 중인 옵션 하나가 끝나는 즉시 백그라운드 작업이 양보합니다.
                // 그 완료를 확인한 뒤 새 추천의 현재 DAY를 최우선으로 조회합니다.
                await activeBackgroundRequest.completion;
            }
            const preparedResult = await prepareVisibleRoutesBeforeDisplay(
                data,
                nextRequest,
                nextActiveDayNo,
                nextTransportMode,
            );
            const preparedData = preparedResult.response;

            await syncRecommendationSnapshot(preparedData, profile.memberId);

            sessionStorage.setItem(COURSE_RECOMMEND_REQUEST_KEY, JSON.stringify(nextRequest));
            sessionStorage.setItem(COURSE_RECOMMEND_RESPONSE_KEY, JSON.stringify(preparedData));
            const previousEntry = {
                response: responseRef.current || response,
                request,
                source,
            };
            const nextEntry = { response: preparedData, request: nextRequest, source: 'api' };
            setPreviousRecommendation(previousEntry);
            setNextRecommendation(nextEntry);
            setShowingNextRecommendation(true);
            persistRecommendationHistory(previousEntry, nextEntry, true);
            setRecommendRequest(nextRequest);
            updateResponse(preparedData);
            setSource('api');
            setStatus('success');
            setRequestError('');
            setFocusedOptionNo(preparedData.courseOptions[0]?.optionNo ?? null);
            setActiveDayNo(nextActiveDayNo);
            setLastSavedCourseIds([]);
            setIsPreparingVisibleRoutes(false);
            setNotice(preparedResult.buildError
                ? {
                    tone: 'error',
                    message: preparedResult.buildError,
                }
                : {
                    tone: 'success',
                    message: '같은 취향을 바탕으로 다른 추천 코스와 현재 DAY의 실제 이동시간을 준비했어요.',
                });
        } catch (error) {
            setIsPreparingVisibleRoutes(false);
            setNotice({
                tone: 'error',
                message: error?.message || '다른 추천 코스를 만들지 못했습니다. 잠시 후 다시 시도해주세요.',
            });
        } finally {
            foregroundRoutePriorityRef.current = false;
            setIsRecommendingAgain(false);
        }
    };

    /** 재추천 전·후 결과를 새 API 호출 없이 전환합니다. */
    const showRecommendationResult = (entry, showNext) => {
        if (!entry?.response) return;

        const currentEntry = {
            response,
            request: recommendRequest,
            source,
        };
        const updatedPreviousRecommendation = showingNextRecommendation
            ? previousRecommendation
            : currentEntry;
        const updatedNextRecommendation = showingNextRecommendation
            ? currentEntry
            : nextRecommendation;

        setPreviousRecommendation(updatedPreviousRecommendation);
        setNextRecommendation(updatedNextRecommendation);
        queuedDayActivationRef.current = null;
        setPendingDayNo(null);
        setIsPreparingVisibleRoutes(false);
        updateResponse(entry.response);
        setRecommendRequest(entry.request);
        setSource(entry.source || 'api');
        setShowingNextRecommendation(showNext);
        setStatus(hasRecommendationPlaces(entry.response) ? 'success' : 'empty');
        setRequestError('');
        setFocusedOptionNo(entry.response.courseOptions?.[0]?.optionNo ?? null);
        setActiveDayNo((currentDayNo) => (
            getAvailableDayNo(entry.response, currentDayNo)
        ));
        setLastSavedCourseIds([]);
        setNotice(null);
        sessionStorage.setItem(COURSE_RECOMMEND_REQUEST_KEY, JSON.stringify(entry.request));
        sessionStorage.setItem(COURSE_RECOMMEND_RESPONSE_KEY, JSON.stringify(entry.response));
        persistRecommendationHistory(
            updatedPreviousRecommendation,
            updatedNextRecommendation,
            showNext,
        );
    };

    const toggleSaveSelection = (recommendationKey, optionNo) => {
        const selectionKey = getSaveSelectionKey(recommendationKey, optionNo);
        if (
            isSavingSelected
            || isRecommendingAgain
            || savedCourseIdsBySelectionKey[selectionKey]
        ) return;

        setSelectedSaveCourseKeys((previous) => {
            if (previous.includes(selectionKey)) {
                return previous.filter((currentKey) => currentKey !== selectionKey);
            }

            if (previous.length >= availableSaveCourseCount) {
                return previous;
            }

            return [...previous, selectionKey];
        });
    };

    const handleSaveSelected = async () => {
        if (isRecommendingAgain) return;

        const selectedCourses = saveRecommendationEntries.flatMap(({ key, entry }) => (
            entry.response.courseOptions
                .filter((option) => {
                    const selectionKey = getSaveSelectionKey(key, option.optionNo);
                    return selectedSaveCourseKeys.includes(selectionKey)
                        && !savedCourseIdsBySelectionKey[selectionKey];
                })
                .map((option) => ({
                    selectionKey: getSaveSelectionKey(key, option.optionNo),
                    option,
                    entry,
                }))
        ));

        if (selectedCourses.length === 0) {
            setNotice({
                tone: 'info',
                message: '저장할 코스를 한 개 이상 선택해주세요.',
            });
            return;
        }

        if (selectedCourses.some(({ entry }) => entry.source === 'preview')) {
            setNotice({
                tone: 'info',
                message: '현재는 화면 미리보기입니다. 실제 추천 응답이 들어오면 선택한 코스를 저장할 수 있습니다.',
            });
            return;
        }

        // 현재 CourseSaveService는 인증 토큰과 별개로 memberId를 필수 검증합니다.
        if (!profile.memberId) {
            setNotice({
                tone: 'error',
                message: '회원 ID를 확인할 수 없습니다. 로그인 성공 시 memberId를 사용자 정보와 함께 저장해주세요.',
            });
            return;
        }

        setIsSavingSelected(true);
        setLastSavedCourseIds([]);
        setNotice(null);

        try {
            const requests = selectedCourses.map(({ option, entry }) => {
                const selectedTransportMode = resolveTransportMode(
                    entry.response?.transportMode,
                    entry.request?.transportMode,
                    entry.source === 'preview' ? recommendationPreview.transportMode : null,
                );

                return buildSaveRequest(
                    option,
                    entry.response,
                    profile,
                    getStoredTravelCode(entry.response),
                    selectedTransportMode,
                );
            });
            const savedCourses = await saveCourseRequests(requests);

            const savedIdsBySelectionKey = {};
            selectedCourses.forEach(({ selectionKey, entry }, index) => {
                const savedCourseId = savedCourses[index].courseId;
                savedIdsBySelectionKey[selectionKey] = savedCourseId;
                rememberCourseTravelCode(savedCourseId, getStoredTravelCode(entry.response));
            });

            setSavedCourseIdsBySelectionKey((previous) => ({
                ...previous,
                ...savedIdsBySelectionKey,
            }));
            setLastSavedCourseIds(savedCourses.map((savedCourse) => savedCourse.courseId));
            setSelectedSaveCourseKeys([]);
            window.location.href = '/mypage/courses';
        } catch (error) {
            const partiallySavedCourses = Array.isArray(error?.savedCourses)
                ? error.savedCourses
                : [];

            if (partiallySavedCourses.length > 0) {
                const partiallySavedSelections = selectedCourses.slice(
                    0,
                    partiallySavedCourses.length,
                );
                const savedIdsBySelectionKey = {};
                partiallySavedSelections.forEach(({ selectionKey, entry }, index) => {
                    const savedCourseId = partiallySavedCourses[index].courseId;
                    savedIdsBySelectionKey[selectionKey] = savedCourseId;
                    rememberCourseTravelCode(savedCourseId, getStoredTravelCode(entry.response));
                });

                setSavedCourseIdsBySelectionKey((previous) => ({
                    ...previous,
                    ...savedIdsBySelectionKey,
                }));
                setSelectedSaveCourseKeys((previous) => previous.filter(
                    (selectionKey) => !savedIdsBySelectionKey[selectionKey],
                ));
                setLastSavedCourseIds(
                    partiallySavedCourses.map((savedCourse) => savedCourse.courseId),
                );
                setNotice({
                    tone: 'error',
                    message: `선택한 ${selectedCourses.length}개 중 ${partiallySavedCourses.length}개만 저장됐어요. 남은 코스를 다시 저장해 주세요.`,
                });
            } else {
                setNotice({
                    tone: 'error',
                    message: error?.message || '코스를 저장하지 못했습니다. 잠시 후 다시 시도해주세요.',
                });
            }
        } finally {
            setIsSavingSelected(false);
        }
    };

    const scrollToComparison = () => {
        document.getElementById('course-comparison-panel')?.scrollIntoView({
            behavior: 'smooth',
            block: 'center',
        });
    };

    const isDaySwitchLoading = isPreparingVisibleRoutes
        && status === 'success'
        && pendingDayNo != null
        && Number(pendingDayNo) !== Number(activeDayNo);
    const isResolvingVisibleRoutes = isPreparingVisibleRoutes
        && status === 'success'
        && !isDaySwitchLoading
        && !isRecommendingAgain;
    const isRecommendationLoading = status === 'loading'
        || isRecommendingAgain
        || isResolvingVisibleRoutes;
    const loadingTitle = isResolvingVisibleRoutes
        ? pendingDayNo
            ? `DAY ${pendingDayNo} 실제 이동시간을 확인하고 있어요`
            : '실제 이동시간을 확인하고 있어요'
        : isRecommendingAgain
            ? '새로운 추천 코스를 만들고 있어요'
            : '맞춤 추천 코스를 만들고 있어요';
    const loadingDescription = isResolvingVisibleRoutes
        ? `${transport?.label || '이동수단'} 기준 경로 확인이 끝난 뒤 DAY 화면을 전환할게요.`
        : isRecommendingAgain
            ? '기존 코스와 겹치지 않도록 다른 장소 조합과 이동 순서를 계산하고 있어요.'
            : '취향 점수와 이동 거리, 방문 순서를 함께 계산하고 있어요.';

    return (
        <div className="page course-result-page">
            <RecommendationLoadingOverlay
                active={isRecommendationLoading}
                title={loadingTitle}
                description={loadingDescription}
                longWaitDescription={isResolvingVisibleRoutes
                    ? `${transport?.label || '이동수단'} 경로 API 응답을 기다리고 있어요. 완료되는 즉시 추천 코스를 보여드릴게요.`
                    : "추천 가능한 장소 조합을 꼼꼼히 비교하고 있어 평소보다 조금 더 걸리고 있어요. 계산은 정상적으로 진행 중입니다."}
                delay={pendingDayNo ? 0 : 450}
            />
            <Header variant="default" />

            <main className="course-result-shell">
                <RecommendationSteps currentStep={4} />

                <section className="course-result-hero">
                    <div className="course-result-hero-copy">
                        <a className="course-result-back-btn" href="/survey/result" aria-label="취향 분석 결과로 돌아가기">
                            <ArrowLeft size={20} strokeWidth={2.1} aria-hidden="true" />
                        </a>

                        <div>
                            <p className="course-result-eyebrow"><Sparkles size={14} aria-hidden="true" /> 맞춤 추천 완료</p>
                            <h1><strong>{travelCode}</strong> {profile.name}님을 위한 맞춤 코스 추천</h1>
                            <p>여행 정보와 취향 분석 결과를 바탕으로 총 {displayRecommendationCount}개의 서울 여행 코스를 준비했어요.</p>

                            <div className="course-result-type-badges" aria-label={`여행 유형 ${travelCode}`}>
                                {travelBadges.map((badge) => (
                                    <span className={badge.tone} key={`${badge.letter}-${badge.label}`}>
                                        <b>{badge.letter}</b>
                                        {badge.label}
                                    </span>
                                ))}
                            </div>
                        </div>
                    </div>

                    <div className="course-result-hero-visual" aria-hidden="true">
                        <img src={heroSeoulImage} alt="" />
                        <span className="course-result-hero-orbit orbit-one" />
                        <span className="course-result-hero-orbit orbit-two" />
                        <span className="course-result-hero-pin"><Star size={18} fill="currentColor" /></span>
                    </div>
                </section>

                <div className="course-result-toolbar">
                    <div className="course-result-tabs">
                        <span className="active course-result-current-tab" aria-current="page">
                            <Star size={18} fill="currentColor" aria-hidden="true" />추천 코스
                        </span>
                        {!nextRecommendation ? (
                            <button
                                className={isWalkingRerecommendDisabled ? 'walking-rerecommend-disabled' : undefined}
                                type="button"
                                onClick={handleRecommendAgain}
                                disabled={
                                    isWalkingRerecommendDisabled
                                    || isRecommendingAgain
                                    || isSavingSelected
                                    || status === 'loading'
                                }
                                aria-label={
                                    isWalkingRerecommendDisabled
                                        ? '도보 코스 다시 추천받기 비활성화'
                                        : '같은 취향 검사 결과로 다른 코스 다시 추천받기'
                                }
                                title={
                                    isWalkingRerecommendDisabled
                                        ? '도보 코스 추가 추천은 장소 데이터 확장 후 제공됩니다.'
                                        : undefined
                                }
                            >
                                <RefreshCw
                                    className={isRecommendingAgain ? 'is-spinning' : undefined}
                                    size={18}
                                    aria-hidden="true"
                                />
                                {isRecommendingAgain
                                    ? '추천 만드는 중'
                                    : isWalkingRerecommendDisabled
                                        ? '재추천 준비 중'
                                        : '다시 추천받기'}
                            </button>
                        ) : (
                            <div className="course-result-history-navigation" aria-label="이전·다음 추천 결과 이동">
                                <button
                                    className={!showingNextRecommendation ? 'active' : undefined}
                                    type="button"
                                    disabled={!showingNextRecommendation || isSavingSelected}
                                    onClick={() => showRecommendationResult(previousRecommendation, false)}
                                >
                                    <ChevronLeft size={18} aria-hidden="true" />
                                    이전 추천 코스
                                </button>
                                <button
                                    className={showingNextRecommendation ? 'active' : undefined}
                                    type="button"
                                    disabled={showingNextRecommendation || isSavingSelected}
                                    onClick={() => showRecommendationResult(nextRecommendation, true)}
                                >
                                    다음 추천 코스
                                    <ChevronRight size={18} aria-hidden="true" />
                                </button>
                            </div>
                        )}
                    </div>

                    <div className="course-result-toolbar-actions">
                        <a className="course-result-home-button" href="/">
                            <House size={17} aria-hidden="true" />
                            메인화면으로 가기
                        </a>
                        {source === 'preview' && (
                            <span className="course-result-preview-label"><Info size={14} aria-hidden="true" />UI 미리보기</span>
                        )}
                        <button type="button" onClick={scrollToComparison}>
                            <GitCompareArrows size={17} aria-hidden="true" />
                            코스 비교
                            <b>{comparedCourseKeys.length}</b>
                        </button>
                    </div>
                </div>

                {status === 'success' && hasEstimatedTravelTimes && (
                        <section className="course-result-estimated-notice" role="status">
                            <span><Info size={18} aria-hidden="true" /></span>
                            <div>
                                <strong>
                                    표시 중인 DAY에서 {estimatedLegCount}개 구간만 예상값이에요
                                </strong>
                                <p>
                                    {transportMode === 'PUBLIC_TRANSIT'
                                        ? 'ODsay에서 실제 경로를 받지 못한 구간만 예상 거리와 시간으로 보완했습니다.'
                                        : 'OpenRouteService에서 실제 경로를 받지 못한 구간입니다. 백엔드의 OPENROUTESERVICE_API_KEY 설정과 실행 로그를 확인해 주세요.'}
                                </p>
                            </div>
                        </section>
                )}

                {status === 'success' && options.length > 0 && (
                    <section
                        className={`course-result-save-selection${selectedSaveCourseKeys.length > 0 ? ' has-selection' : ''}`}
                        aria-label="저장할 추천 코스 선택"
                        aria-busy={isSavingSelected}
                    >
                        <div className="course-result-save-selection-copy">
                            <span><Bookmark size={19} aria-hidden="true" /></span>
                            <div>
                                <strong>저장할 코스를 선택해 주세요</strong>
                                <small>{nextRecommendation ? `이전·다음 추천 결과에서 최대 ${availableSaveCourseCount}개까지 한 번에 각각 별도 코스로 저장돼요.` : '한 개는 단건으로, 두 개 이상은 한 번에 각각 별도 코스로 저장돼요.'}</small>
                            </div>
                        </div>

                        <div className="course-result-save-selection-actions">
                            <span><b>{selectedSaveCourseKeys.length}</b> / {availableSaveCourseCount} 선택</span>
                            {selectedSaveCourseKeys.length > 0 && (
                                <button
                                    className="secondary"
                                    type="button"
                                    disabled={isSavingSelected || isRecommendingAgain}
                                    onClick={() => setSelectedSaveCourseKeys([])}
                                >
                                    선택 해제
                                </button>
                            )}
                            <button
                                className="primary"
                                type="button"
                                disabled={isSavingSelected || isRecommendingAgain || selectedSaveCourseKeys.length === 0}
                                onClick={handleSaveSelected}
                            >
                                {isSavingSelected
                                    ? '저장 중...'
                                    : selectedSaveCourseKeys.length > 1
                                        ? `${selectedSaveCourseKeys.length}개 코스 함께 저장`
                                        : '선택한 코스 저장'}
                            </button>
                        </div>
                    </section>
                )}

                {status === 'loading' && <LoadingCards />}

                {status === 'error' && (
                    <section className="course-result-state-card" role="alert">
                        <span className="error"><Info size={24} aria-hidden="true" /></span>
                        <h2>추천 코스를 불러오지 못했어요</h2>
                        <p>{requestError}</p>
                        <div>
                            <button type="button" onClick={requestRecommendation}>
                                <RefreshCw size={16} aria-hidden="true" />다시 불러오기
                            </button>
                            <button
                                className="secondary"
                                type="button"
                                onClick={() => {
                                    updateResponse(recommendationPreview);
                                    setSource('preview');
                                    setStatus('success');
                                }}
                            >
                                화면 미리보기
                            </button>
                        </div>
                    </section>
                )}

                {status === 'empty' && (
                    <section className="course-result-state-card">
                        <span><Sparkles size={24} aria-hidden="true" /></span>
                        <h2>조건에 맞는 추천 코스가 아직 없어요</h2>
                        <p>여행 정보나 취향 답변을 조금 바꾸면 새로운 코스를 추천받을 수 있어요.</p>
                        <a href="/travel-info">여행 정보 다시 입력하기</a>
                    </section>
                )}

                {status === 'success' && options.length > 0 && (
                    <div className="course-result-layout">
                        <section className="course-result-list" aria-label="맞춤 추천 코스 목록">
                            {options.map((option) => {
                                const saveSelectionKey = getSaveSelectionKey(
                                    activeRecommendationKey,
                                    option.optionNo,
                                );

                                return (
                                    <CourseRecommendationCard
                                        option={option}
                                        transportMode={transportMode}
                                        activeDayNo={activeDayNo}
                                        isEstimatedTravelTime={Boolean(
                                            getOptionDay(option, activeDayNo)
                                                ?.places
                                                ?.some((place, placeIndex) => (
                                                    placeIndex > 0
                                                    && place.routeEstimated
                                                )),
                                        )}
                                        isCompared={comparedCourseKeys.includes(
                                            getSaveSelectionKey(activeRecommendationKey, option.optionNo),
                                        )}
                                        isSelectedForSave={selectedSaveCourseKeys.includes(saveSelectionKey)}
                                        isSelectionDisabled={isRecommendingAgain}
                                        isSaving={isSavingSelected}
                                        isSaved={Boolean(savedCourseIdsBySelectionKey[saveSelectionKey])}
                                        onToggleCompare={(optionNo) => (
                                            toggleCompare(getSaveSelectionKey(activeRecommendationKey, optionNo))
                                        )}
                                        onToggleSaveSelection={(optionNo) => (
                                            toggleSaveSelection(activeRecommendationKey, optionNo)
                                        )}
                                        onFocusOption={(nextOption) => setFocusedOptionNo(nextOption.optionNo)}
                                        onActiveDayChange={handleActiveDayChange}
                                        key={`${activeRecommendationKey}-${option.optionNo ?? option.optionType}`}
                                    />
                                );
                            })}
                        </section>

                        <aside className="course-result-sidebar">
                            <CourseMapPreview option={focusedOption} activeDayNo={activeDayNo} />

                            <ComparisonPanel
                                options={comparisonOptions}
                                selectedCourseKeys={comparedCourseKeys}
                                activeDayNo={activeDayNo}
                                onRemove={toggleCompare}
                                onReset={() => setComparedCourseKeys([])}
                            />

                            <section className="course-result-tip-card">
                                <span className="course-result-tip-icon"><Lightbulb size={19} aria-hidden="true" /></span>
                                <div>
                                    <h2>코스 TIP</h2>
                                    <p>{getCourseTip(transport, travelCode)}</p>
                                </div>
                            </section>
                        </aside>
                    </div>
                )}
            </main>

            <Footer />

            {isDaySwitchLoading && (
                <div
                    className={`course-result-toast course-result-route-loading-toast${notice ? ' has-notice' : ''}`}
                    role="status"
                    aria-live="polite"
                    aria-busy="true"
                >
                    <span><RefreshCw size={18} aria-hidden="true" /></span>
                    <p>
                        DAY {pendingDayNo} 실제 이동시간을 준비 중이에요.
                        준비되면 자동으로 전환돼요.
                    </p>
                </div>
            )}

            {notice && (
                <div className={`course-result-toast ${notice.tone}`} role="status">
                    <span>{notice.tone === 'success' ? <Check size={18} aria-hidden="true" /> : <Info size={18} aria-hidden="true" />}</span>
                    <p>{notice.message}</p>
                    {lastSavedCourseIds.length > 0 && (
                        <a href="/mypage/courses">내 코스에서 확인하기</a>
                    )}
                    <button type="button" aria-label="알림 닫기" onClick={() => setNotice(null)}>
                        <X size={16} aria-hidden="true" />
                    </button>
                </div>
            )}
        </div>
    );
}

export default CourseRecommendPage;

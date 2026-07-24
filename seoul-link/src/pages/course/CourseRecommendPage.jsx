import {
    useCallback,
    useEffect,
    useEffectEvent,
    useMemo,
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
    resolveCourseRouteDetails,
    saveCourse,
    saveCourses,
} from '../../api/courseApi';
import {
    buildCourseRecommendAgainRequest,
    COURSE_RECOMMEND_REQUEST_KEY,
    COURSE_RECOMMEND_RESPONSE_KEY,
} from '../../utils/courseRecommendationHandoff';
import {
    getTransportMeta,
    normalizeTransitPathType,
    normalizeTransportMode,
    resolveTransportMode,
} from '../../utils/courseTransport';
import { rememberCourseTravelCode } from '../../utils/courseTravelCode';
import recommendationPreview from '../../mocks/courseRecommendation.json';
import heroSeoulImage from '../../assets/images/hero-seoul-main.png';
import hanokImage from '../../assets/images/moods/mood-hanok-photo.png';
import walkingImage from '../../assets/images/moods/mood-walking-alley.png';
import sunsetImage from '../../assets/images/moods/mood-sunset-seoul.png';

const fallbackImages = [hanokImage, walkingImage, sunsetImage];
const COURSE_RECOMMEND_HISTORY_KEY = 'seoulinkCourseRecommendHistory';
const RECOMMENDATION_BASE_TIMEOUT_MS = 45_000;
const RECOMMENDATION_TIMEOUT_PER_EXTRA_DAY_MS = 15_000;
const RECOMMENDATION_MAX_TIMEOUT_MS = 150_000;
const ROUTE_DETAILS_REQUEST_TIMEOUT_MS = 30_000;
// 개발 모드의 중복 effect나 빠른 재시도로 같은 추천 요청이 겹치지 않도록 Promise를 재사용합니다.
const recommendationPromiseCache = new Map();
const routeDetailsPromiseCache = new Map();
// 이전·다음 결과 전환이나 카드 재마운트에서도 같은 DAY 상세를 다시 요청하지 않습니다.
const routeDetailsResultCache = new Map();

/** 재추천 전·후에 optionNo가 반복되므로 저장 선택 상태는 결과 묶음까지 포함해 구분합니다. */
function getSaveSelectionKey(recommendationKey, optionNo) {
    return `${recommendationKey}:${optionNo}`;
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

/** 현재의 3개 옵션 응답과 이전 단일 코스 응답을 화면 공통 구조로 맞춥니다. */
function normalizeRecommendationResponse(data) {
    if (Array.isArray(data?.courseOptions)) {
        const courseOptions = data.courseOptions.map((option) => ({
            ...option,
            estimatedTravelTimes: option?.estimatedTravelTimes == null
                ? Boolean(data.estimatedTravelTimes)
                : Boolean(option.estimatedTravelTimes),
        }));

        return {
            ...data,
            transportMode: normalizeTransportMode(data.transportMode),
            estimatedTravelTimes: Boolean(
                data.estimatedTravelTimes
                || courseOptions.some((option) => option.estimatedTravelTimes),
            ),
            courseOptions,
        };
    }

    // 이전 백엔드 응답(단일 저장 코스)도 화면에서 한 개 옵션으로 볼 수 있게 유지합니다.
    if (Array.isArray(data?.days)) {
        return {
            resultId: data.resultId ?? null,
            travelCode: data.travelCode ?? null,
            transportMode: normalizeTransportMode(data.transportMode),
            estimatedTravelTimes: Boolean(data.estimatedTravelTimes),
            courseOptions: [
                {
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
                },
            ],
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

/** 동일 코스·DAY 상세 요청과 완료 결과를 공유하고 자동 재시도 없이 30초에 중단합니다. */
function requestRouteDetailsOnce(cacheKey, request) {
    if (routeDetailsResultCache.has(cacheKey)) {
        return Promise.resolve(routeDetailsResultCache.get(cacheKey));
    }

    if (!routeDetailsPromiseCache.has(cacheKey)) {
        const controller = new AbortController();
        let timedOut = false;
        const timeoutId = window.setTimeout(() => {
            timedOut = true;
            controller.abort();
        }, ROUTE_DETAILS_REQUEST_TIMEOUT_MS);
        const pendingRequest = resolveCourseRouteDetails(request, {
            signal: controller.signal,
        })
            .catch((error) => {
                if (timedOut || error?.name === 'AbortError') {
                    throw new Error(
                        '교통편 확인이 30초를 넘겨 중단됐어요.',
                    );
                }
                throw error;
            })
            .then((routeDetails) => {
                routeDetailsResultCache.set(cacheKey, routeDetails);
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
    return data?.courseOptions?.find((option) => Array.isArray(option?.days) && option.days.length > 0)
        ?.days?.[0]?.dayNo ?? 1;
}

/** 옵션에서 현재 선택한 일차를 찾고 없으면 첫 일차를 사용합니다. */
function getOptionDay(option, activeDayNo) {
    const days = Array.isArray(option?.days) ? option.days : [];
    return days.find((day) => day?.dayNo === activeDayNo) || days[0] || null;
}

/** 현재 카드들에 실제로 표시 중인 DAY의 추정 경로 개수만 셉니다. */
function countEstimatedLegs(courseOptions, activeDayNo) {
    return (Array.isArray(courseOptions) ? courseOptions : []).reduce(
        (total, option) => {
            const day = getOptionDay(option, activeDayNo);
            return total + (day?.places || []).filter(
                (place, index) => index > 0 && Boolean(place.routeEstimated),
            ).length;
        },
        0,
    );
}

/** 추천 응답의 장소 한 건을 고정 순서 경로 조회 입력으로 변환합니다. */
function toRouteDetailsCandidate(place, visitDate) {
    return {
        placeId: Number(place.placeId),
        placeName: place.placeName,
        category: place.category,
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
        alternativeCandidates: [],
    };
}

/** 카드에 표시할 옵션의 한 DAY만 백엔드 고정 순서 경로 조회 요청으로 만듭니다. */
function buildRouteDetailsRequest(
    option,
    day,
    response,
    recommendRequest,
    transportMode,
) {
    const places = Array.isArray(day?.places) ? day.places : [];

    if (!option || !day || places.length === 0) {
        throw new Error('교통편을 확인할 코스 정보가 없습니다.');
    }

    return {
        resultId: response?.resultId ?? null,
        travelCode: response?.travelCode ?? null,
        transportMode,
        dailyStartTime: response?.dailyStartTime
            ?? recommendRequest?.dailyStartTime
            ?? null,
        placeCandidates: places.map((place) => (
            toRouteDetailsCandidate(place, day.visitDate)
        )),
        alternativeCandidates: [],
    };
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
function recalculateVisitTimes(places, dailyStartTime) {
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
        if (index > 0) {
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

/** 실제 경로 응답을 해당 옵션·DAY에만 합치고 시각·합계·예상 여부를 다시 계산합니다. */
function mergeRouteDetails(
    currentResponse,
    optionNo,
    dayNo,
    routeDetails,
    dailyStartTime,
    errorMessage = '',
) {
    if (!currentResponse) return currentResponse;

    const optimizedByPlaceId = new Map(
        (routeDetails?.optimizedPlaces || []).map((place) => [
            Number(place.placeId),
            place,
        ]),
    );
    const courseOptions = (currentResponse.courseOptions || []).map((option) => {
        if (option.optionNo !== optionNo) return option;

        const days = (option.days || []).map((day) => {
            if (day.dayNo !== dayNo) return day;

            const mergedPlaces = (day.places || []).map((place) => {
                const resolved = optimizedByPlaceId.get(Number(place.placeId));
                if (!resolved) return place;

                return {
                    ...place,
                    distanceFromPreviousKm: resolved.distanceFromPreviousKm,
                    travelTimeFromPreviousMinutes:
                    resolved.travelTimeFromPreviousMinutes,
                    transitPathType: normalizeTransitPathType(
                        resolved.transitPathType,
                    ),
                    routeEstimated: Boolean(resolved.routeEstimated),
                };
            });
            const places = recalculateVisitTimes(
                mergedPlaces,
                dailyStartTime,
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

            return {
                ...day,
                places,
                dailyDistanceKm,
                dailyTravelTimeMinutes,
                dailyVisitTimeMinutes,
                dailyCourseTimeMinutes:
                    dailyVisitTimeMinutes + dailyTravelTimeMinutes,
                routeDetailsAttempted: true,
                routeDetailsError: errorMessage || null,
            };
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

        return {
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
    });

    return {
        ...currentResponse,
        courseOptions,
        estimatedTravelTimes: courseOptions.some(
            (option) => Boolean(option.estimatedTravelTimes),
        ),
    };
}

function getRouteDetailsKey(option, day, transportMode) {
    const orderedPlaceIds = (day?.places || [])
        .map((place) => Number(place?.placeId) || 'place')
        .join('>');

    return [
        transportMode || 'transport',
        option?.recommendationKey || option?.optionNo || 'option',
        day?.dayNo || 'day',
        day?.visitDate || 'date',
        orderedPlaceIds || 'route',
    ].join(':');
}

function getCourseTip(transport, travelCode) {
    const densityTip = travelCode.endsWith('P')
        ? '빽빽한 일정형 성향에 맞춰 볼거리를 알차게 담았어요.'
        : '여유 일정형 성향에 맞춰 장소별 체류 시간을 넉넉하게 잡았어요.';

    return transport ? `${transport.tip} ${densityTip}` : densityTip;
}

/** 사용자가 고른 옵션에서 COURSE와 COURSE_DETAILS 저장에 필요한 값만 추립니다. */
function buildSaveRequest(option, response, profile, travelCode, transportMode) {
    if (!transportMode) {
        throw new Error('추천 결과의 이동수단을 확인할 수 없습니다. 다시 추천받아 주세요.');
    }

    // 구간별 경로 종류도 도착 장소에 귀속된 값이므로 거리·시간과 함께 저장 요청에 유지합니다.
    const places = (option.days || []).flatMap((day) => (
        (day.places || []).map((place, index) => ({
            placeId: place.placeId,
            category: place.category,
            visitDate: place.visitDate || day.visitDate,
            visitOrder: place.visitOrder ?? index + 1,
            visitTime: place.visitTime || place.expectedVisitTimeHHmm || null,
            expectedVisitMinutes: Number(place.expectedVisitMinutes) || 0,
            distanceFromPreviousKm: Number(place.distanceFromPreviousKm) || 0,
            travelTimeFromPreviousMinutes: Number(place.travelTimeFromPreviousMinutes) || 0,
            transitPathType: normalizeTransitPathType(place.transitPathType),
            routeEstimated: Boolean(place.routeEstimated),
        }))
    ));

    return {
        ...(profile.memberId ? { memberId: profile.memberId } : {}),
        ...(response?.resultId ? { resultId: response.resultId } : {}),
        title: option.title || option.optionName || '서울 맞춤 추천 코스',
        description: option.description || `${option.optionName || '맞춤'} 방식으로 구성한 서울 여행 코스`,
        travelCode,
        transportMode,
        courseType: 'SURVEY',
        region: '서울',
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
    const [routeDetailStatusByKey, setRouteDetailStatusByKey] = useState({});
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
    const estimatedLegCount = countEstimatedLegs(options, activeDayNo);
    const isResolvingVisibleRoutes = options.some((option) => (
        routeDetailStatusByKey[
            getRouteDetailsKey(
                option,
                getOptionDay(option, activeDayNo),
                transportMode,
            )
            ] === 'loading'
    ));
    const hasEstimatedTravelTimes = estimatedLegCount > 0;
    const focusedOption = options.find((option) => option.optionNo === focusedOptionNo) || options[0];

    const requestRecommendation = useCallback(async () => {
        const request = recommendRequest
            || window.history.state?.courseRecommendRequest
            || readStoredObject([COURSE_RECOMMEND_REQUEST_KEY, 'courseRecommendRequest']);

        if (!request) {
            setResponse(recommendationPreview);
            setSource('preview');
            setStatus('success');
            setRequestError('');
            return;
        }

        setStatus('loading');
        setRequestError('');

        try {
            const data = normalizeRecommendationResponse(await requestRecommendationOnce(request));

            if (!data) {
                throw new Error('추천 결과 형식을 확인할 수 없습니다.');
            }

            sessionStorage.setItem(COURSE_RECOMMEND_RESPONSE_KEY, JSON.stringify(data));
            sessionStorage.removeItem(COURSE_RECOMMEND_HISTORY_KEY);
            setResponse(data);
            setSource('api');
            setFocusedOptionNo(data.courseOptions[0]?.optionNo ?? null);
            setActiveDayNo(getFirstDayNo(data));
            setPreviousRecommendation({ response: data, request, source: 'api' });
            setNextRecommendation(null);
            setShowingNextRecommendation(false);
            setStatus(hasRecommendationPlaces(data) ? 'success' : 'empty');
        } catch (error) {
            setStatus('error');
            setRequestError(error?.message || '추천 코스를 불러오지 못했습니다.');
        }
    }, [recommendRequest]);

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

    const toggleCompare = (comparisonKey) => {
        setComparedCourseKeys((previous) => (
            previous.includes(comparisonKey)
                ? previous.filter((currentKey) => currentKey !== comparisonKey)
                : [...previous, comparisonKey].slice(-6)
        ));
    };

    /**
     * 카드에 현재 표시되는 옵션·DAY의 실제 인접 구간만 요청합니다.
     * 이동수단과 관계없이 같은 완료 결과·진행 중 요청을 모듈 캐시가 공유합니다.
     */
    const requestOptionRouteDetails = async (
        option,
        requestedDayNo,
    ) => {
        if (source === 'preview' || !transportMode) {
            return;
        }

        const day = getOptionDay(option, requestedDayNo);
        if (!day || day.routeDetailsAttempted) {
            return;
        }

        const cacheKey = getRouteDetailsKey(
            option,
            day,
            transportMode,
        );
        if (routeDetailStatusByKey[cacheKey] === 'loading') {
            return;
        }

        let routeRequest;
        try {
            routeRequest = buildRouteDetailsRequest(
                option,
                day,
                response,
                recommendRequest,
                transportMode,
            );
        } catch (error) {
            const message = error?.message
                || '교통편 요청을 만들지 못했습니다.';
            setResponse((currentResponse) => {
                const currentOption = currentResponse?.courseOptions?.find(
                    (candidate) => candidate.optionNo === option.optionNo,
                );
                const currentDay = getOptionDay(
                    currentOption,
                    requestedDayNo,
                );
                if (!currentDay) {
                    return currentResponse;
                }

                const nextResponse = mergeRouteDetails(
                    currentResponse,
                    option.optionNo,
                    currentDay.dayNo,
                    null,
                    currentResponse?.dailyStartTime
                    ?? recommendRequest?.dailyStartTime,
                    message,
                );
                sessionStorage.setItem(
                    COURSE_RECOMMEND_RESPONSE_KEY,
                    JSON.stringify(nextResponse),
                );
                return nextResponse;
            });
            setRouteDetailStatusByKey((previous) => ({
                ...previous,
                [cacheKey]: 'done',
            }));
            setNotice({
                tone: 'error',
                message,
            });
            return;
        }

        setRouteDetailStatusByKey((previous) => ({
            ...previous,
            [cacheKey]: 'loading',
        }));

        try {
            const routeDetails = await requestRouteDetailsOnce(
                cacheKey,
                routeRequest,
            );
            if (
                !Array.isArray(routeDetails?.optimizedPlaces)
                || routeDetails.optimizedPlaces.length !== day.places.length
            ) {
                throw new Error('교통편 응답의 장소 수가 일치하지 않습니다.');
            }

            setResponse((currentResponse) => {
                const currentOption = currentResponse?.courseOptions?.find(
                    (candidate) => candidate.optionNo === option.optionNo,
                );
                const currentDay = getOptionDay(
                    currentOption,
                    requestedDayNo,
                );
                if (getRouteDetailsKey(
                    currentOption,
                    currentDay,
                    transportMode,
                ) !== cacheKey) {
                    return currentResponse;
                }

                const nextResponse = mergeRouteDetails(
                    currentResponse,
                    option.optionNo,
                    currentDay.dayNo,
                    routeDetails,
                    currentResponse?.dailyStartTime
                    ?? recommendRequest?.dailyStartTime,
                );
                sessionStorage.setItem(
                    COURSE_RECOMMEND_RESPONSE_KEY,
                    JSON.stringify(nextResponse),
                );
                return nextResponse;
            });
        } catch (error) {
            const message = error?.message
                || '대중교통 경로를 일시적으로 확인할 수 없습니다.';
            setResponse((currentResponse) => {
                const currentOption = currentResponse?.courseOptions?.find(
                    (candidate) => candidate.optionNo === option.optionNo,
                );
                const currentDay = getOptionDay(
                    currentOption,
                    requestedDayNo,
                );
                if (getRouteDetailsKey(
                    currentOption,
                    currentDay,
                    transportMode,
                ) !== cacheKey) {
                    return currentResponse;
                }

                const nextResponse = mergeRouteDetails(
                    currentResponse,
                    option.optionNo,
                    currentDay.dayNo,
                    null,
                    currentResponse?.dailyStartTime
                    ?? recommendRequest?.dailyStartTime,
                    message,
                );
                sessionStorage.setItem(
                    COURSE_RECOMMEND_RESPONSE_KEY,
                    JSON.stringify(nextResponse),
                );
                return nextResponse;
            });
        } finally {
            setRouteDetailStatusByKey((previous) => ({
                ...previous,
                [cacheKey]: 'done',
            }));
        }
    };

    const requestVisibleRouteDetails = useEffectEvent((
        option,
        requestedDayNo,
    ) => {
        void requestOptionRouteDetails(option, requestedDayNo);
    });

    /**
     * 접힌 카드에도 장소별 시간이 이미 노출되므로 현재 선택된 DAY는 세 옵션 모두
     * 자동 보정합니다. 다른 DAY는 사용자가 탭을 눌렀을 때만 같은 방식으로 조회합니다.
     */
    useEffect(() => {
        if (status !== 'success' || source === 'preview' || !transportMode) {
            return;
        }

        const visibleOptions = Array.isArray(response?.courseOptions)
            ? response.courseOptions
            : [];
        visibleOptions.forEach((option) => {
            const day = getOptionDay(option, activeDayNo);
            if (day && !day.routeDetailsAttempted) {
                requestVisibleRouteDetails(option, day.dayNo);
            }
        });
    }, [
        activeDayNo,
        response,
        source,
        status,
        transportMode,
    ]);

    const handleRecommendAgain = async () => {
        // 같은 결과 묶음에서는 재추천 API를 한 번만 호출하고 이후에는 앞·뒤 결과만 전환합니다.
        if (nextRecommendation) {
            return;
        }

        const request = recommendRequest
            || window.history.state?.courseRecommendRequest
            || readStoredObject([COURSE_RECOMMEND_REQUEST_KEY, 'courseRecommendRequest']);

        if (!request || source === 'preview') {
            setNotice({
                tone: 'info',
                message: '현재는 화면 미리보기입니다. 취향 검사 결과와 장소 후보가 전달되면 같은 취향으로 다른 코스를 다시 추천합니다.',
            });
            return;
        }

        // 현재 세 조합을 제외 목록에 추가하되 설문·날짜·후보 풀은 그대로 재사용합니다.
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

        setIsRecommendingAgain(true);
        setNotice(null);

        try {
            const data = normalizeRecommendationResponse(
                await requestRecommendationOnce(nextRequest),
            );

            if (!data || !hasRecommendationPlaces(data)) {
                throw new Error('새로운 추천 결과에 표시할 장소가 없습니다.');
            }

            sessionStorage.setItem(COURSE_RECOMMEND_REQUEST_KEY, JSON.stringify(nextRequest));
            sessionStorage.setItem(COURSE_RECOMMEND_RESPONSE_KEY, JSON.stringify(data));
            const previousEntry = {
                response,
                request,
                source,
            };
            const nextEntry = { response: data, request: nextRequest, source: 'api' };
            setPreviousRecommendation(previousEntry);
            setNextRecommendation(nextEntry);
            setShowingNextRecommendation(true);
            persistRecommendationHistory(previousEntry, nextEntry, true);
            setRecommendRequest(nextRequest);
            setResponse(data);
            setSource('api');
            setStatus('success');
            setRequestError('');
            setFocusedOptionNo(data.courseOptions[0]?.optionNo ?? null);
            setActiveDayNo(getFirstDayNo(data));
            setLastSavedCourseIds([]);
            setNotice({
                tone: 'success',
                message: '같은 취향을 바탕으로 다른 추천 코스를 새로 준비했어요.',
            });
        } catch (error) {
            setNotice({
                tone: 'error',
                message: error?.message || '다른 추천 코스를 만들지 못했습니다. 잠시 후 다시 시도해주세요.',
            });
        } finally {
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
        setResponse(entry.response);
        setRecommendRequest(entry.request);
        setSource(entry.source || 'api');
        setShowingNextRecommendation(showNext);
        setStatus(hasRecommendationPlaces(entry.response) ? 'success' : 'empty');
        setRequestError('');
        setFocusedOptionNo(entry.response.courseOptions?.[0]?.optionNo ?? null);
        setActiveDayNo(getFirstDayNo(entry.response));
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
            setNotice({
                tone: 'success',
                message: selectedCourses.length === 1
                    ? '선택한 코스를 내 코스에 담았습니다.'
                    : `선택한 코스 ${selectedCourses.length}개를 각각 내 코스에 담았습니다.`,
            });
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

    const isRecommendationLoading = status === 'loading' || isRecommendingAgain;
    const loadingTitle = isRecommendingAgain
        ? '새로운 추천 코스를 만들고 있어요'
        : '맞춤 추천 코스를 만들고 있어요';
    const loadingDescription = isRecommendingAgain
        ? '기존 코스와 겹치지 않도록 다른 장소 조합과 이동 순서를 계산하고 있어요.'
        : '취향 점수와 이동 거리, 방문 순서를 함께 계산하고 있어요.';

    return (
        <div className="page course-result-page">
            <RecommendationLoadingOverlay
                active={isRecommendationLoading}
                title={loadingTitle}
                description={loadingDescription}
                longWaitDescription="추천 가능한 장소 조합을 꼼꼼히 비교하고 있어 평소보다 조금 더 걸리고 있어요. 계산은 정상적으로 진행 중입니다."
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
                                type="button"
                                onClick={handleRecommendAgain}
                                disabled={isRecommendingAgain || isSavingSelected || status === 'loading'}
                                aria-label="같은 취향 검사 결과로 다른 코스 다시 추천받기"
                            >
                                <RefreshCw
                                    className={isRecommendingAgain ? 'is-spinning' : undefined}
                                    size={18}
                                    aria-hidden="true"
                                />
                                {isRecommendingAgain ? '추천 만드는 중' : '다시 추천받기'}
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

                {status === 'success'
                    && (isResolvingVisibleRoutes || hasEstimatedTravelTimes)
                    && (
                        <section className="course-result-estimated-notice" role="status">
                            <span><Info size={18} aria-hidden="true" /></span>
                            <div>
                                <strong>
                                    {isResolvingVisibleRoutes
                                        ? `카드의 실제 ${transport?.label || '경로'} 이동시간을 확인하고 있어요`
                                        : `표시 중인 DAY에서 ${estimatedLegCount}개 구간만 예상값이에요`}
                                </strong>
                                <p>
                                    {isResolvingVisibleRoutes
                                        ? '현재 표시 중인 DAY의 인접 구간만 조회하고, 같은 구간은 캐시에서 재사용합니다.'
                                        : transportMode === 'PUBLIC_TRANSIT'
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
                                    setResponse(recommendationPreview);
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
                            {options.map((option, index) => {
                                const saveSelectionKey = getSaveSelectionKey(
                                    activeRecommendationKey,
                                    option.optionNo,
                                );

                                return (
                                    <CourseRecommendationCard
                                        option={option}
                                        transportMode={transportMode}
                                        fallbackImage={fallbackImages[index % fallbackImages.length]}
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
                                        isRouteDetailsLoading={
                                            routeDetailStatusByKey[
                                                getRouteDetailsKey(
                                                    option,
                                                    getOptionDay(option, activeDayNo),
                                                    transportMode,
                                                )
                                                ] === 'loading'
                                        }
                                        onToggleCompare={(optionNo) => (
                                            toggleCompare(getSaveSelectionKey(activeRecommendationKey, optionNo))
                                        )}
                                        onToggleSaveSelection={(optionNo) => (
                                            toggleSaveSelection(activeRecommendationKey, optionNo)
                                        )}
                                        onFocusOption={(nextOption) => setFocusedOptionNo(nextOption.optionNo)}
                                        onActiveDayChange={setActiveDayNo}
                                        onRequestRouteDetails={requestOptionRouteDetails}
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

import { recordRecommendedCourses } from '../api/courseApi';
import {
    COURSE_RECOMMEND_REQUEST_KEY,
    COURSE_RECOMMEND_RESPONSE_KEY,
} from './courseRecommendationHandoff';

const COURSE_RECOMMEND_HISTORY_KEY = 'seoulinkCourseRecommendHistory';

function safelyParse(value) {
    if (!value || typeof value !== 'string') return null;

    try {
        return JSON.parse(value);
    } catch {
        return null;
    }
}

/** 지도 표시 전용 출발 숙소는 추천 이력의 실제 방문 장소에서 제외합니다. */
function sanitizeResponse(response) {
    if (!response || typeof response !== 'object') return null;

    return {
        ...response,
        courseOptions: (response.courseOptions || []).map((option) => ({
            ...option,
            days: (option.days || []).map((day) => ({
                ...day,
                places: (day.places || []).filter((place) => !place?.routeOrigin),
            })),
        })),
    };
}

function getResponseIdentity(response) {
    const optionKeys = (response?.courseOptions || [])
        .map((option) => option?.recommendationKey)
        .filter(Boolean)
        .sort();

    if (!response?.resultId || optionKeys.length === 0) return null;
    return `${response.resultId}:${optionKeys.join('|')}`;
}

/** 회원 ID가 포함된 새 추천 요청은 추천 API 안에서 이미 이력이 저장됩니다. */
function wasRecordedByRecommendationApi(request) {
    const memberId = Number(request?.memberId);
    return Number.isInteger(memberId) && memberId > 0;
}

/** 추천 화면에서 실제 경로 상세 조회가 한 번이라도 끝난 응답인지 확인합니다. */
function hasResolvedRouteDetails(response) {
    return (response?.courseOptions || []).some((option) => (
        (option?.days || []).some((day) => Boolean(day?.routeDetailsAttempted))
    ));
}

/**
 * 현재 탭에 남아 있는 최초 추천·재추천 응답을 서버 이력에 한 번씩 동기화합니다.
 *
 * <p>이 기능은 수정 전 생성돼 DB에 없던 현재 세션의 코스를 복구하기 위한
 * 호환 처리다. 새 추천도 실제 경로 조회가 끝난 응답은 다시 보내 상세 화면이
 * 추천 화면에서 이미 받은 거리·시간을 그대로 재사용하게 한다.</p>
 */
export async function syncStoredRecommendationHistory(options = {}) {
    const currentResponse = safelyParse(
        sessionStorage.getItem(COURSE_RECOMMEND_RESPONSE_KEY),
    );
    const currentRequest = safelyParse(
        sessionStorage.getItem(COURSE_RECOMMEND_REQUEST_KEY),
    );
    const storedHistory = safelyParse(
        sessionStorage.getItem(COURSE_RECOMMEND_HISTORY_KEY),
    );
    const candidates = [
        {
            response: currentResponse,
            request: currentRequest,
        },
        {
            response: storedHistory?.previous?.source === 'preview'
                ? null
                : storedHistory?.previous?.response,
            request: storedHistory?.previous?.request,
        },
        {
            response: storedHistory?.next?.source === 'preview'
                ? null
                : storedHistory?.next?.response,
            request: storedHistory?.next?.request,
        },
    ];
    const uniqueResponses = new Map();

    candidates.forEach(({ response: candidate, request }) => {
        const response = sanitizeResponse(candidate);
        if (
            wasRecordedByRecommendationApi(request)
            && !hasResolvedRouteDetails(response)
        ) {
            return;
        }

        const identity = getResponseIdentity(response);
        if (identity) uniqueResponses.set(identity, response);
    });

    for (const response of uniqueResponses.values()) {
        await recordRecommendedCourses(response, options);
    }
}

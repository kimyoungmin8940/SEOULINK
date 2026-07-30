import { recordRecommendedCourses } from '../api/courseApi';
import { COURSE_RECOMMEND_RESPONSE_KEY } from './courseRecommendationHandoff';

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

/**
 * 현재 탭에 남아 있는 최초 추천·재추천 응답을 서버 이력에 한 번씩 동기화합니다.
 *
 * <p>이 기능은 수정 전 생성돼 DB에 없던 현재 세션의 코스를 복구하기 위한
 * 호환 처리이며, 새 추천은 추천 API 응답 시점에 서버가 직접 기록합니다.</p>
 */
export async function syncStoredRecommendationHistory(options = {}) {
    const currentResponse = safelyParse(
        sessionStorage.getItem(COURSE_RECOMMEND_RESPONSE_KEY),
    );
    const storedHistory = safelyParse(
        sessionStorage.getItem(COURSE_RECOMMEND_HISTORY_KEY),
    );
    const candidates = [
        currentResponse,
        storedHistory?.previous?.source === 'preview'
            ? null
            : storedHistory?.previous?.response,
        storedHistory?.next?.source === 'preview'
            ? null
            : storedHistory?.next?.response,
    ];
    const uniqueResponses = new Map();

    candidates.forEach((candidate) => {
        const response = sanitizeResponse(candidate);
        const identity = getResponseIdentity(response);
        if (identity) uniqueResponses.set(identity, response);
    });

    for (const response of uniqueResponses.values()) {
        await recordRecommendedCourses(response, options);
    }
}

import { normalizeTransportMode, TRANSPORT_MODES } from './courseTransport';

export const COURSE_RECOMMEND_REQUEST_KEY = 'seoulinkCourseRecommendRequest';
export const COURSE_RECOMMEND_RESPONSE_KEY = 'seoulinkCourseRecommendResponse';

const SUPPORTED_CATEGORIES = ['TOUR', 'RESTAURANT', 'CAFE', 'HOTEL'];
const MAX_EXCLUDED_RECOMMENDATIONS = 60;

function requestError(message) {
    throw new TypeError(`추천 코스 입력 오류: ${message}`);
}

/** 장소 후보와 중첩 대체 후보를 동일한 API 계약으로 정규화합니다. */
function normalizeCandidate(candidate, path) {
    if (!candidate || typeof candidate !== 'object') {
        requestError(`${path}는 장소 객체여야 합니다.`);
    }

    const placeId = Number(candidate.placeId);
    if (!Number.isInteger(placeId) || placeId < 1) {
        requestError(`${path}.placeId는 1 이상의 정수여야 합니다.`);
    }
    if (typeof candidate.placeName !== 'string' || !candidate.placeName.trim()) {
        requestError(`${path}.placeName은 필수입니다.`);
    }

    const category = typeof candidate.category === 'string'
        ? candidate.category.trim().toUpperCase()
        : '';
    if (!SUPPORTED_CATEGORIES.includes(category)) {
        requestError(`${path}.category는 ${SUPPORTED_CATEGORIES.join(', ')} 중 하나여야 합니다.`);
    }

    const alternatives = candidate.alternativeCandidates ?? [];
    if (!Array.isArray(alternatives)) {
        requestError(`${path}.alternativeCandidates는 배열이어야 합니다.`);
    }

    return {
        ...candidate,
        placeId,
        placeName: candidate.placeName.trim(),
        category,
        alternativeCandidates: alternatives.map(
            (alternative, index) => normalizeCandidate(
                { ...alternative, alternativeCandidates: [] },
                `${path}.alternativeCandidates[${index}]`,
            ),
        ),
    };
}

/** 하루치 목표 장소 수·카테고리 비율·후보 목록의 일관성을 검증합니다. */
function normalizeDailyPlan(dailyPlan, index) {
    const path = `dailyPlans[${index}]`;
    if (!dailyPlan || typeof dailyPlan !== 'object') {
        requestError(`${path}는 날짜별 일정 객체여야 합니다.`);
    }
    if (!/^\d{4}-\d{2}-\d{2}$/.test(dailyPlan.visitDate || '')) {
        requestError(`${path}.visitDate는 YYYY-MM-DD 형식이어야 합니다.`);
    }

    const targetPlaceCount = Number(dailyPlan.targetPlaceCount);
    if (!Number.isInteger(targetPlaceCount) || targetPlaceCount < 1) {
        requestError(`${path}.targetPlaceCount는 1 이상의 정수여야 합니다.`);
    }
    if (!dailyPlan.categoryTargets || typeof dailyPlan.categoryTargets !== 'object') {
        requestError(`${path}.categoryTargets는 필수입니다.`);
    }

    const categoryTargets = Object.fromEntries(SUPPORTED_CATEGORIES.map((category) => {
        const count = Number(dailyPlan.categoryTargets[category] ?? 0);
        if (!Number.isInteger(count) || count < 0) {
            requestError(`${path}.categoryTargets.${category}는 0 이상의 정수여야 합니다.`);
        }
        return [category, count];
    }));
    const categoryTargetSum = Object.values(categoryTargets).reduce(
        (sum, count) => sum + count,
        0,
    );
    if (categoryTargetSum < targetPlaceCount) {
        requestError(`${path}.categoryTargets 합계는 targetPlaceCount 이상이어야 합니다.`);
    }

    if (!Array.isArray(dailyPlan.placeCandidates) || dailyPlan.placeCandidates.length === 0) {
        requestError(`${path}.placeCandidates가 한 개 이상 필요합니다.`);
    }

    const placeCandidates = dailyPlan.placeCandidates.map(
        (candidate, candidateIndex) => normalizeCandidate(
            candidate,
            `${path}.placeCandidates[${candidateIndex}]`,
        ),
    );
    if (placeCandidates.length < targetPlaceCount) {
        requestError(`${path}.placeCandidates 수는 targetPlaceCount 이상이어야 합니다.`);
    }

    const candidateIds = new Set();
    placeCandidates.forEach((candidate, candidateIndex) => {
        if (candidateIds.has(candidate.placeId)) {
            requestError(`${path}.placeCandidates[${candidateIndex}].placeId가 중복되었습니다.`);
        }
        candidateIds.add(candidate.placeId);
    });

    SUPPORTED_CATEGORIES.forEach((category) => {
        const availableCount = placeCandidates.filter(
            (candidate) => candidate.category === category,
        ).length;

        if (availableCount < categoryTargets[category]) {
            requestError(
                `${path}.${category} 후보가 categoryTargets보다 적습니다. `
                + `(필요 ${categoryTargets[category]}개, 전달 ${availableCount}개)`,
            );
        }
    });

    return {
        ...dailyPlan,
        targetPlaceCount,
        categoryTargets,
        placeCandidates,
    };
}

/** 재추천 시 이미 본 조합의 키를 중복 없이 서버 허용 개수로 제한합니다. */
function normalizeExcludedRecommendationKeys(value) {
    if (value == null) {
        return [];
    }
    if (!Array.isArray(value)) {
        requestError('excludedRecommendationKeys는 배열이어야 합니다.');
    }
    if (value.length > MAX_EXCLUDED_RECOMMENDATIONS) {
        requestError(`excludedRecommendationKeys는 최대 ${MAX_EXCLUDED_RECOMMENDATIONS}개까지 보낼 수 있습니다.`);
    }

    return [...new Set(value.map((key, index) => {
        if (typeof key !== 'string' || !key.trim()) {
            requestError(`excludedRecommendationKeys[${index}]는 빈 문자열일 수 없습니다.`);
        }
        return key.trim();
    }))];
}

/** 서버 키가 없는 구버전 응답도 장소 구성만으로 같은 코스를 식별합니다. */
function deriveRecommendationKey(option) {
    if (typeof option?.recommendationKey === 'string' && option.recommendationKey.trim()) {
        return option.recommendationKey.trim();
    }

    return [...(option?.days ?? [])]
        .sort((left, right) => String(left?.visitDate).localeCompare(String(right?.visitDate)))
        .map((day) => [...(day?.places ?? [])]
            .filter((place) => Number.isInteger(Number(place?.placeId)))
            .sort((left, right) => Number(left.placeId) - Number(right.placeId))
            .map((place) => `${day.visitDate}:${Number(place.placeId)}`)
            .join(','))
        .filter(Boolean)
        .join('|');
}

/** 다른 담당 화면에서 받은 값을 코스 추천 API의 최종 요청 계약으로 정규화합니다. */
export function normalizeCourseRecommendRequest(data) {
    if (!data || typeof data !== 'object') {
        requestError('요청 데이터가 필요합니다.');
    }

    const resultId = Number(data.resultId);
    if (!Number.isInteger(resultId) || resultId < 1) {
        requestError('resultId는 1 이상의 정수여야 합니다.');
    }

    const dailyStartTime = data.dailyStartTime || '10:00';
    if (!/^(?:[01]\d|2[0-3]):[0-5]\d$/.test(dailyStartTime)) {
        requestError('dailyStartTime은 HH:mm 형식이어야 합니다.');
    }
    if (!Array.isArray(data.dailyPlans) || data.dailyPlans.length === 0) {
        requestError('dailyPlans가 한 개 이상 필요합니다.');
    }

    const travelCode = typeof data.travelCode === 'string'
        ? data.travelCode.trim().toUpperCase()
        : '';
    if (travelCode && !/^[A-Z]{5}$/.test(travelCode)) {
        requestError('travelCode는 영문 대문자 5자리여야 합니다.');
    }

    const transportMode = normalizeTransportMode(data.transportMode);
    if (!transportMode) {
        requestError(
            `transportMode는 ${Object.values(TRANSPORT_MODES).join(', ')} 중 하나여야 합니다.`,
        );
    }

    return {
        ...data,
        resultId,
        travelCode: travelCode || null,
        transportMode,
        dailyStartTime,
        excludedRecommendationKeys: normalizeExcludedRecommendationKeys(
            data.excludedRecommendationKeys,
        ),
        dailyPlans: data.dailyPlans.map(normalizeDailyPlan),
    };
}

/** 같은 설문·여행 정보는 유지하고 현재 화면의 코스 조합만 제외한 재추천 요청을 만든다. */
export function buildCourseRecommendAgainRequest(data, courseOptions) {
    const request = normalizeCourseRecommendRequest(data);
    const displayedKeys = (Array.isArray(courseOptions) ? courseOptions : [])
        .map(deriveRecommendationKey)
        .filter(Boolean);

    if (displayedKeys.length === 0) {
        requestError('현재 추천 코스의 제외 키를 만들 수 없습니다.');
    }

    return {
        ...request,
        // 기존 제외 목록과 현재 세 옵션을 합쳐 같은 장소 조합이 다시 나오지 않게 합니다.
        excludedRecommendationKeys: [
            ...new Set([
                ...request.excludedRecommendationKeys,
                ...displayedKeys,
            ]),
        ].slice(-MAX_EXCLUDED_RECOMMENDATIONS),
    };
}

/** 추천 입력을 보관해 새로고침이나 일반 링크 이동 뒤에도 결과 화면이 API를 호출하게 합니다. */
export function storeCourseRecommendRequest(data) {
    const request = normalizeCourseRecommendRequest(data);
    sessionStorage.setItem(COURSE_RECOMMEND_REQUEST_KEY, JSON.stringify(request));
    sessionStorage.removeItem(COURSE_RECOMMEND_RESPONSE_KEY);
    return request;
}

/** 현재의 경량 라우터에서 추천 입력을 저장한 뒤 결과 화면으로 이동합니다. */
export function openCourseRecommendPage(data) {
    storeCourseRecommendRequest(data);
    window.location.assign('/courses');
}

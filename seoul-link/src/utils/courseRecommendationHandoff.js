import { normalizeTransportMode, TRANSPORT_MODES } from './courseTransport';

export const COURSE_RECOMMEND_REQUEST_KEY = 'seoulinkCourseRecommendRequest';
export const COURSE_RECOMMEND_RESPONSE_KEY = 'seoulinkCourseRecommendResponse';
export const COURSE_RESOLVED_ROUTE_SNAPSHOT_KEY =
    'seoulinkResolvedCourseRouteSnapshots';

const SUPPORTED_CATEGORIES = ['TOUR', 'RESTAURANT', 'CAFE', 'HOTEL'];
const SUPPORTED_COMPANION_TYPES = ['SOLO', 'COUPLE', 'FRIENDS', 'FAMILY'];
const MAX_EXCLUDED_RECOMMENDATIONS = 60;
const MAX_PREVIOUSLY_RECOMMENDED_PLACES = 500;
const MAX_RESOLVED_ROUTE_SNAPSHOTS = 18;

/** 해당 DAY에서 화면에 표시할 모든 이동 구간이 실제 API값인지 확인합니다. */
function isResolvedRouteDay(day) {
    if (!day?.routeDetailsAttempted || !Array.isArray(day?.places)) {
        return false;
    }

    return day.places.every((place, index) => (
        !(
            (index > 0 || Boolean(day?.routeOriginPlace))
            && Boolean(place?.routeEstimated)
        )
    ));
}

/** 상세 화면 재사용에는 필요 없는 대체 후보 목록을 제외해 세션 용량을 줄입니다. */
function toResolvedRouteDaySnapshot(day) {
    const withoutAlternatives = (place) => {
        if (!place || typeof place !== 'object') return place;
        const snapshot = { ...place };
        delete snapshot.alternativeCandidates;
        return snapshot;
    };

    return {
        ...day,
        routeOriginPlace: withoutAlternatives(day.routeOriginPlace),
        places: (day.places || []).map(withoutAlternatives),
    };
}

/** 추천 결과에서 이미 받은 실제 경로를 courseId별 상세 진입용으로 보관합니다. */
export function persistResolvedCourseRouteSnapshots(response) {
    if (
        typeof sessionStorage === 'undefined'
        || !response
        || !Array.isArray(response?.courseOptions)
    ) {
        return;
    }

    let stored = { courses: {} };
    try {
        const parsed = JSON.parse(
            sessionStorage.getItem(COURSE_RESOLVED_ROUTE_SNAPSHOT_KEY),
        );
        if (parsed?.courses && typeof parsed.courses === 'object') {
            stored = parsed;
        }
    } catch {
        stored = { courses: {} };
    }

    response.courseOptions.forEach((option) => {
        const courseId = Number(option?.courseId);
        const resolvedDays = (option?.days || [])
            .filter(isResolvedRouteDay)
            .map(toResolvedRouteDaySnapshot);

        if (!Number.isInteger(courseId) || courseId < 1 || resolvedDays.length === 0) {
            return;
        }

        const previous = stored.courses[String(courseId)] || {};
        const daysByNumber = new Map(
            (previous?.option?.days || []).map(
                (day) => [Number(day?.dayNo), day],
            ),
        );
        resolvedDays.forEach((day) => {
            daysByNumber.set(Number(day?.dayNo), day);
        });

        stored.courses[String(courseId)] = {
            resultId: response.resultId ?? previous.resultId ?? null,
            travelCode: response.travelCode ?? previous.travelCode ?? null,
            transportMode:
                response.transportMode ?? previous.transportMode ?? null,
            savedAt: Date.now(),
            option: {
                ...(previous.option || {}),
                ...option,
                days: [...daysByNumber.values()].sort(
                    (left, right) => Number(left?.dayNo) - Number(right?.dayNo),
                ),
            },
        };
    });

    const retainedCourses = Object.fromEntries(
        Object.entries(stored.courses)
            .sort(([, left], [, right]) => (
                Number(right?.savedAt) - Number(left?.savedAt)
            ))
            .slice(0, MAX_RESOLVED_ROUTE_SNAPSHOTS),
    );

    try {
        sessionStorage.setItem(
            COURSE_RESOLVED_ROUTE_SNAPSHOT_KEY,
            JSON.stringify({ courses: retainedCourses }),
        );
    } catch {
        // 세션 저장 공간이 부족해도 추천 결과 자체와 DB 동기화는 계속합니다.
    }
}

/** 상세 courseId와 일치하는 최신 실제 경로 스냅샷을 반환합니다. */
export function getResolvedCourseRouteSnapshot(courseId) {
    const normalizedCourseId = Number(courseId);
    if (
        typeof sessionStorage === 'undefined'
        || !Number.isInteger(normalizedCourseId)
        || normalizedCourseId < 1
    ) {
        return null;
    }

    const readStoredSnapshot = () => {
        const parsed = JSON.parse(
            sessionStorage.getItem(COURSE_RESOLVED_ROUTE_SNAPSHOT_KEY),
        );
        return parsed?.courses?.[String(normalizedCourseId)] || null;
    };

    try {
        const storedSnapshot = readStoredSnapshot();
        if (storedSnapshot) return storedSnapshot;

        // 수정 전부터 세션에 남아 있던 추천 응답도 상세 진입 순간 한 번
        // 복구해, 새 추천을 다시 받지 않아도 이미 조회한 실제값을 사용합니다.
        const storedResponse = JSON.parse(
            sessionStorage.getItem(COURSE_RECOMMEND_RESPONSE_KEY),
        );
        const storedHistory = JSON.parse(
            sessionStorage.getItem('seoulinkCourseRecommendHistory'),
        );
        [
            storedResponse,
            storedHistory?.previous?.response,
            storedHistory?.next?.response,
        ].filter(Boolean).forEach(persistResolvedCourseRouteSnapshots);

        return readStoredSnapshot();
    } catch {
        return null;
    }
}

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

/** 재추천 점수 감점에 사용할 이전 장소 ID를 중복 없이 정규화합니다. */
function normalizePreviouslyRecommendedPlaceIds(value) {
    if (value == null) {
        return [];
    }
    if (!Array.isArray(value)) {
        requestError('previouslyRecommendedPlaceIds는 배열이어야 합니다.');
    }
    if (value.length > MAX_PREVIOUSLY_RECOMMENDED_PLACES) {
        requestError(
            `previouslyRecommendedPlaceIds는 최대 ${MAX_PREVIOUSLY_RECOMMENDED_PLACES}개까지 보낼 수 있습니다.`,
        );
    }

    return [...new Set(value.map((placeId, index) => {
        const normalized = Number(placeId);
        if (!Number.isInteger(normalized) || normalized < 1) {
            requestError(`previouslyRecommendedPlaceIds[${index}]는 1 이상의 정수여야 합니다.`);
        }
        return normalized;
    }))];
}

/** 백엔드가 계산한 상위 구 순서를 중복 없이 유지합니다. */
function normalizePreferredRegions(value) {
    if (value == null) {
        return [];
    }
    if (!Array.isArray(value)) {
        requestError('preferredRegions는 배열이어야 합니다.');
    }

    return [...new Set(value
        .map((region) => (
            typeof region === 'string' ? region.trim() : ''
        ))
        .filter(Boolean))]
        .slice(0, 5);
}

/** 숙소는 일반 후보와 분리하되 장소 DTO 자체는 같은 규칙으로 검증합니다. */
function normalizeHotelCandidates(value) {
    if (value == null) {
        return [];
    }
    if (!Array.isArray(value)) {
        requestError('hotelCandidates는 배열이어야 합니다.');
    }

    const hotelCandidates = value.map((candidate, index) => normalizeCandidate(
        candidate,
        `hotelCandidates[${index}]`,
    ));
    const hotelIds = new Set();
    hotelCandidates.forEach((candidate, index) => {
        if (candidate.category !== 'HOTEL') {
            requestError(`hotelCandidates[${index}].category는 HOTEL이어야 합니다.`);
        }
        if (hotelIds.has(candidate.placeId)) {
            requestError(`hotelCandidates[${index}].placeId가 중복되었습니다.`);
        }
        hotelIds.add(candidate.placeId);
    });
    return hotelCandidates;
}

/**
 * 숙소가 바뀌거나 실제 경로 보정으로 장소가 교체돼도 현재 화면의 일반 장소
 * 구성만 재추천 제외 기준으로 사용합니다. 장소는 ID순으로 정렬해 방문 순서가
 * 달라도 같은 장소 집합이면 같은 코스로 판단합니다.
 */
export function deriveRecommendationKey(option, transportMode) {
    const normalizedTransportMode = normalizeTransportMode(transportMode);
    const composition = [...(option?.days ?? [])]
        .sort((left, right) => String(left?.visitDate).localeCompare(String(right?.visitDate)))
        .map((day) => [...(day?.places ?? [])]
            .filter((place) => String(place?.category || '').trim().toUpperCase() !== 'HOTEL')
            .filter((place) => Number.isInteger(Number(place?.placeId)))
            .sort((left, right) => Number(left.placeId) - Number(right.placeId))
            .map((place) => `${day.visitDate}:${Number(place.placeId)}`)
            .join(','))
        .filter(Boolean)
        .join(',');

    if (composition && normalizedTransportMode) {
        return `${normalizedTransportMode}:${composition}`;
    }

    return typeof option?.recommendationKey === 'string'
        ? option.recommendationKey.trim()
        : '';
}

/** 다른 담당 화면에서 받은 값을 코스 추천 API의 최종 요청 계약으로 정규화합니다. */
export function normalizeCourseRecommendRequest(data) {
    if (!data || typeof data !== 'object') {
        requestError('요청 데이터가 필요합니다.');
    }

    const memberId = data.memberId == null || data.memberId === ''
        ? null
        : Number(data.memberId);
    if (
        memberId != null
        && (!Number.isInteger(memberId) || memberId < 1)
    ) {
        requestError('memberId는 1 이상의 정수여야 합니다.');
    }

    const resultId = Number(data.resultId);
    if (!Number.isInteger(resultId) || resultId < 1) {
        requestError('resultId는 1 이상의 정수여야 합니다.');
    }

    const travelCode = typeof data.travelCode === 'string'
        ? data.travelCode.trim().toUpperCase()
        : '';
    const scheduleType = String(data.scheduleType || travelCode.charAt(4) || '')
        .trim()
        .toUpperCase();
    const defaultDailyStartTime = scheduleType === 'P'
        ? '11:00'
        : scheduleType === 'R'
            ? '13:00'
            : '10:00';
    const dailyStartTime = data.dailyStartTime || defaultDailyStartTime;
    if (!/^(?:[01]\d|2[0-3]):[0-5]\d$/.test(dailyStartTime)) {
        requestError('dailyStartTime은 HH:mm 형식이어야 합니다.');
    }
    if (!Array.isArray(data.dailyPlans) || data.dailyPlans.length === 0) {
        requestError('dailyPlans가 한 개 이상 필요합니다.');
    }

    if (travelCode && !/^[A-Z]{5}$/.test(travelCode)) {
        requestError('travelCode는 영문 대문자 5자리여야 합니다.');
    }

    const transportMode = normalizeTransportMode(data.transportMode);
    if (!transportMode) {
        requestError(
            `transportMode는 ${Object.values(TRANSPORT_MODES).join(', ')} 중 하나여야 합니다.`,
        );
    }
    const companionType = typeof data.companionType === 'string'
        ? data.companionType.trim().toUpperCase()
        : '';
    if (companionType && !SUPPORTED_COMPANION_TYPES.includes(companionType)) {
        requestError(
            `companionType은 ${SUPPORTED_COMPANION_TYPES.join(', ')} 중 하나여야 합니다.`,
        );
    }

    return {
        ...data,
        memberId,
        resultId,
        travelCode: travelCode || null,
        companionType: companionType || null,
        preferredRegions: normalizePreferredRegions(data.preferredRegions),
        transportMode,
        dailyStartTime,
        excludedRecommendationKeys: normalizeExcludedRecommendationKeys(
            data.excludedRecommendationKeys,
        ),
        previouslyRecommendedPlaceIds: normalizePreviouslyRecommendedPlaceIds(
            data.previouslyRecommendedPlaceIds,
        ),
        hotelCandidates: normalizeHotelCandidates(data.hotelCandidates),
        dailyPlans: data.dailyPlans.map(normalizeDailyPlan),
    };
}

/** 같은 설문·여행 정보는 유지하고 현재 화면의 코스 조합만 제외한 재추천 요청을 만든다. */
export function buildCourseRecommendAgainRequest(data, courseOptions) {
    const request = normalizeCourseRecommendRequest(data);
    const displayedOptions = Array.isArray(courseOptions) ? courseOptions : [];
    const displayedKeys = displayedOptions
        .map((option) => deriveRecommendationKey(option, request.transportMode))
        .filter(Boolean);
    const displayedPlaceIds = displayedOptions
        .flatMap((option) => option?.days ?? [])
        .flatMap((day) => day?.places ?? [])
        .map((place) => Number(place?.placeId))
        .filter((placeId) => Number.isInteger(placeId) && placeId > 0);

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
        // 서버는 현재 화면에서 본 장소를 우선 완전 제외하고,
        // 카테고리 후보가 부족할 때만 감점된 점수로 최소 재사용합니다.
        previouslyRecommendedPlaceIds: [
            ...new Set([
                ...request.previouslyRecommendedPlaceIds,
                ...displayedPlaceIds,
            ]),
        ].slice(-MAX_PREVIOUSLY_RECOMMENDED_PLACES),
    };
}


/**
 * 재추천용으로 DB에서 다시 받은 후보 초안을 기존 요청에 덮어씁니다.
 * 이동수단과 누적 제외 키·이전 장소 ID는 유지하고, dailyPlans와 숙소 후보만
 * 방금 조회한 최신 후보 풀로 교체합니다.
 */
export function applyRefreshedCourseDraft(data, refreshedDraft) {
    const request = normalizeCourseRecommendRequest(data);
    if (!refreshedDraft || typeof refreshedDraft !== 'object') {
        requestError('새로 조회한 재추천 후보 초안이 필요합니다.');
    }

    const surveyId = Number(refreshedDraft.surveyId ?? request.surveyId);
    if (!Number.isInteger(surveyId) || surveyId < 1) {
        requestError('새 후보 초안의 surveyId가 올바르지 않습니다.');
    }

    return normalizeCourseRecommendRequest({
        ...request,
        surveyId,
        resultId: refreshedDraft.resultId ?? request.resultId,
        travelCode: refreshedDraft.travelCode ?? request.travelCode,
        scheduleType: refreshedDraft.scheduleType ?? request.scheduleType,
        companionType: refreshedDraft.companionType ?? request.companionType,
        // 첫 결과 화면에서 본 구 순위를 재추천에서도 유지해 화면과 코스 기준이 갈라지지 않게 합니다.
        preferredRegions: request.preferredRegions.length
            ? request.preferredRegions
            : refreshedDraft.preferredRegions,
        transportType: refreshedDraft.transportType ?? request.transportType,
        startDate: refreshedDraft.startDate ?? request.startDate,
        endDate: refreshedDraft.endDate ?? request.endDate,
        travelDays: refreshedDraft.travelDays ?? request.travelDays,
        dailyStartTime: refreshedDraft.dailyStartTime ?? request.dailyStartTime,
        hotelCandidates: refreshedDraft.hotelCandidates,
        dailyPlans: refreshedDraft.dailyPlans,
        // 현재 화면에서 실제 사용 중인 이동수단은 설문 원본 표기와 상관없이 유지합니다.
        transportMode: request.transportMode,
        excludedRecommendationKeys: request.excludedRecommendationKeys,
        previouslyRecommendedPlaceIds: request.previouslyRecommendedPlaceIds,
    });
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

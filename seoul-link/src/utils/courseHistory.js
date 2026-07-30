import { getCourseCoverImageUrls } from './courseImage';

/** 로그인 저장값의 손상된 JSON을 무시하고 null로 처리합니다. */
function safelyParse(value) {
    if (!value || typeof value !== 'string') return null;

    try {
        return JSON.parse(value);
    } catch {
        return null;
    }
}

/** 로그인 모듈 통합 전 memberId 탐색에 필요한 JWT payload만 읽습니다. */
function decodeTokenPayload(token) {
    try {
        const encodedPayload = token?.split('.')[1];
        if (!encodedPayload) return null;

        const normalized = encodedPayload
            .replace(/-/g, '+')
            .replace(/_/g, '/')
            .padEnd(Math.ceil(encodedPayload.length / 4) * 4, '=');
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

/** 서로 다른 목록 응답에서 사용한 코스 ID 필드명을 하나로 통일합니다. */
export function getCourseId(course) {
    const courseId = Number(
        course?.courseId
        ?? course?.savedCourseId
        ?? course?.recommendationId
        ?? course?.id,
    );

    return Number.isInteger(courseId) && courseId > 0 ? courseId : null;
}

/** 로그인 통합 전 백엔드가 요구하는 memberId를 기존 로그인 저장값에서 찾습니다. */
export function getCurrentMemberId() {
    const storedUser = ['user', 'member', 'loginUser']
        .map((key) => safelyParse(localStorage.getItem(key)))
        .find((value) => value && typeof value === 'object') || {};
    const tokenPayload = decodeTokenPayload(localStorage.getItem('accessToken')) || {};
    const memberId = Number(
        storedUser.memberId
        ?? storedUser.userId
        ?? storedUser.id
        ?? tokenPayload.memberId
        ?? tokenPayload.userId
        ?? tokenPayload.id
        ?? localStorage.getItem('memberId'),
    );

    return Number.isInteger(memberId) && memberId > 0 ? memberId : null;
}

function formatMinutes(value) {
    const minutes = Math.max(0, Math.round(Number(value) || 0));
    const hours = Math.floor(minutes / 60);
    const restMinutes = minutes % 60;

    if (hours === 0) return `${restMinutes}분`;
    return restMinutes === 0 ? `${hours}시간` : `${hours}시간 ${restMinutes}분`;
}

function nonEmptyArray(value) {
    return Array.isArray(value) ? value.filter(Boolean) : [];
}

/** 목록 API 응답을 서버 데이터 기준의 카드 형식으로 정규화합니다. */
export function normalizeRecommendedCourseList(
    response,
) {
    const list = Array.isArray(response)
        ? response
        : Array.isArray(response?.content)
            ? response.content
            : Array.isArray(response?.data)
                ? response.data
                : Array.isArray(response?.data?.content)
                    ? response.data.content
                    : [];
    const normalizedCourses = list
        .map((course) => {
            const courseId = getCourseId(course);
            const serverRegions = nonEmptyArray(course?.regions);
            const serverTags = nonEmptyArray(course?.tags);
            const totalMinutes = course?.totalCourseTimeMinutes
                ?? course?.totalVisitTimeMinutes;
            const coverImageUrls = getCourseCoverImageUrls(course);
            const coverImageUrl = coverImageUrls[0] || null;

            return {
                ...course,
                courseId,
                title: course?.title || '서울 맞춤 추천 코스',
                description: course?.description
                    || '취향 검사 결과를 바탕으로 추천된 서울 여행 코스입니다.',
                coverImageUrl,
                imageUrl: coverImageUrl,
                coverImageUrls,
                regions: serverRegions,
                duration: course?.duration
                    || `약 ${formatMinutes(totalMinutes)}`,
                area: course?.area
                    || serverRegions.join(' · ')
                    || course?.region
                    || '서울',
                tags: serverTags.length > 0
                    ? serverTags
                    : ['추천코스', '취향맞춤'],
                liked: course?.liked ?? false,
            };
        })
        .filter((course) => course.courseId);

    /*
     * 수정 전 화면 진입 시 세션 복구 API가 같은 추천을 한 번 더 저장한 경우에도
     * 서버의 안정적인 recommendationKey 기준으로 최신 행 하나만 표시합니다.
     * 키가 없는 구버전 응답은 서로 다른 코스를 잘못 합치지 않도록 courseId를
     * 그대로 사용합니다.
     */
    const seenRecommendationKeys = new Set();
    return normalizedCourses.filter((course) => {
        const recommendationKey = String(
            course?.recommendationKey || '',
        ).trim();
        const identity = recommendationKey
            ? `recommendation:${recommendationKey}`
            : `course:${course.courseId}`;

        if (seenRecommendationKeys.has(identity)) {
            return false;
        }
        seenRecommendationKeys.add(identity);
        return true;
    });
}

/** 내 코스 API 응답을 카드 모델로 통일하고 여행 시작·종료일을 보존합니다. */
export function normalizeMyCourseList(
    response,
) {
    return normalizeRecommendedCourseList(response).map((course) => ({
        ...course,
        startDate: course.startDate
            || course.travelStartDate
            || course.firstVisitDate
            || null,
        endDate: course.endDate
            || course.travelEndDate
            || course.lastVisitDate
            || course.startDate
            || null,
    }));
}

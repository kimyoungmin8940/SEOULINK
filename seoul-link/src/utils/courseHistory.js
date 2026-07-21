const RECOMMENDATION_STORAGE_KEYS = [
    'recommendedCourses',
    'myRecommendedCourses',
    'recommendationCourses',
];

/** 로그인·코스 캐시의 손상된 JSON을 무시하고 null로 처리합니다. */
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

/** 백엔드에 아직 없는 대표 이미지·태그를 유지하기 위한 화면 보조 캐시입니다. */
export function readRecommendedCourseCache() {
    for (const key of RECOMMENDATION_STORAGE_KEYS) {
        const parsed = safelyParse(localStorage.getItem(key));
        if (Array.isArray(parsed)) return parsed;
    }

    return [];
}

/** 선택 저장 직후 목록·상세 화면이 사용할 추천 코스 요약을 갱신합니다. */
export function writeRecommendedCourseCache(courses) {
    localStorage.setItem(
        RECOMMENDATION_STORAGE_KEYS[0],
        JSON.stringify(Array.isArray(courses) ? courses : []),
    );
}

/** 서버 상세 응답의 이미지·태그를 보완할 같은 코스의 로컬 요약을 찾습니다. */
export function findCachedRecommendedCourse(courseId) {
    return readRecommendedCourseCache().find(
        (course) => getCourseId(course) === Number(courseId),
    ) || null;
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

/** 목록 API 응답을 카드 형식으로 바꾸고, 서버의 null 필드만 기존 화면값으로 보완합니다. */
export function normalizeRecommendedCourseList(
    response,
    { cachedCourses = [], fallbackImages = [] } = {},
) {
    const list = Array.isArray(response)
        ? response
        : Array.isArray(response?.data)
            ? response.data
            : [];
    const cachedById = new Map(
        cachedCourses
            .map((course) => [getCourseId(course), course])
            .filter(([courseId]) => courseId),
    );

    return list
        .map((course, index) => {
            const courseId = getCourseId(course);
            const cached = cachedById.get(courseId) || {};
            const serverRegions = nonEmptyArray(course?.regions);
            const cachedRegions = nonEmptyArray(cached?.regions);
            const regions = serverRegions.length > 0 ? serverRegions : cachedRegions;
            const serverTags = nonEmptyArray(course?.tags);
            const cachedTags = nonEmptyArray(cached?.tags);
            const totalMinutes = course?.totalCourseTimeMinutes
                ?? course?.totalVisitTimeMinutes
                ?? cached?.totalCourseTimeMinutes
                ?? cached?.totalVisitTimeMinutes;
            const fallbackImage = fallbackImages.length > 0
                ? fallbackImages[index % fallbackImages.length]
                : null;
            const coverImageUrl = course?.coverImageUrl
                || course?.imageUrl
                || cached?.coverImageUrl
                || cached?.imageUrl
                || fallbackImage;

            return {
                ...cached,
                ...course,
                courseId,
                title: course?.title || cached?.title || '서울 맞춤 추천 코스',
                description: course?.description
                    || cached?.description
                    || '취향 검사 결과를 바탕으로 추천된 서울 여행 코스입니다.',
                coverImageUrl,
                imageUrl: coverImageUrl,
                regions,
                duration: course?.duration
                    || cached?.duration
                    || `약 ${formatMinutes(totalMinutes)}`,
                area: course?.area
                    || regions.join(' · ')
                    || course?.region
                    || cached?.area
                    || cached?.region
                    || '서울',
                tags: serverTags.length > 0
                    ? serverTags
                    : cachedTags.length > 0
                        ? cachedTags
                        : ['추천코스', '취향맞춤'],
                liked: course?.liked ?? cached?.liked ?? false,
            };
        })
        .filter((course) => course.courseId);
}

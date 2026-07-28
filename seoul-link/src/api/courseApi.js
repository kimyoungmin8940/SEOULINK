import { ApiError, apiClient } from './apiClient';
import { normalizeCourseRecommendRequest } from '../utils/courseRecommendationHandoff';

/** URL이나 쿼리 문자열에 들어갈 식별자를 API 호출 전에 공통 검증합니다. */
function requirePositiveId(value, fieldName) {
    if (!Number.isInteger(value) || value < 1) {
        throw new ApiError(
            0,
            'INVALID_REQUEST',
            `${fieldName}는 1 이상의 정수여야 합니다.`,
        );
    }
    return value;
}

/** 단일 일정 최적화의 선택 필드가 누락돼도 백엔드에는 빈 배열로 전달합니다. */
function withAlternativeCandidates(data) {
    return {
        ...data,
        alternativeCandidates: data?.alternativeCandidates ?? [],
    };
}

/**
 * 추천 후보의 방문 순서와 거리·시간만 계산합니다.
 * @param {import('../types/course').CourseOptimizeRequest} data
 * @param {RequestInit} [options]
 * @returns {Promise<import('../types/course').CourseOptimizeResponse>}
 */
export const optimizeCourse = (data, options = {}) => apiClient.post(
    '/courses/optimize',
    withAlternativeCandidates(data),
    options,
);

/**
 * 추천 카드에 현재 표시 중인 DAY의 방문 순서를 유지한 채 실제 인접 구간만 조회합니다.
 * @param {import('../types/course').CourseOptimizeRequest} data
 * @param {RequestInit} [options]
 * @returns {Promise<import('../types/course').CourseOptimizeResponse>}
 */
export const resolveCourseRouteDetails = (data, options = {}) => apiClient.post(
    '/courses/route-details',
    withAlternativeCandidates(data),
    options,
);

/**
 * 추천 후보를 최적화해 PREFERENCE/MIN_DISTANCE/BALANCED 3개 옵션을 받습니다.
 * 이 단계에서는 저장하지 않고, 결과 화면에서 고른 개수에 따라 saveCourse/saveCourses로 저장합니다.
 * @param {import('../types/course').CourseRecommendRequest} data
 * @param {RequestInit} [options]
 * @returns {Promise<import('../types/course').CourseRecommendResponse>}
 */
export const recommendCourse = (data, options = {}) => apiClient.post(
    '/courses/recommend',
    normalizeCourseRecommendRequest(data),
    options,
);

/** 저장된 설문을 날짜별 추천 후보 풀로 변환해 최적화 입력 초안을 받습니다. */
export const getCourseDraft = (surveyId, options = {}) => apiClient.get(
    `/courses/draft?surveyId=${requirePositiveId(Number(surveyId), '설문 ID')}`,
    options,
);

/**
 * 다시 추천받기에서 직전 결과의 장소를 우선 제외하고 DB 후보 풀을 새로 받습니다.
 * 후보 조회만 수행하므로 ODsay·ORS 같은 외부 경로 API는 호출하지 않습니다.
 */
export const refreshCourseDraft = (
    surveyId,
    previouslyRecommendedPlaceIds = [],
    options = {},
) => {
    const normalizedIds = Array.isArray(previouslyRecommendedPlaceIds)
        ? [...new Set(previouslyRecommendedPlaceIds
            .map(Number)
            .filter((placeId) => Number.isInteger(placeId) && placeId > 0))]
        : [];

    return apiClient.post(
        '/courses/draft/recommend-again',
        {
            surveyId: requirePositiveId(Number(surveyId), '설문 ID'),
            previouslyRecommendedPlaceIds: normalizedIds,
        },
        options,
    );
};

/**
 * 사용자가 확정한 최적화 코스를 저장합니다.
 * @param {import('../types/course').CourseSaveRequest} data
 * @param {RequestInit} [options]
 * @returns {Promise<import('../types/course').CourseSaveResponse>}
 */
export const saveCourse = (data, options = {}) => apiClient.post(
    '/courses',
    data,
    options,
);

/**
 * 사용자가 선택한 추천 코스 1~3개를 한 트랜잭션으로 저장합니다.
 * @param {import('../types/course').CourseBatchSaveRequest} data
 * @param {RequestInit} [options]
 * @returns {Promise<import('../types/course').CourseBatchSaveResponse>}
 */
export const saveCourses = (data, options = {}) => apiClient.post(
    '/courses/batch',
    data,
    options,
);

/**
 * 저장 코스의 날짜별 장소 상세를 조회합니다.
 * @param {number} courseId
 * @param {RequestInit} [options]
 * @returns {Promise<import('../types/course').CourseDetailResponse>}
 */
export const getCourseDetail = (courseId, options = {}) => {
    const { memberId, ...requestOptions } = options;
    const query = memberId
        ? `?memberId=${requirePositiveId(memberId, '회원 ID')}`
        : '';

    return apiClient.get(
        `/courses/${requirePositiveId(courseId, '코스 ID')}${query}`,
        requestOptions,
    );
};

/**
 * 회원의 설문 기반 추천 코스 카드 목록을 조회합니다.
 * @param {number} memberId
 * @param {RequestInit} [options]
 * @returns {Promise<import('../types/course').CourseSummaryResponse[]>}
 */
export const getRecommendedCourses = (memberId, options = {}) => apiClient.get(
    `/courses/recommended?memberId=${requirePositiveId(memberId, '회원 ID')}`,
    options,
);

/** @deprecated getRecommendedCourses를 사용하세요. */
export const getCourses = getRecommendedCourses;

/**
 * 회원이 보유한 전체 코스 카드 목록을 조회합니다.
 * @param {number} memberId
 * @param {RequestInit} [options]
 * @returns {Promise<import('../types/course').CourseSummaryResponse[]>}
 */
export const getMyCourses = (memberId, options = {}) => apiClient.get(
    `/courses/my?memberId=${requirePositiveId(memberId, '회원 ID')}`,
    options,
);

/**
 * 기존 호출부 호환용 별칭이며 내부적으로 CUSTOM 유형으로 저장합니다.
 * @param {import('../types/course').CourseSaveRequest} data
 * @param {RequestInit} [options]
 * @returns {Promise<import('../types/course').CourseSaveResponse>}
 */
export const getSavedRecommendedCourses = (memberId, options = {}) => apiClient.get(
    `/courses/members/${requirePositiveId(memberId, '회원 ID')}/saved`,
    options,
);

export const getCustomCourses = (memberId, options = {}) => apiClient.get(
    `/courses/members/${requirePositiveId(memberId, '회원 ID')}/custom`,
    options,
);

export const removeSavedRecommendedCourse = (courseId, memberId, options = {}) =>
    apiClient.delete(
        `/courses/${requirePositiveId(courseId, '코스 ID')}/saved?memberId=${requirePositiveId(memberId, '회원 ID')}`,
        options,
    );

export const updateCourse = (courseId, data, options = {}) => {
    const { memberId, ...requestOptions } = options;
    const query = memberId
        ? `?memberId=${requirePositiveId(memberId, '회원 ID')}`
        : '';

    return apiClient.put(
        `/courses/${requirePositiveId(courseId, '코스 ID')}${query}`,
        data,
        requestOptions,
    );
};

export const deleteCourse = (courseId, memberId, options = {}) =>
    apiClient.delete(
        `/courses/${requirePositiveId(courseId, '코스 ID')}?memberId=${requirePositiveId(memberId, '회원 ID')}`,
        options,
    );

export const createCustomCourse = (data, options = {}) => saveCourse(
    { ...data, courseType: 'CUSTOM' },
    options,
);

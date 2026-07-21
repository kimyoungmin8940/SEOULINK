import { ApiError, apiClient } from './apiClient';

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
 * 추천 후보 최적화와 SURVEY 코스 저장을 한 번에 처리합니다.
 * @param {import('../types/course').CourseRecommendRequest} data
 * @param {RequestInit} [options]
 * @returns {Promise<import('../types/course').CourseRecommendResponse>}
 */
export const recommendCourse = (data, options = {}) => apiClient.post(
    '/courses/recommend',
    withAlternativeCandidates(data),
    options,
);

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
 * 저장 코스의 날짜별 장소 상세를 조회합니다.
 * @param {number} courseId
 * @param {RequestInit} [options]
 * @returns {Promise<import('../types/course').CourseDetailResponse>}
 */
export const getCourseDetail = (courseId, options = {}) => apiClient.get(
    `/courses/${requirePositiveId(courseId, '코스 ID')}`,
    options,
);

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
    `/members/me/courses?memberId=${requirePositiveId(memberId, '회원 ID')}`,
    options,
);

/**
 * 기존 호출부 호환용 별칭이며 내부적으로 CUSTOM 유형으로 저장합니다.
 * @param {import('../types/course').CourseSaveRequest} data
 * @param {RequestInit} [options]
 * @returns {Promise<import('../types/course').CourseSaveResponse>}
 */
export const createCustomCourse = (data, options = {}) => saveCourse(
    { ...data, courseType: 'CUSTOM' },
    options,
);

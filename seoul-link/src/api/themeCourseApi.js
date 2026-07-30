import { ApiError, apiClient } from './apiClient';
import { saveCourse } from './courseApi';

function requirePositiveId(value, fieldName) {
    const id = Number(value);

    if (!Number.isInteger(id) || id < 1) {
        throw new ApiError(
            0,
            'INVALID_REQUEST',
            `${fieldName}는 1 이상의 정수여야 합니다.`,
        );
    }

    return id;
}

function requireSourceCourseKey(value) {
    const sourceCourseKey = String(value || '').trim();

    if (!/^[A-Z0-9_]+$/.test(sourceCourseKey)) {
        throw new ApiError(
            0,
            'INVALID_REQUEST',
            '테마 코스 원본 키가 올바르지 않습니다.',
        );
    }

    return sourceCourseKey;
}

function toThemeSaveRequest(course, memberId) {
    const places = course.days.flatMap((day) => day.places.map((place) => {
        if (!place.databaseMatched) {
            throw new ApiError(
                0,
                'THEME_PLACE_NOT_FOUND',
                `${place.placeName} 장소가 DB에 없어 코스를 저장할 수 없습니다.`,
            );
        }

        return {
            placeId: requirePositiveId(place.placeId, '장소 ID'),
            category: place.category,
            dayNo: requirePositiveId(day.dayNo, 'dayNo'),
            visitDate: null,
            visitOrder: place.visitOrder,
            visitTime: place.expectedVisitTimeHHmm || place.visitTime || null,
            expectedVisitMinutes: place.expectedVisitMinutes,
            distanceFromPreviousKm: place.distanceFromPreviousKm || 0,
            travelTimeFromPreviousMinutes:
                place.travelTimeFromPreviousMinutes || 0,
            transitPathType: place.transitPathType || null,
            routeEstimated: Boolean(place.routeEstimated),
        };
    }));

    return {
        memberId: requirePositiveId(memberId, '회원 ID'),
        title: course.title,
        description: course.description,
        transportMode: course.transportMode || 'PUBLIC_TRANSIT',
        courseType: 'THEME',
        sourceCourseKey: requireSourceCourseKey(course.sourceCourseKey),
        region: course.region,
        publicCourse: false,
        places,
    };
}

/** 승혜 백엔드 계약에 맞춰 특정 테마 코스 한 건의 저장 여부를 조회합니다. */
export const getThemeCourseBookmarkStatus = (
    memberId,
    sourceCourseKey,
    options = {},
) => {
    const query = new URLSearchParams({
        memberId: String(requirePositiveId(memberId, '회원 ID')),
        sourceCourseKey: requireSourceCourseKey(sourceCourseKey),
    });

    return apiClient.get(`/courses/theme-bookmarks/status?${query}`, options);
};

/** DB에서 확인된 장소 ID와 방문 순서를 THEME 회원 코스로 저장합니다. */
export const saveThemeCourse = (course, memberId, options = {}) => saveCourse(
    toThemeSaveRequest(course, memberId),
    options,
);

/** 승혜 백엔드 계약에 맞춰 저장된 THEME 코스를 삭제합니다. */
export const deleteSavedThemeCourse = (
    memberId,
    sourceCourseKey,
    options = {},
) => {
    const query = new URLSearchParams({
        memberId: String(requirePositiveId(memberId, '회원 ID')),
        sourceCourseKey: requireSourceCourseKey(sourceCourseKey),
    });

    return apiClient.delete(`/courses/theme-bookmarks?${query}`, options);
};

export const COURSE_REQUEST_STATUS = Object.freeze({
    IDLE: 'idle',
    LOADING: 'loading',
    SUCCESS: 'success',
    EMPTY: 'empty',
    ERROR: 'error',
});

export function createIdleState() {
    return { status: COURSE_REQUEST_STATUS.IDLE, data: null, error: null };
}

export function createLoadingState() {
    return { status: COURSE_REQUEST_STATUS.LOADING, data: null, error: null };
}

/** 배열 또는 날짜별 장소가 비어 있으면 결과 없음 상태로 판단합니다. */
export function isCourseDataEmpty(data) {
    if (data == null) {
        return true;
    }
    if (Array.isArray(data)) {
        return data.length === 0;
    }
    if (Array.isArray(data.days)) {
        return !data.days.some((day) => (
            Array.isArray(day.places) && day.places.length > 0
        ));
    }
    if (Array.isArray(data.optimizedPlaces)) {
        return data.optimizedPlaces.length === 0;
    }
    return false;
}

export function createResolvedState(data, isEmpty = isCourseDataEmpty) {
    return {
        status: isEmpty(data)
            ? COURSE_REQUEST_STATUS.EMPTY
            : COURSE_REQUEST_STATUS.SUCCESS,
        data,
        error: null,
    };
}

/** ApiError와 일반 오류를 화면에서 사용하는 동일한 구조로 변환합니다. */
export function createErrorState(error) {
    const status = Number.isFinite(error?.status) ? error.status : 0;
    const code = typeof error?.code === 'string'
        ? error.code
        : 'UNKNOWN_ERROR';
    const message = error instanceof Error
        ? error.message
        : '알 수 없는 오류가 발생했습니다.';

    return {
        status: COURSE_REQUEST_STATUS.ERROR,
        data: null,
        error: {
            status,
            code,
            message,
            retryable: status === 0 || status >= 500,
        },
    };
}

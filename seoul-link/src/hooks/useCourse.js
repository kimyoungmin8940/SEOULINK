import { useCallback, useEffect, useRef, useState } from 'react';
import {
    getCourseDetail,
    getMyCourses,
    getRecommendedCourses,
    optimizeCourse,
    recommendCourse,
    saveCourse,
} from '../api/courseApi';
import {
    createErrorState,
    createIdleState,
    createLoadingState,
    createResolvedState,
    isCourseDataEmpty,
} from '../utils/courseRequestState';

/**
 * 코스 API의 idle/loading/success/empty/error 상태와 요청 취소·재시도를 관리합니다.
 * request 함수는 첫 번째 인자로 AbortSignal을 받고 이후 run 인자를 전달받습니다.
 */
export function useCourseRequest(
    request,
    { enabled = false, isEmpty = isCourseDataEmpty } = {},
) {
    const [state, setState] = useState(createIdleState);
    const controllerRef = useRef(null);
    const lastArgsRef = useRef([]);
    const requestIdRef = useRef(0);
    const mountedRef = useRef(false);

    const cancel = useCallback(() => {
        controllerRef.current?.abort();
    }, []);

    const run = useCallback(async (...args) => {
        cancel();
        const controller = new AbortController();
        const requestId = ++requestIdRef.current;
        controllerRef.current = controller;
        lastArgsRef.current = args;
        setState(createLoadingState());

        try {
            const data = await request(controller.signal, ...args);
            if (mountedRef.current && requestId === requestIdRef.current) {
                setState(createResolvedState(data, isEmpty));
            }
            return data;
        } catch (error) {
            const isCurrentRequest = requestId === requestIdRef.current;
            if (error instanceof Error && error.name === 'AbortError') {
                if (mountedRef.current && isCurrentRequest) {
                    setState(createIdleState());
                }
                return null;
            }

            if (mountedRef.current && isCurrentRequest) {
                setState(createErrorState(error));
            }
            throw error;
        }
    }, [cancel, isEmpty, request]);

    const retry = useCallback(
        () => run(...lastArgsRef.current),
        [run],
    );

    const reset = useCallback(() => {
        requestIdRef.current += 1;
        cancel();
        setState(createIdleState());
    }, [cancel]);

    useEffect(() => {
        mountedRef.current = true;
        return () => {
            mountedRef.current = false;
            cancel();
        };
    }, [cancel]);

    useEffect(() => {
        if (!enabled) {
            return undefined;
        }

        run().catch(() => {});
        return cancel;
    }, [cancel, enabled, run]);

    return {
        ...state,
        run,
        retry,
        reset,
        cancel,
    };
}

/** 저장된 코스 상세를 courseId 변경 시 자동 조회합니다. */
export function useCourse(courseId, { enabled = true } = {}) {
    const request = useCallback(
        (signal) => getCourseDetail(courseId, { signal }),
        [courseId],
    );

    return useCourseRequest(request, {
        enabled: enabled && Number.isInteger(courseId) && courseId > 0,
    });
}

/** 회원의 SURVEY 추천 코스 목록을 자동 조회합니다. */
export function useRecommendedCourses(memberId, { enabled = true } = {}) {
    const request = useCallback(
        (signal) => getRecommendedCourses(memberId, { signal }),
        [memberId],
    );

    return useCourseRequest(request, {
        enabled: enabled && Number.isInteger(memberId) && memberId > 0,
    });
}

/** 회원의 전체 보유 코스 목록을 자동 조회합니다. */
export function useMyCourses(memberId, { enabled = true } = {}) {
    const request = useCallback(
        (signal) => getMyCourses(memberId, { signal }),
        [memberId],
    );

    return useCourseRequest(request, {
        enabled: enabled && Number.isInteger(memberId) && memberId > 0,
    });
}

/** run(requestBody)로 추천 최적화와 저장을 실행합니다. */
export function useCourseRecommendation() {
    const request = useCallback(
        (signal, data) => recommendCourse(data, { signal }),
        [],
    );
    return useCourseRequest(request);
}

/** run(requestBody)으로 저장 없이 최적화만 실행합니다. */
export function useCourseOptimization() {
    const request = useCallback(
        (signal, data) => optimizeCourse(data, { signal }),
        [],
    );
    return useCourseRequest(request);
}

/** run(requestBody)으로 사용자가 확정한 코스를 저장합니다. */
export function useCourseSave() {
    const request = useCallback(
        (signal, data) => saveCourse(data, { signal }),
        [],
    );
    return useCourseRequest(request);
}

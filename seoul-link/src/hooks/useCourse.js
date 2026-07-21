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
        // 새 요청을 시작할 때 이전 요청을 취소해 늦게 도착한 응답이 최신 화면을 덮지 않게 합니다.
        cancel();
        const controller = new AbortController();
        const requestId = ++requestIdRef.current;
        controllerRef.current = controller;
        lastArgsRef.current = args;
        setState(createLoadingState());

        try {
            const data = await request(controller.signal, ...args);
            // 요청 번호가 같은 경우에만 상태를 반영해 요청 간 경합을 한 번 더 방지합니다.
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
        // 진행 중인 요청의 번호를 무효화한 뒤 초기 상태로 되돌립니다.
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

        // effect 본문에서 바로 상태를 변경하지 않고 다음 작업 큐에서 자동 조회합니다.
        const timeoutId = window.setTimeout(() => {
            run().catch(() => {});
        }, 0);

        return () => {
            window.clearTimeout(timeoutId);
            cancel();
        };
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

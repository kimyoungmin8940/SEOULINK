import { useCallback, useEffect, useMemo, useState } from 'react';

import {
    deleteSavedThemeCourse,
    getThemeCourseBookmarkStatus,
    saveThemeCourse,
} from '../api/themeCourseApi';

/**
 * 테마 목록과 상세 화면이 같은 서버 저장 상태를 사용하도록 묶은 공통 훅입니다.
 * localStorage는 사용하지 않고 TRAVEL_COURSES 저장 결과를 화면의 기준으로 삼습니다.
 */
export default function useThemeCourseBookmarks(
    memberId,
    { enabled = true, courses = [] } = {},
) {
    const [savedCourses, setSavedCourses] = useState([]);
    const [pendingKeys, setPendingKeys] = useState([]);
    const [status, setStatus] = useState(
        enabled && memberId ? 'loading' : 'idle',
    );
    const [error, setError] = useState(null);

    useEffect(() => {
        const sourceCourseKeys = [
            ...new Set(courses.map((course) => course.sourceCourseKey).filter(Boolean)),
        ];

        if (!enabled || !memberId || sourceCourseKeys.length === 0) {
            return undefined;
        }

        const controller = new AbortController();

        Promise.all(sourceCourseKeys.map(
            (sourceCourseKey) => getThemeCourseBookmarkStatus(
                memberId,
                sourceCourseKey,
                { signal: controller.signal },
            ),
        ))
            .then((responses) => {
                setSavedCourses(responses
                    .filter((response) => response?.saved)
                    .map((response) => ({
                        ...response,
                        savedCourseId: response.courseId,
                    })));
                setStatus('success');
            })
            .catch((requestError) => {
                if (requestError?.name === 'AbortError') return;

                setError(requestError);
                setStatus('error');
            });

        return () => controller.abort();
    }, [courses, enabled, memberId]);

    const savedByKey = useMemo(
        () => new Map(
            savedCourses.map((course) => [course.sourceCourseKey, course]),
        ),
        [savedCourses],
    );

    const isSaved = useCallback(
        (sourceCourseKey) => savedByKey.has(sourceCourseKey),
        [savedByKey],
    );

    const isBusy = useCallback(
        (sourceCourseKey) => pendingKeys.includes(sourceCourseKey),
        [pendingKeys],
    );

    const toggle = useCallback(async (course) => {
        const sourceCourseKey = course?.sourceCourseKey;

        if (!sourceCourseKey || !memberId || pendingKeys.includes(sourceCourseKey)) {
            return savedByKey.has(sourceCourseKey);
        }

        setPendingKeys((keys) => [...keys, sourceCourseKey]);
        setError(null);

        try {
            if (savedByKey.has(sourceCourseKey)) {
                await deleteSavedThemeCourse(memberId, sourceCourseKey);
                setSavedCourses((courses) => courses.filter(
                    (savedCourse) => savedCourse.sourceCourseKey !== sourceCourseKey,
                ));
                return false;
            }

            const response = await saveThemeCourse(course, memberId);
            const savedCourseId = Number(
                response?.savedCourseId
                ?? response?.courseId
                ?? response?.id,
            );

            setSavedCourses((courses) => [
                ...courses.filter(
                    (savedCourse) => savedCourse.sourceCourseKey !== sourceCourseKey,
                ),
                {
                    ...response,
                    sourceCourseKey,
                    savedCourseId: Number.isInteger(savedCourseId) && savedCourseId > 0
                        ? savedCourseId
                        : null,
                    saved: true,
                },
            ]);
            return true;
        } catch (requestError) {
            setError(requestError);
            throw requestError;
        } finally {
            setPendingKeys((keys) => keys.filter(
                (key) => key !== sourceCourseKey,
            ));
        }
    }, [memberId, pendingKeys, savedByKey]);

    return {
        savedCourses,
        savedByKey,
        status,
        error,
        isSaved,
        isBusy,
        toggle,
    };
}

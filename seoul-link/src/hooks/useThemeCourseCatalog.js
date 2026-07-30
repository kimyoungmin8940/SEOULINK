import { useEffect, useMemo, useState } from 'react';

import { getThemePlacesByNames } from '../api/placeApi';
import {
    getThemeCoursePlaceNames,
    getThemeCourses,
    hydrateThemeCoursesWithPlaces,
    themeCourses,
} from '../data/themeCourseData';

/** 테마 코스 구성에 PLACES의 최신 장소·좌표·사진 데이터를 결합합니다. */
export default function useThemeCourseCatalog(themeSlug) {
    const baseCourses = useMemo(
        () => (themeSlug === 'all' ? themeCourses : getThemeCourses(themeSlug)),
        [themeSlug],
    );
    const [courses, setCourses] = useState(baseCourses);
    const [status, setStatus] = useState(baseCourses.length ? 'loading' : 'not-found');
    const [error, setError] = useState(null);
    const [missingPlaceNames, setMissingPlaceNames] = useState([]);

    useEffect(() => {
        if (!baseCourses.length) {
            return undefined;
        }

        const controller = new AbortController();

        getThemePlacesByNames(
            getThemeCoursePlaceNames(baseCourses),
            { signal: controller.signal },
        )
            .then((places) => {
                const hydrated = hydrateThemeCoursesWithPlaces(baseCourses, places);
                setCourses(hydrated.courses);
                setMissingPlaceNames(hydrated.missingPlaceNames);
                setStatus('success');
            })
            .catch((requestError) => {
                if (requestError?.name === 'AbortError') return;
                setError(requestError);
                setStatus('error');
            });

        return () => controller.abort();
    }, [baseCourses]);

    return {
        courses,
        status,
        error,
        missingPlaceNames,
    };
}

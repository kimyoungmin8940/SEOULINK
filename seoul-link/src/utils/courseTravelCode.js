const COURSE_TRAVEL_CODE_STORAGE_KEY = 'seoulinkCourseTravelCodes';

function safelyParse(value) {
    if (!value || typeof value !== 'string') return null;

    try {
        return JSON.parse(value);
    } catch {
        return null;
    }
}

/** 취향 검사 코드의 다섯 자리 구성을 검증하고 대문자로 통일합니다. */
export function normalizeTravelCode(value) {
    const normalized = String(value || '').trim().toUpperCase();
    return /^[AH][TM][LB][SD][PR]$/.test(normalized) ? normalized : null;
}

/** 상세 API가 travelCode를 반환하지 않는 경우를 대비해 저장한 courseId와 취향 코드를 함께 기억합니다. */
export function rememberCourseTravelCode(courseId, travelCode) {
    const normalizedCourseId = Number(courseId);
    const normalizedTravelCode = normalizeTravelCode(travelCode);

    if (
        typeof window === 'undefined'
        || !Number.isInteger(normalizedCourseId)
        || normalizedCourseId < 1
        || !normalizedTravelCode
    ) return;

    try {
        const storedMap = safelyParse(
            window.localStorage.getItem(COURSE_TRAVEL_CODE_STORAGE_KEY),
        );
        const nextMap = storedMap && typeof storedMap === 'object' ? storedMap : {};

        nextMap[normalizedCourseId] = {
            travelCode: normalizedTravelCode,
            savedAt: Date.now(),
        };

        const trimmedEntries = Object.entries(nextMap)
            .sort(([, first], [, second]) => Number(second?.savedAt) - Number(first?.savedAt))
            .slice(0, 100);

        window.localStorage.setItem(
            COURSE_TRAVEL_CODE_STORAGE_KEY,
            JSON.stringify(Object.fromEntries(trimmedEntries)),
        );
    } catch {
        // 저장소가 차단돼도 코스 저장 자체는 정상적으로 끝나게 둡니다.
    }
}

/** 저장된 courseId에 대응하는 취향 코드를 상세 화면에서 다시 읽습니다. */
export function getRememberedCourseTravelCode(courseId) {
    const normalizedCourseId = Number(courseId);

    if (
        typeof window === 'undefined'
        || !Number.isInteger(normalizedCourseId)
        || normalizedCourseId < 1
    ) return null;

    try {
        const storedMap = safelyParse(
            window.localStorage.getItem(COURSE_TRAVEL_CODE_STORAGE_KEY),
        );

        return normalizeTravelCode(storedMap?.[normalizedCourseId]?.travelCode);
    } catch {
        return null;
    }
}


const CURRENT_TRAVEL_CODE_OBJECT_KEYS = [
    'seoulinkSurveyResult',
    'surveyResult',
    'travelTypeResult',
    'seoulinkCourseRecommendationResponse',
    'courseRecommendationResponse',
];

const CURRENT_TRAVEL_CODE_VALUE_KEYS = [
    'travelCode',
    'typeCode',
    'preferenceCode',
];

function findTravelCode(value) {
    if (!value || typeof value !== 'object') return null;

    const candidates = [
        value.travelCode,
        value.typeCode,
        value.preferenceCode,
        value.surveyTypeCode,
        value.result?.travelCode,
        value.result?.typeCode,
        value.data?.travelCode,
        value.data?.typeCode,
    ];

    return candidates.map(normalizeTravelCode).find(Boolean) || null;
}

/**
 * 현재 탭에 남아 있는 최신 취향 검사 코드를 찾습니다.
 * 상세 API·목록 요약에 코드가 없거나 UI 미리보기를 열었을 때도 표시가 사라지지 않게 사용합니다.
 */
export function getCurrentTravelCode(fallbackValue = null) {
    if (typeof window === 'undefined') {
        return normalizeTravelCode(fallbackValue);
    }

    const storages = [window.sessionStorage, window.localStorage];

    for (const storage of storages) {
        try {
            for (const key of CURRENT_TRAVEL_CODE_OBJECT_KEYS) {
                const code = findTravelCode(safelyParse(storage.getItem(key)));
                if (code) return code;
            }

            for (const key of CURRENT_TRAVEL_CODE_VALUE_KEYS) {
                const code = normalizeTravelCode(storage.getItem(key));
                if (code) return code;
            }
        } catch {
            // 브라우저 저장소 접근이 차단되면 다음 후보 또는 fallback을 사용합니다.
        }
    }

    return normalizeTravelCode(fallbackValue);
}

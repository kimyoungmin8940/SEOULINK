import { useCallback, useEffect, useMemo, useState } from 'react';
import {
    ArrowLeft,
    Check,
    GitCompareArrows,
    Info,
    Lightbulb,
    RefreshCw,
    Sparkles,
    Star,
    X,
} from 'lucide-react';

import Header from '../../components/common/Header';
import Footer from '../../components/common/Footer';
import CourseRecommendationCard from '../../components/course/CourseRecommendationCard';
import CourseMapPreview from '../../components/course/CourseMapPreview';
import { recommendCourse, saveCourse } from '../../api/courseApi';
import {
    buildCourseRecommendAgainRequest,
    COURSE_RECOMMEND_REQUEST_KEY,
    COURSE_RECOMMEND_RESPONSE_KEY,
} from '../../utils/courseRecommendationHandoff';
import {
    readRecommendedCourseCache,
    writeRecommendedCourseCache,
} from '../../utils/courseHistory';
import recommendationPreview from '../../mocks/courseRecommendation.json';
import heroSeoulImage from '../../assets/images/hero-seoul-main.png';
import hanokImage from '../../assets/images/moods/mood-hanok-photo.png';
import walkingImage from '../../assets/images/moods/mood-walking-alley.png';
import sunsetImage from '../../assets/images/moods/mood-sunset-seoul.png';

const fallbackImages = [hanokImage, walkingImage, sunsetImage];
// 개발 모드의 중복 effect나 빠른 재시도로 같은 추천 요청이 겹치지 않도록 Promise를 재사용합니다.
const recommendationPromiseCache = new Map();

const travelTypeDimensions = [
    {
        options: {
            A: { label: '활동형', tone: 'blue' },
            H: { label: '휴식형', tone: 'blue' },
        },
    },
    {
        options: {
            T: { label: '역사형', tone: 'purple' },
            M: { label: '현대형', tone: 'purple' },
        },
    },
    {
        options: {
            L: { label: '럭셔리형', tone: 'green' },
            B: { label: '가성비형', tone: 'green' },
        },
    },
    {
        options: {
            S: { label: '안정형', tone: 'orange' },
            D: { label: '도전형', tone: 'orange' },
        },
    },
    {
        options: {
            P: { label: '빽빽한 일정형', tone: 'pink' },
            R: { label: '여유 일정형', tone: 'pink' },
        },
    },
];

/** 손상된 브라우저 저장값이 있어도 추천 화면 자체는 계속 열리도록 안전하게 파싱합니다. */
function safelyParse(value) {
    if (!value || typeof value !== 'string') {
        return null;
    }

    try {
        return JSON.parse(value);
    } catch {
        return null;
    }
}

/** 화면 간 전달 키가 통합되기 전의 구버전 키까지 세션→로컬 순서로 탐색합니다. */
function readStoredObject(keys) {
    for (const storage of [sessionStorage, localStorage]) {
        for (const key of keys) {
            const parsed = safelyParse(storage.getItem(key));

            if (parsed) {
                return parsed;
            }
        }
    }

    return null;
}

/** 현재의 3개 옵션 응답과 이전 단일 코스 응답을 화면 공통 구조로 맞춥니다. */
function normalizeRecommendationResponse(data) {
    if (Array.isArray(data?.courseOptions)) {
        return data;
    }

    // 이전 백엔드 응답(단일 저장 코스)도 화면에서 한 개 옵션으로 볼 수 있게 유지합니다.
    if (Array.isArray(data?.days)) {
        return {
            resultId: data.resultId ?? null,
            travelCode: data.travelCode ?? null,
            courseOptions: [
                {
                    optionNo: 1,
                    optionType: 'BALANCED',
                    optionName: '균형 추천 코스',
                    title: data.title,
                    description: data.description,
                    totalDistanceKm: data.totalDistanceKm,
                    totalTravelTimeMinutes: data.totalTravelTimeMinutes,
                    totalVisitTimeMinutes: data.totalVisitTimeMinutes,
                    totalCourseTimeMinutes: data.totalCourseTimeMinutes,
                    days: data.days,
                },
            ],
        };
    }

    return null;
}

/** 동일 요청이 진행 중이면 기존 Promise를 반환하고, 실패한 요청만 캐시에서 제거합니다. */
function requestRecommendationOnce(request) {
    const cacheKey = JSON.stringify(request);

    if (!recommendationPromiseCache.has(cacheKey)) {
        const pendingRequest = recommendCourse(request).catch((error) => {
            recommendationPromiseCache.delete(cacheKey);
            throw error;
        });
        recommendationPromiseCache.set(cacheKey, pendingRequest);
    }

    return recommendationPromiseCache.get(cacheKey);
}

/** 전달된 응답, 저장 응답, 새 요청, UI 미리보기 순서로 최초 화면 상태를 결정합니다. */
function getInitialRecommendationState() {
    const navigationResponse = normalizeRecommendationResponse(
        window.history.state?.courseRecommendResponse,
    );
    const storedResponse = normalizeRecommendationResponse(
        readStoredObject([
            COURSE_RECOMMEND_RESPONSE_KEY,
            'courseRecommendResponse',
            'courseRecommendationResponse',
        ]),
    );
    const navigationRequest = window.history.state?.courseRecommendRequest || null;
    const storedRequest = readStoredObject([
            COURSE_RECOMMEND_REQUEST_KEY,
            'courseRecommendRequest',
            'courseRecommendationRequest',
        ]);
    const request = navigationRequest || storedRequest;

    if (navigationResponse) {
        return {
            response: navigationResponse,
            request,
            source: 'api',
            status: 'success',
        };
    }

    // 앞 단계가 새 요청을 직접 전달했다면 이전 세션 결과보다 새 요청을 우선합니다.
    if (navigationRequest) {
        return { response: null, request: navigationRequest, source: 'api', status: 'loading' };
    }

    const responseMatchesRequest = !storedRequest
        || !storedResponse?.resultId
        || !storedRequest?.resultId
        || storedResponse.resultId === storedRequest.resultId;

    if (storedResponse && responseMatchesRequest) {
        return {
            response: storedResponse,
            request: storedRequest,
            source: 'api',
            status: 'success',
        };
    }

    if (request) {
        return { response: null, request, source: 'api', status: 'loading' };
    }

    return {
        response: recommendationPreview,
        request: null,
        source: 'preview',
        status: 'success',
    };
}

/** 로그인 모듈 통합 전 memberId 확인용으로 JWT payload만 안전하게 읽습니다. */
function decodeTokenPayload(token) {
    try {
        const payload = token?.split('.')[1];
        if (!payload) return null;

        const normalized = payload.replace(/-/g, '+').replace(/_/g, '/');
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

/** 여러 로그인 저장 형식을 임시 지원해 저장 요청에 필요한 회원 정보를 찾습니다. */
function getUserProfile() {
    // 회원 API가 준비되기 전까지 로그인 담당 화면이 저장한 브라우저 값만 사용합니다.
    const storedUser = readStoredObject(['user', 'member', 'loginUser']) || {};
    const tokenPayload = decodeTokenPayload(localStorage.getItem('accessToken')) || {};
    const directName = localStorage.getItem('nickname')
        || localStorage.getItem('userName')
        || localStorage.getItem('memberName')
        || localStorage.getItem('name');
    const memberId = Number(
        storedUser.memberId
        ?? storedUser.id
        ?? tokenPayload.memberId
        ?? tokenPayload.userId
        ?? localStorage.getItem('memberId'),
    );

    return {
        name: directName
            || storedUser.nickname
            || storedUser.userName
            || storedUser.memberName
            || storedUser.name
            || '회원',
        memberId: Number.isInteger(memberId) && memberId > 0 ? memberId : null,
    };
}

/** 추천 응답을 우선하고 설문 저장값을 보조로 사용해 5자리 여행 유형을 결정합니다. */
function getStoredTravelCode(response) {
    const surveyResult = readStoredObject([
        'seoulinkSurveyResult',
        'surveyResult',
        'travelTypeResult',
    ]) || {};
    const code = response?.travelCode
        || surveyResult.travelCode
        || surveyResult.typeCode
        || localStorage.getItem('travelCode')
        || sessionStorage.getItem('travelCode')
        || recommendationPreview.travelCode;

    return /^[A-Z]{5}$/.test(code || '') ? code : 'ATBSP';
}

/** 5자리 여행 유형 코드를 화면에 표시할 성향 배지 다섯 개로 변환합니다. */
function getTravelTypeBadges(travelCode) {
    return travelTypeDimensions.map(({ options }, index) => {
        const letter = travelCode[index];
        const metadata = options[letter] || { label: '맞춤형', tone: 'blue' };
        return { letter, ...metadata };
    });
}

function formatMinutes(value) {
    const minutes = Math.max(0, Math.round(Number(value) || 0));
    const hours = Math.floor(minutes / 60);
    const rest = minutes % 60;

    if (hours === 0) return `${rest}분`;
    return rest === 0 ? `${hours}시간` : `${hours}시간 ${rest}분`;
}

function getOptionPlaceCount(option) {
    return (option?.days || []).reduce(
        (sum, day) => sum + (Array.isArray(day?.places) ? day.places.length : 0),
        0,
    );
}

/** 사용자가 고른 옵션에서 COURSE와 COURSE_DETAILS 저장에 필요한 값만 추립니다. */
function buildSaveRequest(option, response, profile, travelCode) {
    const places = (option.days || []).flatMap((day) => (
        (day.places || []).map((place, index) => ({
            placeId: place.placeId,
            visitDate: place.visitDate || day.visitDate,
            visitOrder: place.visitOrder ?? index + 1,
            visitTime: place.visitTime || place.expectedVisitTimeHHmm || null,
            expectedVisitMinutes: Number(place.expectedVisitMinutes) || 0,
            distanceFromPreviousKm: Number(place.distanceFromPreviousKm) || 0,
            travelTimeFromPreviousMinutes: Number(place.travelTimeFromPreviousMinutes) || 0,
        }))
    ));

    return {
        ...(profile.memberId ? { memberId: profile.memberId } : {}),
        ...(response?.resultId ? { resultId: response.resultId } : {}),
        title: option.title || option.optionName || '서울 맞춤 추천 코스',
        description: option.description || `${option.optionName || '맞춤'} 방식으로 구성한 서울 여행 코스`,
        travelCode,
        courseType: 'SURVEY',
        region: '서울',
        publicCourse: false,
        places,
    };
}

/** 서버 목록에 아직 없는 이미지·태그를 상세/목록 화면에서 보완할 로컬 요약을 저장합니다. */
function storeRecommendedCourseSummary(option, savedCourse) {
    if (!savedCourse?.courseId) return;

    const places = (option.days || []).flatMap((day) => day.places || []);
    const themeFields = [
        ['themePalaceCultureYn', '역사·문화'],
        ['themeNatureHangangYn', '자연·한강'],
        ['themeDateYn', '데이트'],
        ['themeFoodTourYn', '맛집탐방'],
        ['themeCafeTourYn', '카페투어'],
        ['themeShoppingHotplaceYn', '쇼핑·핫플'],
        ['themeNightViewYn', '야경'],
        ['themeHotelStayYn', '숙소'],
    ];
    const tags = themeFields
        .filter(([field]) => places.some((place) => place?.[field] === 'Y'))
        .map(([, label]) => label)
        .slice(0, 4);
    const coverImageUrl = option.coverImageUrl
        || places.find((place) => place.imageUrl)?.imageUrl
        || fallbackImages[0];
    const previousCourses = readRecommendedCourseCache();
    const summary = {
        courseId: savedCourse.courseId,
        title: savedCourse.title || option.title || option.optionName,
        description: option.description || '취향 검사 결과를 바탕으로 추천된 서울 여행 코스입니다.',
        coverImageUrl,
        imageUrl: coverImageUrl,
        regions: ['서울'],
        area: '서울',
        tags: tags.length > 0 ? tags : ['추천코스', '취향맞춤'],
        placeCount: savedCourse.placeCount ?? getOptionPlaceCount(option),
        dayCount: savedCourse.dayCount ?? option.days?.length ?? 1,
        totalDistanceKm: savedCourse.totalDistanceKm ?? option.totalDistanceKm,
        totalCourseTimeMinutes: savedCourse.totalCourseTimeMinutes
            ?? option.totalCourseTimeMinutes,
        duration: `약 ${formatMinutes(
            savedCourse.totalCourseTimeMinutes ?? option.totalCourseTimeMinutes,
        )}`,
        liked: false,
    };
    const nextCourses = [
        summary,
        ...(Array.isArray(previousCourses) ? previousCourses : [])
            .filter((course) => course?.courseId !== summary.courseId),
    ].slice(0, 50);

    writeRecommendedCourseCache(nextCourses);
}

/** 추천 API 응답을 기다리는 동안 세 개 옵션의 자리와 비슷한 스켈레톤을 표시합니다. */
function LoadingCards() {
    return (
        <div className="course-result-loading" aria-label="추천 코스를 불러오는 중">
            {[1, 2, 3].map((item) => (
                <div className="course-result-loading-card" key={item}>
                    <span className="course-result-loading-image" />
                    <span className="course-result-loading-lines">
                        <i />
                        <i />
                        <i />
                    </span>
                </div>
            ))}
        </div>
    );
}

/** 최대 세 개 코스의 거리·시간·장소 수를 나란히 확인하는 비교 영역입니다. */
function ComparisonPanel({ options, selectedOptionNos, onRemove, onReset }) {
    const selectedOptions = options.filter((option) => selectedOptionNos.includes(option.optionNo));

    return (
        <section className="course-result-compare-card" id="course-comparison-panel">
            <div className="course-result-side-heading">
                <div>
                    <span className="course-result-side-icon"><GitCompareArrows size={17} aria-hidden="true" /></span>
                    <h2>코스 비교</h2>
                </div>
                {selectedOptions.length > 0 && (
                    <button type="button" onClick={onReset}>초기화</button>
                )}
            </div>

            {selectedOptions.length === 0 ? (
                <div className="course-result-compare-empty">
                    <GitCompareArrows size={25} strokeWidth={1.7} aria-hidden="true" />
                    <p>비교할 코스를 담아보세요.</p>
                    <small>거리와 예상 시간을 한눈에 볼 수 있어요.</small>
                </div>
            ) : (
                <div className="course-result-compare-list">
                    {selectedOptions.map((option) => (
                        <div className="course-result-compare-item" key={option.optionNo}>
                            <span>{option.optionName}</span>
                            <button type="button" aria-label={`${option.optionName} 비교에서 제거`} onClick={() => onRemove(option.optionNo)}>
                                <X size={14} aria-hidden="true" />
                            </button>
                            <strong>{Number(option.totalDistanceKm || 0).toFixed(1)}km</strong>
                            <small>{formatMinutes(option.totalCourseTimeMinutes)} · {getOptionPlaceCount(option)}곳</small>
                        </div>
                    ))}
                </div>
            )}

            <p className="course-result-compare-guide">
                {selectedOptions.length < 2
                    ? '두 개 이상 담으면 차이를 더 쉽게 비교할 수 있어요.'
                    : `${selectedOptions.length}개 코스를 비교 중이에요.`}
            </p>
        </section>
    );
}

/** 취향 기반 3개 코스를 조회·비교하고, 사용자가 선택한 한 코스를 저장하는 결과 화면입니다. */
function CourseRecommendPage() {
    const [initialState] = useState(getInitialRecommendationState);
    const [response, setResponse] = useState(initialState.response);
    const [status, setStatus] = useState(initialState.status);
    const [source, setSource] = useState(initialState.source);
    const [requestError, setRequestError] = useState('');
    const [comparedOptionNos, setComparedOptionNos] = useState([]);
    const [focusedOptionNo, setFocusedOptionNo] = useState(null);
    const [savingOptionNo, setSavingOptionNo] = useState(null);
    const [savedOptionNo, setSavedOptionNo] = useState(null);
    const [savedCourseId, setSavedCourseId] = useState(null);
    const [notice, setNotice] = useState(null);
    const [recommendRequest, setRecommendRequest] = useState(initialState.request);
    const [isRecommendingAgain, setIsRecommendingAgain] = useState(false);
    const profile = useMemo(() => getUserProfile(), []);
    const options = Array.isArray(response?.courseOptions) ? response.courseOptions : [];
    const travelCode = getStoredTravelCode(response);
    const travelBadges = getTravelTypeBadges(travelCode);
    const focusedOption = options.find((option) => option.optionNo === focusedOptionNo) || options[0];

    const requestRecommendation = useCallback(async () => {
        const request = recommendRequest
            || window.history.state?.courseRecommendRequest
            || readStoredObject([COURSE_RECOMMEND_REQUEST_KEY, 'courseRecommendRequest']);

        if (!request) {
            setResponse(recommendationPreview);
            setSource('preview');
            setStatus('success');
            setRequestError('');
            return;
        }

        setStatus('loading');
        setRequestError('');

        try {
            const data = normalizeRecommendationResponse(await requestRecommendationOnce(request));

            if (!data) {
                throw new Error('추천 결과 형식을 확인할 수 없습니다.');
            }

            sessionStorage.setItem(COURSE_RECOMMEND_RESPONSE_KEY, JSON.stringify(data));
            setResponse(data);
            setSource('api');
            setFocusedOptionNo(data.courseOptions[0]?.optionNo ?? null);
            setStatus(data.courseOptions.length > 0 ? 'success' : 'empty');
        } catch (error) {
            setStatus('error');
            setRequestError(error?.message || '추천 코스를 불러오지 못했습니다.');
        }
    }, [recommendRequest]);

    // 추천 요청 데이터가 앞 단계에서 전달된 경우에만 백엔드 추천 API를 실행합니다.
    useEffect(() => {
        if (initialState.status !== 'loading' || status !== 'loading') {
            return undefined;
        }

        let cancelled = false;
        queueMicrotask(() => {
            if (!cancelled) {
                requestRecommendation();
            }
        });

        return () => {
            cancelled = true;
        };
    }, [initialState.status, requestRecommendation, status]);

    const toggleCompare = (optionNo) => {
        // 비교 패널의 화면 밀도를 위해 최근 선택한 세 옵션까지만 유지합니다.
        setComparedOptionNos((previous) => (
            previous.includes(optionNo)
                ? previous.filter((currentOptionNo) => currentOptionNo !== optionNo)
                : [...previous, optionNo].slice(-3)
        ));
    };

    const handleRecommendAgain = async () => {
        const request = recommendRequest
            || window.history.state?.courseRecommendRequest
            || readStoredObject([COURSE_RECOMMEND_REQUEST_KEY, 'courseRecommendRequest']);

        if (!request || source === 'preview') {
            setNotice({
                tone: 'info',
                message: '현재는 화면 미리보기입니다. 취향 검사 결과와 장소 후보가 전달되면 같은 취향으로 다른 코스를 다시 추천합니다.',
            });
            return;
        }

        // 현재 세 조합을 제외 목록에 추가하되 설문·날짜·후보 풀은 그대로 재사용합니다.
        let nextRequest;
        try {
            nextRequest = buildCourseRecommendAgainRequest(request, options);
        } catch (error) {
            setNotice({
                tone: 'error',
                message: error?.message || '재추천 요청을 만들지 못했습니다.',
            });
            return;
        }

        setIsRecommendingAgain(true);
        setNotice(null);

        try {
            const data = normalizeRecommendationResponse(
                await requestRecommendationOnce(nextRequest),
            );

            if (!data || data.courseOptions.length === 0) {
                throw new Error('새로운 추천 결과 형식을 확인할 수 없습니다.');
            }

            sessionStorage.setItem(COURSE_RECOMMEND_REQUEST_KEY, JSON.stringify(nextRequest));
            sessionStorage.setItem(COURSE_RECOMMEND_RESPONSE_KEY, JSON.stringify(data));
            setRecommendRequest(nextRequest);
            setResponse(data);
            setSource('api');
            setStatus('success');
            setRequestError('');
            setComparedOptionNos([]);
            setFocusedOptionNo(data.courseOptions[0]?.optionNo ?? null);
            setSavedOptionNo(null);
            setSavedCourseId(null);
            setNotice({
                tone: 'success',
                message: '같은 취향을 바탕으로 다른 추천 코스를 새로 준비했어요.',
            });
        } catch (error) {
            setNotice({
                tone: 'error',
                message: error?.message || '다른 추천 코스를 만들지 못했습니다. 잠시 후 다시 시도해주세요.',
            });
        } finally {
            setIsRecommendingAgain(false);
        }
    };

    const handleSave = async (option) => {
        if (source === 'preview') {
            setNotice({
                tone: 'info',
                message: '현재는 화면 미리보기입니다. 실제 추천 응답이 들어오면 선택한 코스만 백엔드에 저장됩니다.',
            });
            return;
        }

        // 현재 CourseSaveService는 인증 토큰과 별개로 memberId를 필수 검증합니다.
        if (!profile.memberId) {
            setNotice({
                tone: 'error',
                message: '회원 ID를 확인할 수 없습니다. 로그인 성공 시 memberId를 사용자 정보와 함께 저장해주세요.',
            });
            return;
        }

        setSavingOptionNo(option.optionNo);
        setNotice(null);

        try {
            // 추천 옵션 전체를 저장하지 않고 사용자가 확정한 한 옵션만 서버에 저장합니다.
            const savedCourse = await saveCourse(
                buildSaveRequest(option, response, profile, travelCode),
            );
            storeRecommendedCourseSummary(option, savedCourse);
            setSavedOptionNo(option.optionNo);
            setSavedCourseId(savedCourse?.courseId ?? null);
            setNotice({
                tone: 'success',
                message: '선택한 코스를 내 코스에 담았습니다.',
            });
        } catch (error) {
            setNotice({
                tone: 'error',
                message: error?.message || '코스를 저장하지 못했습니다. 잠시 후 다시 시도해주세요.',
            });
        } finally {
            setSavingOptionNo(null);
        }
    };

    const scrollToComparison = () => {
        document.getElementById('course-comparison-panel')?.scrollIntoView({
            behavior: 'smooth',
            block: 'center',
        });
    };

    return (
        <div className="page course-result-page">
            <Header variant="default" />

            <main className="course-result-shell">
                <section className="course-result-hero">
                    <div className="course-result-hero-copy">
                        <a className="course-result-back-btn" href="/survey/result" aria-label="취향 분석 결과로 돌아가기">
                            <ArrowLeft size={20} strokeWidth={2.1} aria-hidden="true" />
                        </a>

                        <div>
                            <p className="course-result-eyebrow"><Sparkles size={14} aria-hidden="true" /> 맞춤 추천 완료</p>
                            <h1><strong>{travelCode}</strong> {profile.name}님을 위한 맞춤 코스 추천</h1>
                            <p>여행 정보와 취향 분석 결과를 바탕으로 세 가지 서울 여행 코스를 준비했어요.</p>

                            <div className="course-result-type-badges" aria-label={`여행 유형 ${travelCode}`}>
                                {travelBadges.map((badge) => (
                                    <span className={badge.tone} key={`${badge.letter}-${badge.label}`}>
                                        <b>{badge.letter}</b>
                                        {badge.label}
                                    </span>
                                ))}
                            </div>
                        </div>
                    </div>

                    <div className="course-result-hero-visual" aria-hidden="true">
                        <img src={heroSeoulImage} alt="" />
                        <span className="course-result-hero-orbit orbit-one" />
                        <span className="course-result-hero-orbit orbit-two" />
                        <span className="course-result-hero-pin"><Star size={18} fill="currentColor" /></span>
                    </div>
                </section>

                <div className="course-result-toolbar">
                    <div className="course-result-tabs">
                        <a className="active" href="/courses"><Star size={18} fill="currentColor" aria-hidden="true" />추천 코스</a>
                        <button
                            type="button"
                            onClick={handleRecommendAgain}
                            disabled={isRecommendingAgain || status === 'loading'}
                            aria-label="같은 취향 검사 결과로 다른 코스 다시 추천받기"
                        >
                            <RefreshCw
                                className={isRecommendingAgain ? 'is-spinning' : undefined}
                                size={18}
                                aria-hidden="true"
                            />
                            {isRecommendingAgain ? '추천 만드는 중' : '다시 추천받기'}
                        </button>
                    </div>

                    <div className="course-result-toolbar-actions">
                        {source === 'preview' && (
                            <span className="course-result-preview-label"><Info size={14} aria-hidden="true" />UI 미리보기</span>
                        )}
                        <button type="button" onClick={scrollToComparison}>
                            <GitCompareArrows size={17} aria-hidden="true" />
                            코스 비교
                            <b>{comparedOptionNos.length}</b>
                        </button>
                    </div>
                </div>

                {status === 'loading' && <LoadingCards />}

                {status === 'error' && (
                    <section className="course-result-state-card" role="alert">
                        <span className="error"><Info size={24} aria-hidden="true" /></span>
                        <h2>추천 코스를 불러오지 못했어요</h2>
                        <p>{requestError}</p>
                        <div>
                            <button type="button" onClick={requestRecommendation}>
                                <RefreshCw size={16} aria-hidden="true" />다시 불러오기
                            </button>
                            <button
                                className="secondary"
                                type="button"
                                onClick={() => {
                                    setResponse(recommendationPreview);
                                    setSource('preview');
                                    setStatus('success');
                                }}
                            >
                                화면 미리보기
                            </button>
                        </div>
                    </section>
                )}

                {status === 'empty' && (
                    <section className="course-result-state-card">
                        <span><Sparkles size={24} aria-hidden="true" /></span>
                        <h2>조건에 맞는 추천 코스가 아직 없어요</h2>
                        <p>여행 정보나 취향 답변을 조금 바꾸면 새로운 코스를 추천받을 수 있어요.</p>
                        <a href="/travel-info">여행 정보 다시 입력하기</a>
                    </section>
                )}

                {status === 'success' && options.length > 0 && (
                    <div className="course-result-layout">
                        <section className="course-result-list" aria-label="맞춤 추천 코스 목록">
                            {options.map((option, index) => (
                                <CourseRecommendationCard
                                    option={option}
                                    fallbackImage={fallbackImages[index % fallbackImages.length]}
                                    isCompared={comparedOptionNos.includes(option.optionNo)}
                                    isSaving={savingOptionNo === option.optionNo}
                                    isSaved={savedOptionNo === option.optionNo}
                                    onToggleCompare={toggleCompare}
                                    onSave={handleSave}
                                    onFocusOption={(nextOption) => setFocusedOptionNo(nextOption.optionNo)}
                                    key={option.optionNo ?? option.optionType}
                                />
                            ))}
                        </section>

                        <aside className="course-result-sidebar">
                            <CourseMapPreview option={focusedOption} />

                            <ComparisonPanel
                                options={options}
                                selectedOptionNos={comparedOptionNos}
                                onRemove={toggleCompare}
                                onReset={() => setComparedOptionNos([])}
                            />

                            <section className="course-result-tip-card">
                                <span className="course-result-tip-icon"><Lightbulb size={19} aria-hidden="true" /></span>
                                <div>
                                    <h2>코스 TIP</h2>
                                    <p>
                                        {travelCode.endsWith('P')
                                            ? '빽빽한 일정형 성향을 고려해 볼거리를 알차게 담았어요. 이동이 부담되면 이동 최소 코스를 골라보세요.'
                                            : '여유 일정형 성향을 고려해 장소별 체류 시간을 넉넉하게 잡았어요.'}
                                    </p>
                                </div>
                            </section>
                        </aside>
                    </div>
                )}
            </main>

            <Footer />

            {notice && (
                <div className={`course-result-toast ${notice.tone}`} role="status">
                    <span>{notice.tone === 'success' ? <Check size={18} aria-hidden="true" /> : <Info size={18} aria-hidden="true" />}</span>
                    <p>{notice.message}</p>
                    {savedCourseId && (
                        <a href="/courses/recommendations">추천 코스 목록 보기</a>
                    )}
                    <button type="button" aria-label="알림 닫기" onClick={() => setNotice(null)}>
                        <X size={16} aria-hidden="true" />
                    </button>
                </div>
            )}
        </div>
    );
}

export default CourseRecommendPage;

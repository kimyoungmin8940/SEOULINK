import { useEffect, useMemo, useState } from "react";
import {
    Bookmark,
    BriefcaseBusiness,
    CalendarDays,
    ChevronDown,
    ChevronLeft,
    ChevronRight,
    Clock3,
    CreditCard,
    Grid2X2,
    Landmark,
    List,
    MapPin,
    MessageCircle,
    Pencil,
    Plus,
    RefreshCw,
    Route,
    Search,
    Sparkles,
    Utensils,
    UserRound,
} from "lucide-react";

import Header from "../../components/common/Header";
import Footer from "../../components/common/Footer";
import MypageSidebar from "../../components/common/MypageSidebar";
import {
    getSavedRecommendedCourses,
    removeSavedRecommendedCourse,
} from "../../api/courseApi";
import { authStore } from "../../store/authStore";
import {
    getCurrentMemberId,
    normalizeMyCourseList,
} from "../../utils/courseHistory";

import "../../styles/mypage.css";
import "../../styles/savedCourses.css";
import { getCourseFallbackImage } from "../../utils/courseImage";

const PAGE_SIZE = 4;
const COURSE_DETAIL_ENTRY_KEY = "seoulinkCourseDetailEntry";

const menuItems = [
    {
        label: "내 여행 정보",
        path: "/mypage",
        Icon: BriefcaseBusiness,
    },
    {
        label: "저장한 추천 코스",
        path: "/mypage/courses",
        Icon: Bookmark,
    },
    {
        label: "직접 만든 코스",
        path: "/mypage/custom-courses",
        Icon: Route,
    },
    {
        label: "내가 쓴 후기와 댓글",
        path: "/mypage/reviews",
        Icon: MessageCircle,
    },
    {
        label: "취향 검사 결과",
        path: "/mypage/travel-type",
        Icon: Sparkles,
    },
    {
        label: "결제 내역",
        path: "/mypage/payments",
        Icon: CreditCard,
    },
];

const themeFilters = [
    {
        value: "ALL",
        label: "전체",
        keywords: [],
    },
    {
        value: "HISTORY",
        label: "역사·문화",
        keywords: ["역사", "문화", "궁궐", "한옥", "박물관"],
    },
    {
        value: "DATE",
        label: "데이트",
        keywords: ["데이트", "한강", "노을"],
    },
    {
        value: "CAFE",
        label: "감성·카페",
        keywords: ["감성", "카페", "성수"],
    },
    {
        value: "NIGHT",
        label: "야경·맛집",
        keywords: ["야경", "맛집", "미식", "남산", "명동"],
    },
];

function getUserName(member) {
    return (
        member?.nickname?.trim() ||
        member?.name?.trim() ||
        member?.email?.split("@")[0] ||
        "회원"
    );
}

function getCourseText(course) {
    return [
        course.title,
        course.description,
        course.region,
        course.representativePlaceName,
        course.representativeAddress,
    ]
        .filter(Boolean)
        .join(" ")
        .toLowerCase();
}

function getCourseTheme(course) {
    const text = getCourseText(course);

    return (
        themeFilters
            .slice(1)
            .find(({ keywords }) =>
                keywords.some((keyword) =>
                    text.includes(keyword.toLowerCase())
                )
            ) || {
            value: "SEOUL",
            label: "서울 추천",
        }
    );
}

function getCourseImageCandidates(course) {
    return [...new Set([
        ...(Array.isArray(course?.coverImageUrls)
            ? course.coverImageUrls
            : []),
        course?.thumbnailUrl,
        course?.coverImageUrl,
        course?.imageUrl,
        course?.representativeImageUrl,
        course?.placeImageUrl,
    ].filter((value) =>
        typeof value === "string" && value.trim().length > 0
    ))];
}



function SavedCourseCoverImage({ course }) {
    const imageCandidates = useMemo(
        () => getCourseImageCandidates(course),
        [course]
    );

    const fallbackImageUrl = useMemo(
        () => getCourseFallbackImage(course),
        [course]
    );

    const [imageIndex, setImageIndex] = useState(0);

    const currentImageUrl =
        imageCandidates[imageIndex] || fallbackImageUrl;

    const isUsingFallback =
        !imageCandidates[imageIndex];

    useEffect(() => {
        setImageIndex(0);
    }, [
        course?.courseId,
        course?.recommendationKey,
        course?.sourceCourseKey,
    ]);

    return (
        <img
            src={currentImageUrl}
            alt={`${course.title || "코스"} 대표 이미지`}
            onError={() => {
                if (!isUsingFallback) {
                    setImageIndex(
                        (currentIndex) => currentIndex + 1
                    );
                }
            }}
        />
    );
}

function rememberCourseDetailEntry(course) {
    sessionStorage.setItem(
        COURSE_DETAIL_ENTRY_KEY,
        JSON.stringify({
            detailId: course.courseId,
            returnPath: `${window.location.pathname}${window.location.search}`,
            summary: course,
        })
    );

    // 브라우저 뒤로가기 시 이전 저장 목록이 잠깐 보이지 않도록
    // 상세페이지로 이동하기 전에 현재 목록 화면을 숨긴 상태로 캐시합니다.
    document.documentElement.style.visibility = "hidden";
}

function formatDuration(totalMinutes, placeCount) {
    const minutes = Math.round(Number(totalMinutes) || 0);

    if (minutes <= 0) {
        return placeCount > 0
            ? `${placeCount}개 장소`
            : "일정 확인";
    }

    const hours = Math.floor(minutes / 60);
    const remainder = minutes % 60;

    if (hours === 0) {
        return `${remainder}분`;
    }

    return remainder > 0
        ? `${hours}시간 ${remainder}분`
        : `${hours}시간`;
}

function formatSavedDate(value) {
    if (!value) {
        return "저장일 없음";
    }

    const date = new Date(value);

    if (Number.isNaN(date.getTime())) {
        return value;
    }

    return new Intl.DateTimeFormat("ko-KR", {
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
    })
        .format(date)
        .replace(/\s/g, "");
}

function getRouteStops(course) {
    const descriptionStops = String(course.description || "")
        .split(/\s*(?:→|->)\s*/)
        .map((stop) => stop.trim())
        .filter((stop) => stop.length > 1 && stop.length < 24);

    const candidates = [
        ...descriptionStops,
        course.representativePlaceName,
        course.region ? `${course.region} 추천 장소` : null,
    ].filter(Boolean);

    return [...new Set(candidates)].slice(0, 4);
}

export default function MyCoursesPage() {
    const member = authStore.getMember() || {};
    const userName = getUserName(member);
    const memberId = member.memberId || getCurrentMemberId();

    const [courses, setCourses] = useState([]);
    const [isLoading, setIsLoading] = useState(Boolean(memberId));
    const [errorMessage, setErrorMessage] = useState(
        memberId ? "" : "로그인 후 저장한 추천 코스를 확인할 수 있습니다."
    );
    const [notice, setNotice] = useState("");
    const [searchQuery, setSearchQuery] = useState("");
    const [sortOrder, setSortOrder] = useState("RECENT");
    const [viewMode, setViewMode] = useState("LIST");
    const [currentPage, setCurrentPage] = useState(1);
    const [removingIds, setRemovingIds] = useState(() => new Set());

    useEffect(() => {
        let isMounted = true;

        const loadCourses = async () => {
            try {
                setIsLoading(true);
                setErrorMessage("");

                const data = await getSavedRecommendedCourses(memberId);
                const normalizedCourses = normalizeMyCourseList(data);

                if (isMounted) {
                    setCourses(normalizedCourses);
                }
            } catch (error) {
                if (isMounted) {
                    setErrorMessage(
                        error.message ||
                        "저장한 추천 코스를 불러오지 못했습니다."
                    );
                }
            } finally {
                if (isMounted) {
                    setIsLoading(false);
                }
            }
        };

        if (memberId) {
            loadCourses();
        }

        return () => {
            isMounted = false;
        };
    }, [memberId]);

    useEffect(() => {
        if (!notice) {
            return undefined;
        }

        const timer = window.setTimeout(
            () => setNotice(""),
            2400
        );

        return () => window.clearTimeout(timer);
    }, [notice]);

    const filteredCourses = useMemo(() => {
        const normalizedQuery = searchQuery.trim().toLowerCase();

        const nextCourses = courses.filter((course) => {
            const matchesSearch =
                !normalizedQuery ||
                getCourseText(course).includes(normalizedQuery);

            return matchesSearch;
        });

        return [...nextCourses].sort((first, second) => {
            if (sortOrder === "OLDEST") {
                return (
                    new Date(first.savedAt || first.createdAt).getTime() -
                    new Date(second.savedAt || second.createdAt).getTime()
                );
            }

            if (sortOrder === "TITLE") {
                return first.title.localeCompare(
                    second.title,
                    "ko"
                );
            }

            return (
                new Date(second.savedAt || second.createdAt).getTime() -
                new Date(first.savedAt || first.createdAt).getTime()
            );
        });
    }, [courses, searchQuery, sortOrder]);

    const featuredCourse = filteredCourses[0] || null;
    const remainingCourses = filteredCourses;
    const totalPages = Math.max(
        1,
        Math.ceil(remainingCourses.length / PAGE_SIZE)
    );
    const safeCurrentPage = Math.min(currentPage, totalPages);

    const visibleCourses = remainingCourses.slice(
        (safeCurrentPage - 1) * PAGE_SIZE,
        safeCurrentPage * PAGE_SIZE
    );
    const showPagination = remainingCourses.length > PAGE_SIZE;

    const handleProfileEdit = () => {
        const loginType = member.loginType || "LOCAL";

        if (loginType !== "LOCAL") {
            window.alert(
                `${loginType} 계정으로 가입한 회원입니다.\n` +
                "계정 정보는 해당 소셜 서비스에서 관리해주세요."
            );
            return;
        }

        window.location.assign("/find-password");
    };

    const handleRemoveBookmark = async (course) => {
        if (removingIds.has(course.courseId)) {
            return;
        }

        const previousCourses = courses;

        setRemovingIds((current) => {
            const next = new Set(current);
            next.add(course.courseId);
            return next;
        });
        setCourses((current) =>
            current.filter(
                (item) => item.courseId !== course.courseId
            )
        );
        setNotice(`${course.title} 저장을 해제했습니다.`);

        try {
            await removeSavedRecommendedCourse(
                course.courseId,
                memberId
            );
        } catch (error) {
            setCourses(previousCourses);
            setNotice("");
            setErrorMessage(
                error.message ||
                "북마크를 해제하지 못했습니다."
            );
        } finally {
            setRemovingIds((current) => {
                const next = new Set(current);
                next.delete(course.courseId);
                return next;
            });
        }
    };

    return (
        <div className="mypage-v3 saved-courses-page">
            <Header />

            <main className="mypage-v3-main">
                <div className="mypage-v3-layout">
                    {false && (<aside className="mypage-v3-sidebar">
                        <section className="mypage-v3-profile">
                            <div className="mypage-v3-avatar">
                                <UserRound
                                    size={54}
                                    strokeWidth={1.5}
                                />
                            </div>

                            <strong>{userName}님</strong>
                            <span>{member.email}</span>

                            <button
                                className="mypage-profile-edit"
                                type="button"
                                onClick={handleProfileEdit}
                            >
                                <Pencil size={16} />
                                회원 정보 수정
                            </button>
                        </section>

                        <nav
                            className="mypage-v3-menu"
                            aria-label="마이페이지 메뉴"
                        >
                            {menuItems.map(
                                ({ label, path, Icon }) => (
                                    <a
                                        className={
                                            label === "저장한 추천 코스"
                                                ? "active"
                                                : ""
                                        }
                                        href={path}
                                        key={label}
                                    >
                                        <Icon
                                            size={20}
                                            strokeWidth={1.8}
                                        />
                                        <span>{label}</span>
                                    </a>
                                )
                            )}
                        </nav>

                        <a
                            className="mypage-retest"
                            href="/map-course?category=palace-culture"
                        >
                            <Plus size={18} />
                            지도 코스 만들기
                        </a>
                    </aside>)}
                    <MypageSidebar
                        activePath="/mypage/courses"
                        bottomAction="retest"
                    />

                    <section className="saved-courses-content">
                        <header className="saved-courses-heading">
                            <div>
                                <p>
                                    <a href="/mypage">마이페이지</a>
                                    <ChevronRight size={14} />
                                    <span>저장한 추천 코스</span>
                                </p>

                                <h1>저장한 추천 코스</h1>
                                <span>
                                    마음에 든 서울 여행 코스를
                                    한곳에서 관리해보세요.
                                </span>
                            </div>

                            <div className="saved-course-count">
                                <span>
                                    총 <strong>{courses.length}</strong>개의 코스
                                </span>
                                <Bookmark
                                    size={22}
                                    fill="currentColor"
                                />
                            </div>
                        </header>

                        <section
                            className="saved-course-toolbar"
                            aria-label="저장 코스 검색 및 필터"
                        >
                            <label className="saved-course-search">
                                <Search size={19} />
                                <input
                                    type="search"
                                    value={searchQuery}
                                    onChange={(event) => {
                                        setSearchQuery(event.target.value);
                                        setCurrentPage(1);
                                    }}
                                    placeholder="코스명으로 검색"
                                />
                            </label>

                            <div className="saved-course-view-tools">
                                <label className="saved-course-sort">
                                    <select
                                        value={sortOrder}
                                        onChange={(event) => {
                                            setSortOrder(event.target.value);
                                            setCurrentPage(1);
                                        }}
                                        aria-label="정렬 기준"
                                    >
                                        <option value="RECENT">
                                            최근 저장순
                                        </option>
                                        <option value="OLDEST">
                                            오래된 저장순
                                        </option>
                                        <option value="TITLE">
                                            코스명순
                                        </option>
                                    </select>
                                    <ChevronDown size={16} />
                                </label>

                                <div
                                    className="saved-course-view-toggle"
                                    aria-label="보기 방식"
                                >
                                    <button
                                        className={
                                            viewMode === "GRID"
                                                ? "active"
                                                : ""
                                        }
                                        type="button"
                                        aria-label="그리드 보기"
                                        title="그리드 보기"
                                        onClick={() => setViewMode("GRID")}
                                    >
                                        <Grid2X2 size={18} />
                                    </button>
                                    <button
                                        className={
                                            viewMode === "LIST"
                                                ? "active"
                                                : ""
                                        }
                                        type="button"
                                        aria-label="목록 보기"
                                        title="목록 보기"
                                        onClick={() => setViewMode("LIST")}
                                    >
                                        <List size={19} />
                                    </button>
                                </div>
                            </div>
                        </section>

                        {notice && (
                            <div
                                className="saved-course-notice"
                                role="status"
                            >
                                {notice}
                            </div>
                        )}

                        {isLoading && (
                            <div className="saved-course-state">
                                저장한 추천 코스를 불러오고 있습니다.
                            </div>
                        )}

                        {!isLoading && errorMessage && (
                            <div className="saved-course-state error">
                                <p>{errorMessage}</p>
                                <button
                                    type="button"
                                    onClick={() => window.location.reload()}
                                >
                                    다시 불러오기
                                </button>
                            </div>
                        )}

                        {!isLoading &&
                            !errorMessage &&
                            filteredCourses.length === 0 && (
                                <div className="saved-course-state empty">
                                    <Bookmark size={34} />
                                    <h2>
                                        {courses.length === 0
                                            ? "아직 저장한 추천 코스가 없습니다."
                                            : "조건에 맞는 코스가 없습니다."}
                                    </h2>
                                    <p>
                                        취향에 맞는 서울 코스를 추천받고
                                        북마크해보세요.
                                    </p>
                                    <a href="/courses">
                                        새로운 코스 추천받기
                                        <ChevronRight size={16} />
                                    </a>
                                </div>
                            )}

                        {!isLoading &&
                            !errorMessage &&
                            featuredCourse && (
                                <>
                                    <div className="saved-course-section-title">
                                        <h2>최근 저장한 코스</h2>
                                    </div>

                                    <article
                                        className="saved-course-featured"
                                        key={featuredCourse.courseId}
                                    >
                                        <div className="saved-course-featured-image">
                                            <SavedCourseCoverImage course={featuredCourse} />
                                            <div className="saved-course-featured-badges">
                                                <span>
                                                    {featuredCourse.travelCode
                                                        ? `${featuredCourse.travelCode} 추천`
                                                        : "취향 추천"}
                                                </span>
                                                <em>
                                                    {
                                                        getCourseTheme(
                                                            featuredCourse
                                                        ).label
                                                    }
                                                </em>
                                            </div>
                                        </div>

                                        <div className="saved-course-featured-body">
                                            <h2>{featuredCourse.title}</h2>
                                            <p>
                                                {featuredCourse.description ||
                                                    "취향 검사 결과를 바탕으로 추천한 서울 여행 코스입니다."}
                                            </p>

                                            <div className="saved-course-featured-meta">
                                                <span>
                                                    <MapPin size={16} />
                                                    {featuredCourse.region ||
                                                        featuredCourse.area ||
                                                        "서울"}
                                                </span>
                                                <span>
                                                    <Clock3 size={16} />
                                                    {formatDuration(
                                                        featuredCourse.totalCourseMinutes ??
                                                            featuredCourse.totalCourseTimeMinutes ??
                                                            featuredCourse.totalVisitTimeMinutes,
                                                        featuredCourse.placeCount
                                                    )}
                                                </span>
                                                <span>
                                                    <Route size={16} />
                                                    {featuredCourse.placeCount ||
                                                        0}
                                                    개 장소
                                                </span>
                                            </div>

                                            <div className="saved-course-featured-tags">
                                                <span>
                                                    {
                                                        getCourseTheme(
                                                            featuredCourse
                                                        ).label
                                                    }
                                                </span>
                                                {featuredCourse.travelCode && (
                                                    <span>
                                                        {
                                                            featuredCourse.travelCode
                                                        }
                                                    </span>
                                                )}
                                            </div>

                                            <div className="saved-course-featured-date">
                                                <CalendarDays size={16} />
                                                {formatSavedDate(
                                                    featuredCourse.savedAt || featuredCourse.createdAt
                                                )}{" "}
                                                저장
                                            </div>

                                            <div className="saved-course-featured-actions">
                                                <a
                                                    href={`/mypage/courses/${featuredCourse.courseId}`}
                                                    onClick={() =>
                                                        rememberCourseDetailEntry(featuredCourse)
                                                    }
                                                >
                                                    코스 상세보기
                                                </a>
                                                <button
                                                    type="button"
                                                    disabled={removingIds.has(
                                                        featuredCourse.courseId
                                                    )}
                                                    onClick={() =>
                                                        handleRemoveBookmark(
                                                            featuredCourse
                                                        )
                                                    }
                                                >
                                                    저장 해제
                                                </button>
                                            </div>
                                        </div>

                                        <aside className="saved-course-featured-route">
                                            <div>
                                                <strong>DAY 1 주요 동선</strong>
                                                <button
                                                    type="button"
                                                    disabled={removingIds.has(
                                                        featuredCourse.courseId
                                                    )}
                                                    aria-label={`${featuredCourse.title} 북마크 해제`}
                                                    title="북마크 해제"
                                                    onClick={() =>
                                                        handleRemoveBookmark(
                                                            featuredCourse
                                                        )
                                                    }
                                                >
                                                    <Bookmark
                                                        size={22}
                                                        fill="currentColor"
                                                    />
                                                </button>
                                            </div>

                                            <ol>
                                                {getRouteStops(
                                                    featuredCourse
                                                ).map((stop, index) => (
                                                    <li key={`${stop}-${index}`}>
                                                        <span>{index + 1}</span>
                                                        <i>
                                                            {index % 2 === 0 ? (
                                                                <Landmark
                                                                    size={17}
                                                                />
                                                            ) : (
                                                                <Utensils
                                                                    size={17}
                                                                />
                                                            )}
                                                        </i>
                                                        <strong>{stop}</strong>
                                                    </li>
                                                ))}
                                            </ol>

                                            {getRouteStops(featuredCourse)
                                                .length === 0 && (
                                                <p>
                                                    코스 상세에서 전체 동선을
                                                    확인할 수 있습니다.
                                                </p>
                                            )}
                                        </aside>
                                    </article>

                                    {remainingCourses.length > 0 && (
                                        <section className="saved-course-list-section">
                                            <div className="saved-course-list-heading">
                                                <div>
                                                    <h2>
                                                        저장한 코스 전체
                                                    </h2>
                                                    <span>
                                                        {
                                                            remainingCourses.length
                                                        }
                                                        개의 코스
                                                    </span>
                                                </div>

                                                {showPagination && (
                                                    <nav
                                                        className="saved-course-pagination"
                                                        aria-label="저장 코스 페이지"
                                                    >
                                                        <button
                                                            type="button"
                                                            aria-label="이전 페이지"
                                                            disabled={
                                                                safeCurrentPage ===
                                                                1
                                                            }
                                                            onClick={() =>
                                                                setCurrentPage(
                                                                    safeCurrentPage -
                                                                        1
                                                                )
                                                            }
                                                        >
                                                            <ChevronLeft
                                                                size={17}
                                                            />
                                                        </button>

                                                        {Array.from(
                                                            {
                                                                length: totalPages,
                                                            },
                                                            (_, index) =>
                                                                index + 1
                                                        ).map((page) => (
                                                            <button
                                                                className={
                                                                    safeCurrentPage ===
                                                                    page
                                                                        ? "active"
                                                                        : ""
                                                                }
                                                                type="button"
                                                                aria-current={
                                                                    safeCurrentPage ===
                                                                    page
                                                                        ? "page"
                                                                        : undefined
                                                                }
                                                                onClick={() =>
                                                                    setCurrentPage(
                                                                        page
                                                                    )
                                                                }
                                                                key={page}
                                                            >
                                                                {page}
                                                            </button>
                                                        ))}

                                                        <button
                                                            type="button"
                                                            aria-label="다음 페이지"
                                                            disabled={
                                                                safeCurrentPage ===
                                                                totalPages
                                                            }
                                                            onClick={() =>
                                                                setCurrentPage(
                                                                    safeCurrentPage +
                                                                        1
                                                                )
                                                            }
                                                        >
                                                            <ChevronRight
                                                                size={17}
                                                            />
                                                        </button>
                                                    </nav>
                                                )}
                                            </div>

                                            <div
                                                className={`saved-course-compact-list ${
                                                    viewMode === "GRID"
                                                        ? "grid-view"
                                                        : ""
                                                } ${
                                                    visibleCourses.length === 1
                                                        ? "single-item"
                                                        : ""
                                                }`}
                                            >
                                                {visibleCourses.map(
                                                    (course, index) => {
                                                        const theme =
                                                            getCourseTheme(
                                                                course
                                                            );
                                                        const isRemoving =
                                                            removingIds.has(
                                                                course.courseId
                                                            );

                                                        return (
                                                            <article
                                                                className="saved-course-compact-item"
                                                                key={
                                                                    course.courseId
                                                                }
                                                            >
                                                                <SavedCourseCoverImage course={course} />

                                                                <div className="saved-course-compact-copy">
                                                                    <h3>
                                                                        {
                                                                            course.title
                                                                        }
                                                                    </h3>
                                                                    <p>
                                                                        {course.description ||
                                                                            "저장한 서울 여행 코스입니다."}
                                                                    </p>
                                                                    <div>
                                                                        <span>
                                                                            <MapPin
                                                                                size={
                                                                                    14
                                                                                }
                                                                            />
                                                                            {course.region ||
                                                                                course.area ||
                                                                                "서울"}
                                                                        </span>
                                                                        <span>
                                                                            <Clock3
                                                                                size={
                                                                                    14
                                                                                }
                                                                            />
                                                                            {formatDuration(
                                                                                course.totalCourseMinutes ??
                                                                                course.totalCourseTimeMinutes ??
                                                                                course.totalVisitTimeMinutes,
                                                                                course.placeCount
                                                                            )}
                                                                        </span>
                                                                        <span>
                                                                            <Route
                                                                                size={
                                                                                    14
                                                                                }
                                                                            />
                                                                            {course.placeCount ||
                                                                                0}
                                                                            개
                                                                            장소
                                                                        </span>
                                                                    </div>
                                                                </div>

                                                                <em
                                                                    className={`theme-${theme.value.toLowerCase()}`}
                                                                >
                                                                    {theme.label}
                                                                </em>

                                                                <span className="saved-course-compact-date">
                                                                    <CalendarDays
                                                                        size={15}
                                                                    />
                                                                    {formatSavedDate(
                                                                        course.savedAt || course.createdAt
                                                                    )}{" "}
                                                                    저장
                                                                </span>

                                                                <button
                                                                    className="saved-course-compact-bookmark"
                                                                    type="button"
                                                                    disabled={
                                                                        isRemoving
                                                                    }
                                                                    aria-label={`${course.title} 북마크 해제`}
                                                                    title="북마크 해제"
                                                                    onClick={() =>
                                                                        handleRemoveBookmark(
                                                                            course
                                                                        )
                                                                    }
                                                                >
                                                                    <Bookmark
                                                                        size={20}
                                                                        fill="currentColor"
                                                                    />
                                                                </button>


                                                                <a
                                                                    className="saved-course-compact-detail"
                                                                    href={`/mypage/courses/${course.courseId}`}
                                                                    onClick={() =>
                                                                        rememberCourseDetailEntry(course)
                                                                    }
                                                                >
                                                                    상세 보기
                                                                    <ChevronRight
                                                                        size={15}
                                                                    />
                                                                </a>
                                                            </article>
                                                        );
                                                    }
                                                )}
                                            </div>
                                        </section>
                                    )}
                                </>
                            )}
                    </section>
                </div>
            </main>

            <Footer />
        </div>
    );
}

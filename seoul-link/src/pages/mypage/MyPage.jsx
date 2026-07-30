import { useEffect, useState } from "react";
import {
    Bookmark,
    BriefcaseBusiness,
    CalendarDays,
    ChevronRight,
    Clock3,
    CreditCard,
    Footprints,
    Heart,
    Landmark,
    Leaf,
    MapPin,
    MessageCircle,
    Pencil,
    Route,
    ShieldCheck,
    Sparkles,
    Star,
    UserRound,
    WalletCards,
} from "lucide-react";

import Header from "../../components/common/Header";
import Footer from "../../components/common/Footer";
import { getMyTravelType } from "../../api/mypageApi";
import { claimGuestSurvey } from "../../api/surveyApi";
import {
    getCourseDraft,
    getCustomCourses,
    getSavedRecommendedCourses,
    removeSavedRecommendedCourse,
} from "../../api/courseApi";
import { getMemberReviews } from "../../api/reviewApi";
import { getCodeTraits } from "../../data/travelPreferenceData";
import { authStore } from "../../store/authStore";

import heroImage from "../../assets/images/hero-seoul-main.png";
import hanokImage from "../../assets/images/moods/mood-hanok-photo.png";
import sunsetImage from "../../assets/images/moods/mood-sunset-seoul.png";
import cafeImage from "../../assets/images/moods/mood-rainy-cafe.png";
import mapPromoImage from "../../assets/images/mypage-map-promo.png";
import {
    storeCourseRecommendRequest
} from "../../utils/courseRecommendationHandoff";
import "../../styles/mypage.css";
import "../../styles/mypage-travel-code-colors.css";

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

const summaryDefinitions = [
    {
        key: "saved",
        label: "저장한 추천 코스",
        Icon: Bookmark,
        color: "blue",
        path: "/mypage/courses",
    },
    {
        key: "custom",
        label: "직접 만든 코스",
        Icon: Pencil,
        color: "cyan",
        path: "/mypage/custom-courses",
    },
    {
        key: "reviews",
        label: "작성한 후기",
        Icon: MessageCircle,
        color: "purple",
        path: "/mypage/reviews",
    },
];

const traitIconMap = {
    walk: Footprints,
    leaf: Leaf,
    landmark: Landmark,
    spark: Sparkles,
    wallet: WalletCards,
    star: Star,
    shield: ShieldCheck,
    heart: Heart,
    calendar: CalendarDays,
    clock: Clock3,
};

const COURSE_FALLBACK_IMAGES = [
    hanokImage,
    sunsetImage,
    cafeImage,
];

function formatCourseDuration(totalMinutes, placeCount) {
    const minutes = Math.round(Number(totalMinutes) || 0);

    if (minutes <= 0) {
        return placeCount > 0 ? `${placeCount}개 장소` : "일정 확인";
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
        return String(value);
    }

    return new Intl.DateTimeFormat("ko-KR", {
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
    })
        .format(date)
        .replace(/\s/g, "");
}

export default function MyPage() {
    const member = authStore.getMember() || {};
    const [travelType, setTravelType] = useState(null);
    const [travelTypeLoading, setTravelTypeLoading] = useState(
        Boolean(member.memberId)
    );
    const [travelTypeError, setTravelTypeError] = useState("");
    const [savedCourses, setSavedCourses] = useState([]);
    const [customCourseCount, setCustomCourseCount] = useState(0);
    const [reviewCount, setReviewCount] = useState(0);
    const [dashboardLoading, setDashboardLoading] = useState(
        Boolean(member.memberId)
    );
    const [dashboardError, setDashboardError] = useState("");
    const [removingCourseIds, setRemovingCourseIds] = useState(() => new Set());

    useEffect(() => {
        if (!member.memberId) {
            return undefined;
        }

        let active = true;

        const pendingGuestToken =
            localStorage.getItem("guestToken");

        const claimPendingSurvey = pendingGuestToken
            ? claimGuestSurvey(
                pendingGuestToken,
                member.memberId
            )
                .then(() => {
                    localStorage.removeItem("guestToken");
                })
                .catch((error) => {
                    console.error(
                        "이전 취향 검사 결과 연결 실패:",
                        error
                    );
                })
            : Promise.resolve();

        claimPendingSurvey
            .then(() => getMyTravelType(member.memberId))
            .then((result) => {
                if (active) {
                    setTravelType(result);
                    setTravelTypeError("");
                }
            })
            .catch((error) => {
                if (active) {
                    setTravelTypeError(
                        error?.message ||
                        "여행 취향 결과를 불러오지 못했습니다."
                    );
                }
            })
            .finally(() => {
                if (active) {
                    setTravelTypeLoading(false);
                }
            });

        return () => {
            active = false;
        };
    }, [member.memberId]);

    useEffect(() => {
        if (!member.memberId) {
            return undefined;
        }

        const refreshWhenVisible = () => {
            if (document.visibilityState !== "visible") {
                return;
            }

            getMyTravelType(member.memberId)
                .then((result) => {
                    setTravelType(result);
                    setTravelTypeError("");
                })
                .catch(() => {
                    // Keep the already displayed result when a background refresh fails.
                });
        };

        window.addEventListener("focus", refreshWhenVisible);
        document.addEventListener("visibilitychange", refreshWhenVisible);

        return () => {
            window.removeEventListener("focus", refreshWhenVisible);
            document.removeEventListener("visibilitychange", refreshWhenVisible);
        };
    }, [member.memberId]);

    useEffect(() => {
        if (!member.memberId) {
            setDashboardLoading(false);
            setDashboardError("로그인 후 여행 기록을 확인할 수 있습니다.");
            return undefined;
        }

        let active = true;

        Promise.all([
            getSavedRecommendedCourses(member.memberId),
            getCustomCourses(member.memberId),
            getMemberReviews(member.memberId, {
                page: 0,
                size: 1,
                sort: "date",
            }),
        ])
            .then(([saved, custom, reviews]) => {
                if (!active) {
                    return;
                }

                setSavedCourses(Array.isArray(saved) ? saved : []);
                setCustomCourseCount(Array.isArray(custom) ? custom.length : 0);
                setReviewCount(
                    Number(reviews?.totalElements) ||
                    (Array.isArray(reviews?.content)
                        ? reviews.content.length
                        : 0)
                );
                setDashboardError("");
            })
            .catch((error) => {
                if (active) {
                    setDashboardError(
                        error?.message ||
                        "여행 기록을 불러오지 못했습니다."
                    );
                }
            })
            .finally(() => {
                if (active) {
                    setDashboardLoading(false);
                }
            });

        return () => {
            active = false;
        };
    }, [member.memberId]);

    const userName =
        member.nickname?.trim() ||
        member.name?.trim() ||
        "민영환";

    const email =
        member.email ||
        "user@seoulink.com";

    const loginType =
        member.loginType ||
        "LOCAL";

    const travelCode =
        travelType?.travelCode?.trim().toUpperCase() ||
        "";

    const tasteTraits =
        travelCode.length === 5
            ? getCodeTraits(travelCode)
            : [];

    const reloadTravelType = () => {
        if (!member.memberId) {
            return;
        }

        setTravelTypeLoading(true);
        setTravelTypeError("");

        getMyTravelType(member.memberId)
            .then(setTravelType)
            .catch((error) => {
                setTravelTypeError(
                    error?.message ||
                    "여행 취향 결과를 불러오지 못했습니다."
                );
            })
            .finally(() => {
                setTravelTypeLoading(false);
            });
    };

    const handleProfileEdit = () => {
        if (loginType !== "LOCAL") {
            window.alert(
                `${loginType} 계정으로 가입한 회원입니다.\n` +
                "계정 정보는 해당 소셜 서비스에서 관리해주세요."
            );
            return;
        }

        window.location.assign("/find-password");
    };

    const handleRecommendCourse = async (event) => {
        event.preventDefault();

        const surveyId = Number(travelType?.surveyId);

        if (!Number.isInteger(surveyId) || surveyId < 1) {
            window.alert("최신 취향 검사 정보를 확인할 수 없습니다.");
            return;
        }

        try {
            const draft = await getCourseDraft(surveyId);

            const transportModeMap = {
                PUBLIC: "PUBLIC_TRANSIT",
                PUBLIC_TRANSIT: "PUBLIC_TRANSIT",
                WALKING: "WALKING",
                CAR: "DRIVING",
                DRIVING: "DRIVING",
            };

            const transportMode =
                transportModeMap[
                    String(draft?.transportType || "").toUpperCase()
                    ];

            if (!transportMode) {
                throw new Error(
                    "추천 코스의 이동수단을 확인할 수 없습니다."
                );
            }

            storeCourseRecommendRequest({
                ...draft,
                transportMode,
                excludedRecommendationKeys: [],
            });

            window.location.assign("/courses");
        } catch (error) {
            console.error("맞춤 코스 준비 실패:", error);

            window.alert(
                error?.message ||
                "맞춤 코스를 준비하지 못했습니다."
            );
        }
    };

    const handleRemoveSavedCourse = async (course) => {
        if (removingCourseIds.has(course.courseId)) {
            return;
        }

        const previousCourses = savedCourses;

        setRemovingCourseIds((current) => {
            const next = new Set(current);
            next.add(course.courseId);
            return next;
        });
        setSavedCourses((current) =>
            current.filter((item) => item.courseId !== course.courseId)
        );

        try {
            await removeSavedRecommendedCourse(
                course.courseId,
                member.memberId
            );
        } catch (error) {
            setSavedCourses(previousCourses);
            setDashboardError(
                error?.message ||
                "저장한 코스를 해제하지 못했습니다."
            );
        } finally {
            setRemovingCourseIds((current) => {
                const next = new Set(current);
                next.delete(course.courseId);
                return next;
            });
        }
    };

    const openSavedCourseDetail = (course) => {
        if (!Number.isInteger(Number(course?.courseId))) {
            return;
        }

        window.location.assign(`/mypage/courses/${course.courseId}`);
    };

    const handleSavedCourseKeyDown = (event, course) => {
        if (event.key === "Enter" || event.key === " ") {
            event.preventDefault();
            openSavedCourseDetail(course);
        }
    };

    const summaryItems = summaryDefinitions.map((item) => ({
        ...item,
        value: dashboardLoading
            ? "-"
            : {
                saved: savedCourses.length,
                custom: customCourseCount,
                reviews: reviewCount,
            }[item.key],
    }));

    const recentCourses = savedCourses.slice(0, 3);

    return (
        <div className="mypage-v3">
            <Header />

            <main className="mypage-v3-main">
                <div className="mypage-v3-layout">
                    <aside className="mypage-v3-sidebar">
                        <section className="mypage-v3-profile">
                            <div className="mypage-v3-avatar">
                                <UserRound
                                    size={54}
                                    strokeWidth={1.5}
                                />
                            </div>

                            <strong>{userName}님</strong>
                            <span>{email}</span>

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
                                ({ label, path, Icon }, index) => (
                                    <a
                                        className={
                                            index === 0
                                                ? "active"
                                                : ""
                                        }
                                        href={path}
                                        key={`${label}-${index}`}
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

                        <a className="mypage-map-promo" href="/map-course">
                            <span>새로운 여행을</span>
                            <strong>계획해 보세요!</strong>
                            <p>
                                지도에서 원하는 코스를
                                <br />
                                직접 만들 수 있어요.
                            </p>

                            <div
                                className="mypage-map-promo-art"
                                aria-hidden="true"
                            >
                                <img src={mapPromoImage} alt="" />
                            </div>

                            <b>
                                지도 코스 만들기
                                <ChevronRight size={16} />
                            </b>
                        </a>
                    </aside>

                    <section className="mypage-v3-content">
                        <div className="mypage-v3-top">
                            <section
                                className="mypage-type-hero"
                                style={{
                                    backgroundImage: `
                                        linear-gradient(
                                            90deg,
                                            rgba(255,255,255,0.98) 0%,
                                            rgba(255,255,255,0.92) 42%,
                                            rgba(255,255,255,0.2) 72%,
                                            rgba(255,255,255,0.04) 100%
                                        ),
                                        url(${heroImage})
                                    `,
                                }}
                            >
                                <span className="mypage-type-label">
                                    나의 여행 취향
                                </span>

                                {travelTypeLoading ? (
                                    <div className="mypage-travel-state">
                                        <span className="mypage-travel-spinner" />
                                        <strong>
                                            최신 취향 결과를 불러오고 있어요
                                        </strong>
                                    </div>
                                ) : travelTypeError ? (
                                    <div className="mypage-travel-state">
                                        <strong>
                                            취향 결과를 불러오지 못했습니다
                                        </strong>
                                        <button
                                            type="button"
                                            onClick={reloadTravelType}
                                        >
                                            다시 불러오기
                                        </button>
                                    </div>
                                ) : travelType && tasteTraits.length === 5 ? (
                                    <>
                                        <strong className="mypage-type-code">
                                            {[...travelCode].map((code, index) => (
                                                <span className={`tone-${code.toLowerCase()}`} key={`${code}-${index}`}>
                                                    {code}
                                                </span>
                                            ))}
                                        </strong>

                                        <div
                                            className="mypage-type-axes"
                                            aria-label={`${travelCode} 여행 유형`}
                                        >
                                            {tasteTraits.map(
                                                ({ code, label, dimensionKey, color }) => (
                                                    <div key={dimensionKey}>
                                                        <strong
                                                            className={
                                                                `type-axis ${color}`
                                                            }
                                                        >
                                                            {code}
                                                        </strong>
                                                        <span>{label}</span>
                                                    </div>
                                                )
                                            )}
                                        </div>

                                        <h1>
                                            {travelType.typeTitle ||
                                                `${travelCode} 여행자`}
                                        </h1>

                                        <p>
                                            {travelType.typeDescription ||
                                                tasteTraits
                                                    .map(({ answer }) => answer)
                                                    .join(", ")}
                                        </p>

                                        <a href="/mypage/travel-type">
                                            여행 코드 자세히 보기
                                            <ChevronRight size={17} />
                                        </a>
                                    </>
                                ) : (
                                    <div className="mypage-travel-state">
                                        <strong>
                                            아직 저장된 취향 검사 결과가 없어요
                                        </strong>
                                        <p>
                                            간단한 검사로 나에게 맞는 서울 여행
                                            유형을 확인해보세요.
                                        </p>
                                        <a href="/survey">
                                            취향 검사 시작하기
                                            <ChevronRight size={16} />
                                        </a>
                                    </div>
                                )}
                            </section>

                            <section className="mypage-analysis">
                                <h2>취향 분석 요약</h2>

                                {travelTypeLoading ? (
                                    <div className="mypage-analysis-empty">
                                        분석 내용을 준비하고 있어요.
                                    </div>
                                ) : tasteTraits.length === 5 ? (
                                    <div className="mypage-analysis-list">
                                        {tasteTraits.map(
                                            (
                                                {
                                                    code,
                                                    answer,
                                                    icon,
                                                    dimensionKey,
                                                    color,
                                                },
                                            ) => {
                                                const Icon =
                                                    traitIconMap[icon] ||
                                                    Sparkles;

                                                return (
                                                    <div key={dimensionKey}>
                                                        <span
                                                            className={
                                                                `analysis-icon ${color}`
                                                            }
                                                        >
                                                            <Icon
                                                                size={17}
                                                                strokeWidth={1.8}
                                                            />
                                                        </span>

                                                        <strong>
                                                            {answer}
                                                        </strong>

                                                        <em
                                                            className={
                                                                `analysis-code ${color}`
                                                            }
                                                        >
                                                            {code}
                                                        </em>
                                                    </div>
                                                );
                                            }
                                        )}
                                    </div>
                                ) : (
                                    <div className="mypage-analysis-empty">
                                        검사를 완료하면 다섯 가지 여행 성향을
                                        한눈에 볼 수 있어요.
                                    </div>
                                )}

                                <div className="mypage-analysis-actions">
                                    {tasteTraits.length === 5 ? (
                                        <>
                                            <a href="/mypage/travel-type">
                                                상세 결과 보기
                                            </a>

                                            <a
                                                className="primary"
                                                href="/courses"
                                                onClick={handleRecommendCourse}
                                            >
                                                맞춤 코스 추천
                                            </a>
                                        </>
                                    ) : (
                                        <a
                                            className="primary full"
                                            href="/survey"
                                        >
                                            취향 검사 시작하기
                                        </a>
                                    )}
                                </div>
                            </section>
                        </div>

                        <section className="mypage-v3-summary">
                            {summaryItems.map(
                                ({
                                     label,
                                     value,
                                     Icon,
                                     color,
                                     path,
                                 }) => (
                                    <a
                                        className={
                                            `summary-card summary-${color}`
                                        }
                                        href={path}
                                        key={label}
                                    >
                                        <span className="summary-card-icon">
                                            <Icon
                                                size={25}
                                                strokeWidth={1.8}
                                            />
                                        </span>

                                        <span className="summary-card-text">
                                            <small>{label}</small>

                                            <strong>
                                                {value}
                                                <em>개</em>
                                            </strong>
                                        </span>

                                        <ChevronRight
                                            className="summary-card-arrow"
                                            size={18}
                                        />
                                    </a>
                                )
                            )}
                        </section>

                        <section className="recent-course-section">
                            <div className="recent-course-heading">
                                <div>
                                    <Bookmark
                                        size={18}
                                        strokeWidth={1.8}
                                    />
                                    <h2>최근 저장한 코스</h2>
                                </div>

                                <a href="/mypage/courses">
                                    전체 보기
                                    <ChevronRight size={16} />
                                </a>
                            </div>

                            <div className="recent-course-grid">
                                {dashboardLoading && (
                                    <p className="recent-course-state">
                                        여행 기록을 불러오고 있습니다.
                                    </p>
                                )}

                                {!dashboardLoading && dashboardError && (
                                    <p className="recent-course-state error">
                                        {dashboardError}
                                    </p>
                                )}

                                {!dashboardLoading &&
                                !dashboardError &&
                                recentCourses.length === 0 && (
                                    <p className="recent-course-state">
                                        아직 저장한 추천 코스가 없습니다.
                                    </p>
                                )}

                                {!dashboardLoading &&
                                !dashboardError &&
                                recentCourses.map((course, index) => (
                                    <article
                                        className="recent-course-card"
                                        key={course.courseId}
                                        role="link"
                                        tabIndex={0}
                                        aria-label={`${course.title} 상세 일정 보기`}
                                        onClick={() => openSavedCourseDetail(course)}
                                        onKeyDown={(event) =>
                                            handleSavedCourseKeyDown(event, course)
                                        }
                                    >
                                        <div className="course-image-wrap">
                                            <img
                                                src={
                                                    course.thumbnailUrl ||
                                                    course.coverImageUrl ||
                                                    COURSE_FALLBACK_IMAGES[
                                                        index %
                                                        COURSE_FALLBACK_IMAGES.length
                                                    ]
                                                }
                                                alt={course.title}
                                                onError={(event) => {
                                                    event.currentTarget.onerror = null;
                                                    event.currentTarget.src = COURSE_FALLBACK_IMAGES[
                                                        index % COURSE_FALLBACK_IMAGES.length
                                                    ];
                                                }}
                                            />

                                            <span className="course-best">
                                                BEST
                                            </span>

                                            <button
                                                className="course-bookmark-icon"
                                                type="button"
                                                aria-label={
                                                    `${course.title} 저장 해제`
                                                }
                                                disabled={removingCourseIds.has(
                                                    course.courseId
                                                )}
                                                onClick={(event) => {
                                                    event.stopPropagation();
                                                    handleRemoveSavedCourse(course);
                                                }}
                                            >
                                                <Bookmark
                                                    size={22}
                                                    strokeWidth={2.2}
                                                    fill="currentColor"
                                                />
                                            </button>
                                        </div>

                                        <div className="course-card-content">
                                            <h3>{course.title}</h3>

                                            <div className="course-meta">
                                                <span>
                                                    <MapPin size={14} />
                                                    {course.representativePlaceName ||
                                                        course.region ||
                                                        course.regions?.[0] ||
                                                        "서울"}
                                                </span>

                                                <span>
                                                    <Clock3 size={14} />
                                                    {formatCourseDuration(
                                                        course.totalCourseMinutes ?? course.totalCourseTimeMinutes,
                                                        course.placeCount
                                                    )}
                                                </span>
                                            </div>

                                            <div className="course-footer">
                                                <span>
                                                    <CalendarDays
                                                        size={14}
                                                    />
                                                    {formatSavedDate(
                                                        course.savedAt || course.createdAt
                                                    )} 저장
                                                </span>

                                                <em>
                                                    {course.travelCode || "추천 코스"}
                                                </em>
                                            </div>
                                        </div>
                                    </article>
                                ))}
                            </div>
                        </section>
                    </section>
                </div>
            </main>

            <Footer />
        </div>
    );



}

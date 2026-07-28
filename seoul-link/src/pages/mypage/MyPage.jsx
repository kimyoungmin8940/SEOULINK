import {
    Bookmark,
    BriefcaseBusiness,
    CalendarDays,
    ChevronRight,
    Clock3,
    Coffee,
    CreditCard,
    MapPin,
    MessageCircle,
    MoonStar,
    Pencil,
    PersonStanding,
    RefreshCw,
    Route,
    Sparkles,
    Store,
    UserRound,
    Utensils,
    Waves,
} from "lucide-react";

import Header from "../../components/common/Header";
import Footer from "../../components/common/Footer";
import { authStore } from "../../store/authStore";

import heroImage from "../../assets/images/hero-seoul-main.png";
import hanokImage from "../../assets/images/moods/mood-hanok-photo.png";
import sunsetImage from "../../assets/images/moods/mood-sunset-seoul.png";
import cafeImage from "../../assets/images/moods/mood-rainy-cafe.png";

import "../../styles/mypage.css";

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
        path: "/mypage/courses",
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

const summaryItems = [
    {
        label: "저장한 추천 코스",
        value: 4,
        Icon: Bookmark,
        color: "blue",
        path: "/mypage/courses",
    },
    {
        label: "직접 만든 코스",
        value: 3,
        Icon: Pencil,
        color: "cyan",
        path: "/mypage/courses",
    },
    {
        label: "작성한 후기",
        value: 2,
        Icon: MessageCircle,
        color: "purple",
        path: "/mypage/reviews",
    },
];

const tasteAxes = [
    {
        code: "A",
        label: "활동형",
    },
    {
        code: "T",
        label: "역사·문화",
    },
    {
        code: "B",
        label: "자연뷰",
    },
    {
        code: "S",
        label: "힐링형",
    },
    {
        code: "P",
        label: "알찬 일정",
    },
];

const tasteThemes = [
    {
        label: "전통시장",
        Icon: Store,
    },
    {
        label: "한강",
        Icon: Waves,
    },
    {
        label: "야경",
        Icon: MoonStar,
    },
    {
        label: "맛집",
        Icon: Utensils,
    },
    {
        label: "카페",
        Icon: Coffee,
    },
    {
        label: "도보 여행",
        Icon: PersonStanding,
    },
];

const courseItems = [
    {
        id: 1,
        title: "궁궐과 한옥길을 걷는 하루",
        location: "경복궁, 북촌 한옥마을",
        duration: "6시간",
        savedAt: "2026.07.12 저장",
        tag: "역사 · 문화",
        image: hanokImage,
    },
    {
        id: 2,
        title: "한강 노을 데이트 코스",
        location: "여의도, 반포 한강공원",
        duration: "4시간",
        savedAt: "2026.07.08 저장",
        tag: "데이트",
        image: sunsetImage,
    },
    {
        id: 3,
        title: "성수 감성 카페 투어",
        location: "성수동 카페거리",
        duration: "5시간",
        savedAt: "2026.07.01 저장",
        tag: "감성 · 카페",
        image: cafeImage,
    },
];

export default function MyPage() {
    const member = authStore.getMember() || {};

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
                                프로필 수정
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

                        <a
                            className="mypage-retest"
                            href="/survey"
                        >
                            <RefreshCw
                                size={17}
                                strokeWidth={2}
                            />
                            취향 검사 다시하기
                        </a>
                    </aside>

                    <section className="mypage-v3-content">
                        <div className="mypage-v3-top">
                            <section
                                className="mypage-v3-welcome"
                                style={{
                                    backgroundImage: `
                                        linear-gradient(
                                            90deg,
                                            rgba(255,255,255,0.97) 0%,
                                            rgba(255,255,255,0.88) 34%,
                                            rgba(255,255,255,0.16) 70%,
                                            rgba(255,255,255,0.04) 100%
                                        ),
                                        url(${heroImage})
                                    `,
                                }}
                            >
                                <span>
                                    새로운 하루, 새로운 서울
                                </span>

                                <h1>
                                    {userName}님,
                                    <br />
                                    다시 만나 반가워요!
                                </h1>

                                <p>
                                    오늘도 특별한 여행을 계획해보세요.
                                    <br />
                                    서울에서의 멋진 하루를 응원합니다.
                                </p>
                            </section>

                            <section className="mypage-v3-taste">
                                <div className="taste-heading">
                                    <span>나의 여행 취향</span>
                                    <strong>ATBSP</strong>
                                </div>

                                <div
                                    className="taste-axis-grid"
                                    aria-label="ATBSP 여행 유형"
                                >
                                    {tasteAxes.map(
                                        ({ code, label }) => (
                                            <div key={code}>
                                                <strong
                                                    className={
                                                        `taste-axis axis-${code.toLowerCase()}`
                                                    }
                                                >
                                                    {code}
                                                </strong>

                                                <span>{label}</span>
                                            </div>
                                        )
                                    )}
                                </div>

                                <h2>선호 테마</h2>

                                <div className="taste-theme-grid">
                                    {tasteThemes.map(
                                        ({ label, Icon }) => (
                                            <span key={label}>
                                                <Icon
                                                    size={17}
                                                    strokeWidth={1.9}
                                                />
                                                {label}
                                            </span>
                                        )
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
                                {courseItems.map((course) => (
                                    <article
                                        className="recent-course-card"
                                        key={course.id}
                                    >
                                        <div className="course-image-wrap">
                                            <img
                                                src={course.image}
                                                alt={course.title}
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
                                                    {course.location}
                                                </span>

                                                <span>
                                                    <Clock3 size={14} />
                                                    {course.duration}
                                                </span>
                                            </div>

                                            <div className="course-footer">
                                                <span>
                                                    <CalendarDays
                                                        size={14}
                                                    />
                                                    {course.savedAt}
                                                </span>

                                                <em>{course.tag}</em>
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

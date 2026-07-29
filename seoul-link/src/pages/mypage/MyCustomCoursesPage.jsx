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
    Eye,
    MapPin,
    MessageCircle,
    PenLine,
    Pencil,
    Plus,
    RefreshCw,
    Route,
    Search,
    Sparkles,
    Trash2,
    UserRound,
    X,
} from "lucide-react";

import Header from "../../components/common/Header";
import Footer from "../../components/common/Footer";
import {
    deleteCourse,
    getCustomCourses,
    updateCourse,
} from "../../api/courseApi";
import { authStore } from "../../store/authStore";

import hanokImage from "../../assets/images/moods/mood-hanok-photo.png";
import cafeImage from "../../assets/images/moods/mood-rainy-cafe.png";
import nightImage from "../../assets/images/cta-seoul-night.jpg";

import "../../styles/mypage.css";
import "../../styles/customCourses.css";

const FALLBACK_IMAGES = [
    hanokImage,
    cafeImage,
    nightImage,
];

const COURSES_PER_PAGE = 4;

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
        path: "/payment",
        Icon: CreditCard,
    },
];

const statusFilters = [
    {
        value: "ALL",
        label: "전체",
    },
    {
        value: "COMPLETED",
        label: "작성 완료",
    },
    {
        value: "DRAFT",
        label: "작성 중",
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
        ...(course.routePlaceNames || []),
    ]
        .filter(Boolean)
        .join(" ")
        .toLowerCase();
}

function formatDuration(value) {
    const minutes = Math.max(0, Math.round(Number(value) || 0));

    if (minutes === 0) {
        return "시간 미정";
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

function formatDate(value) {
    if (!value) {
        return "수정일 없음";
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

function getRouteText(course) {
    const names = course.routePlaceNames || [];

    if (names.length === 0) {
        return course.region || "서울";
    }

    return names.join("  →  ");
}

export default function MyCustomCoursesPage() {
    const member = authStore.getMember() || {};
    const memberId = member.memberId;
    const userName = getUserName(member);

    const [courses, setCourses] = useState([]);
    const [isLoading, setIsLoading] = useState(Boolean(memberId));
    const [errorMessage, setErrorMessage] = useState(
        memberId ? "" : "로그인 후 직접 만든 코스를 확인할 수 있습니다."
    );
    const [searchQuery, setSearchQuery] = useState("");
    const [activeStatus, setActiveStatus] = useState("ALL");
    const [sortOrder, setSortOrder] = useState("UPDATED_DESC");
    const [currentPage, setCurrentPage] = useState(1);
    const [editingCourse, setEditingCourse] = useState(null);
    const [editForm, setEditForm] = useState({
        title: "",
        description: "",
        region: "",
    });
    const [isSaving, setIsSaving] = useState(false);
    const [editError, setEditError] = useState("");
    const [deletingCourseId, setDeletingCourseId] = useState(null);

    useEffect(() => {
        if (!memberId) {
            return undefined;
        }

        let isMounted = true;

        getCustomCourses(memberId)
            .then((data) => {
                if (isMounted) {
                    setCourses(Array.isArray(data) ? data : []);
                }
            })
            .catch((error) => {
                if (isMounted) {
                    setErrorMessage(
                        error.message ||
                        "직접 만든 코스를 불러오지 못했습니다."
                    );
                }
            })
            .finally(() => {
                if (isMounted) {
                    setIsLoading(false);
                }
            });

        return () => {
            isMounted = false;
        };
    }, [memberId]);

    useEffect(() => {
        if (!editingCourse) {
            return undefined;
        }

        const closeOnEscape = (event) => {
            if (event.key === "Escape" && !isSaving) {
                setEditingCourse(null);
            }
        };

        window.addEventListener("keydown", closeOnEscape);
        return () => window.removeEventListener("keydown", closeOnEscape);
    }, [editingCourse, isSaving]);

    const visibleCourses = useMemo(() => {
        const normalizedQuery = searchQuery.trim().toLowerCase();

        return courses
            .filter((course) => {
                const matchesSearch =
                    !normalizedQuery ||
                    getCourseText(course).includes(normalizedQuery);
                const matchesStatus =
                    activeStatus === "ALL" ||
                    course.status === activeStatus;

                return matchesSearch && matchesStatus;
            })
            .sort((first, second) => {
                if (sortOrder === "CREATED_DESC") {
                    return (
                        new Date(second.createdAt).getTime() -
                        new Date(first.createdAt).getTime()
                    );
                }

                if (sortOrder === "TITLE_ASC") {
                    return first.title.localeCompare(
                        second.title,
                        "ko"
                    );
                }

                return (
                    new Date(second.updatedAt).getTime() -
                    new Date(first.updatedAt).getTime()
                );
            });
    }, [activeStatus, courses, searchQuery, sortOrder]);

    const totalPages = Math.max(
        1,
        Math.ceil(visibleCourses.length / COURSES_PER_PAGE)
    );
    const activePage = Math.min(currentPage, totalPages);

    const paginatedCourses = useMemo(() => {
        const startIndex = (activePage - 1) * COURSES_PER_PAGE;
        const endIndex = startIndex + COURSES_PER_PAGE;

        return visibleCourses.slice(startIndex, endIndex);
    }, [activePage, visibleCourses]);

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

    const openEditModal = (course) => {
        setEditingCourse(course);
        setEditForm({
            title: course.title || "",
            description: course.description || "",
            region: course.region || "서울",
        });
        setEditError("");
    };

    const closeEditModal = () => {
        if (!isSaving) {
            setEditingCourse(null);
            setEditError("");
        }
    };

    const handleEditSubmit = async (event) => {
        event.preventDefault();

        const title = editForm.title.trim();
        if (!title) {
            setEditError("코스 제목을 입력해주세요.");
            return;
        }

        try {
            setIsSaving(true);
            setEditError("");

            const updated = await updateCourse(
                editingCourse.courseId,
                {
                    title,
                    description: editForm.description.trim(),
                    region: editForm.region.trim() || "서울",
                    isPublic: editingCourse.publicStatus || "N",
                },
                { memberId }
            );

            setCourses((current) =>
                current.map((course) =>
                    course.courseId === editingCourse.courseId
                        ? {
                            ...course,
                            title: updated.title,
                            description: updated.description,
                            region: updated.region,
                            updatedAt: updated.updatedAt,
                        }
                        : course
                )
            );
            setEditingCourse(null);
        } catch (error) {
            setEditError(
                error.message ||
                "코스 정보를 수정하지 못했습니다."
            );
        } finally {
            setIsSaving(false);
        }
    };

    const handleDeleteCourse = async (course) => {
        if (deletingCourseId !== null) {
            return;
        }

        const confirmed = window.confirm(
            `"${course.title}" 코스를 삭제하시겠습니까?\n삭제한 코스는 복구할 수 없습니다.`
        );

        if (!confirmed) {
            return;
        }

        try {
            setDeletingCourseId(course.courseId);
            await deleteCourse(course.courseId, memberId);
            setCourses((current) =>
                current.filter(
                    (item) => item.courseId !== course.courseId
                )
            );
        } catch (error) {
            window.alert(
                error.message ||
                "코스를 삭제하지 못했습니다. 잠시 후 다시 시도해주세요."
            );
        } finally {
            setDeletingCourseId(null);
        }
    };

    return (
        <div className="mypage-v3 custom-courses-page">
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
                                            label === "직접 만든 코스"
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
                            href="/map-course"
                        >
                            <Plus size={18} />
                            지도 코스 만들기
                        </a>
                    </aside>

                    <section className="custom-courses-content">
                        <header className="custom-courses-heading">
                            <div>
                                <p>
                                    <a href="/mypage">마이페이지</a>
                                    <ChevronRight size={14} />
                                    <span>직접 만든 코스</span>
                                </p>

                                <h1>직접 만든 코스</h1>
                                <span>
                                    내가 계획한 서울 여행 코스를 확인하고 수정해보세요.
                                </span>
                            </div>

                            <div className="custom-courses-heading-actions">
                                <div className="custom-course-count">
                                    <span
                                        className="custom-course-count-icon"
                                        aria-hidden="true"
                                    >
                                        <PenLine
                                            size={20}
                                            strokeWidth={1.9}
                                        />
                                    </span>
                                    <span className="custom-course-count-copy">
                                        총 <strong>{courses.length}</strong>개의 코스
                                    </span>
                                </div>
                            </div>
                        </header>

                        <section
                            className="custom-course-toolbar"
                            aria-label="직접 만든 코스 검색과 필터"
                        >
                            <label className="custom-course-search">
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

                            <div className="custom-course-filters">
                                {statusFilters.map((filter) => (
                                    <button
                                        className={
                                            activeStatus === filter.value
                                                ? "active"
                                                : ""
                                        }
                                        type="button"
                                        onClick={() => {
                                            setActiveStatus(filter.value);
                                            setCurrentPage(1);
                                        }}
                                        key={filter.value}
                                    >
                                        {filter.label}
                                    </button>
                                ))}
                            </div>

                            <label className="custom-course-sort">
                                <select
                                    value={sortOrder}
                                    onChange={(event) => {
                                        setSortOrder(event.target.value);
                                        setCurrentPage(1);
                                    }}
                                    aria-label="정렬 기준"
                                >
                                    <option value="UPDATED_DESC">
                                        최근 수정순
                                    </option>
                                    <option value="CREATED_DESC">
                                        최근 생성순
                                    </option>
                                    <option value="TITLE_ASC">
                                        코스명순
                                    </option>
                                </select>
                                <ChevronDown size={16} />
                            </label>
                        </section>

                        {isLoading && (
                            <div className="custom-course-state">
                                직접 만든 코스를 불러오고 있습니다.
                            </div>
                        )}

                        {!isLoading && errorMessage && (
                            <div className="custom-course-state error">
                                <p>{errorMessage}</p>
                                <button
                                    type="button"
                                    onClick={() => window.location.reload()}
                                >
                                    <RefreshCw size={16} />
                                    다시 불러오기
                                </button>
                            </div>
                        )}

                        {!isLoading &&
                            !errorMessage &&
                            visibleCourses.length === 0 && (
                                <div className="custom-course-state empty">
                                    <Route size={36} />
                                    <h2>
                                        {courses.length === 0
                                            ? "아직 직접 만든 코스가 없습니다."
                                            : "조건에 맞는 코스가 없습니다."}
                                    </h2>
                                    <p>
                                        지도에서 원하는 장소를 골라 나만의 서울 코스를 만들어보세요.
                                    </p>
                                    <a href="/map-course">
                                        새 코스 만들기
                                        <ChevronRight size={16} />
                                    </a>
                                </div>
                            )}

                        {!isLoading &&
                            !errorMessage &&
                            visibleCourses.length > 0 && (
                                <>
                                    <div className="custom-course-list-heading">
                                        <div>
                                            <h2>직접 만든 코스 전체</h2>
                                            <span>
                                                {visibleCourses.length}개의 코스
                                            </span>
                                        </div>

                                        {totalPages > 1 && (
                                            <nav
                                                className="custom-course-pagination"
                                                aria-label="직접 만든 코스 페이지"
                                            >
                                                <button
                                                    type="button"
                                                    aria-label="이전 페이지"
                                                    disabled={activePage === 1}
                                                    onClick={() =>
                                                        setCurrentPage((page) =>
                                                            Math.max(1, page - 1)
                                                        )
                                                    }
                                                >
                                                    <ChevronLeft size={17} />
                                                </button>

                                                {Array.from(
                                                    { length: totalPages },
                                                    (_, index) => index + 1
                                                ).map((pageNumber) => (
                                                    <button
                                                        className={
                                                            activePage === pageNumber
                                                                ? "active"
                                                                : ""
                                                        }
                                                        type="button"
                                                        aria-current={
                                                            activePage === pageNumber
                                                                ? "page"
                                                                : undefined
                                                        }
                                                        onClick={() =>
                                                            setCurrentPage(pageNumber)
                                                        }
                                                        key={pageNumber}
                                                    >
                                                        {pageNumber}
                                                    </button>
                                                ))}

                                                <button
                                                    type="button"
                                                    aria-label="다음 페이지"
                                                    disabled={
                                                        activePage === totalPages
                                                    }
                                                    onClick={() =>
                                                        setCurrentPage((page) =>
                                                            Math.min(
                                                                totalPages,
                                                                page + 1
                                                            )
                                                        )
                                                    }
                                                >
                                                    <ChevronRight size={17} />
                                                </button>
                                            </nav>
                                        )}
                                    </div>

                                    <div className="custom-course-list">
                                        {paginatedCourses.map(
                                            (course, index) => (
                                                <article
                                                    className="custom-course-card"
                                                    key={course.courseId}
                                                >
                                                    <div className="custom-course-image">
                                                        <img
                                                            src={
                                                                course.thumbnailUrl ||
                                                                course.coverImageUrl ||
                                                                FALLBACK_IMAGES[
                                                                    index %
                                                                    FALLBACK_IMAGES.length
                                                                ]
                                                            }
                                                            alt=""
                                                            onError={(event) => {
                                                                event.currentTarget.onerror =
                                                                    null;
                                                                event.currentTarget.src =
                                                                    FALLBACK_IMAGES[
                                                                        index %
                                                                        FALLBACK_IMAGES.length
                                                                    ];
                                                            }}
                                                        />
                                                    </div>

                                                    <div className="custom-course-card-body">
                                                        <span
                                                            className={`custom-course-status ${(course.status || "COMPLETED").toLowerCase()}`}
                                                        >
                                                            {course.status === "DRAFT"
                                                                ? "작성 중"
                                                                : "작성 완료"}
                                                        </span>

                                                        <h2>{course.title}</h2>

                                                        <p className="custom-course-route">
                                                            <MapPin size={17} />
                                                            <span>
                                                                {getRouteText(course)}
                                                            </span>
                                                        </p>

                                                        <div className="custom-course-meta">
                                                            <span>
                                                                <MapPin size={16} />
                                                                {course.placeCount}개 장소
                                                            </span>
                                                            <span>
                                                                <Clock3 size={16} />
                                                                {formatDuration(
                                                                    course.totalCourseMinutes ?? course.totalCourseTimeMinutes
                                                                )}
                                                            </span>
                                                            <span>
                                                                <CalendarDays size={16} />
                                                                {formatDate(
                                                                    course.updatedAt ||
                                                                    course.createdAt
                                                                )}{" "}
                                                                수정
                                                            </span>
                                                        </div>
                                                    </div>

                                                    <div className="custom-course-actions">
                                                        <a
                                                            className="secondary"
                                                            href={`/courses/${course.courseId}`}
                                                        >
                                                            <Eye size={16} />
                                                            코스 보기
                                                        </a>
                                                        <button
                                                            className="danger"
                                                            type="button"
                                                            disabled={
                                                                deletingCourseId ===
                                                                course.courseId
                                                            }
                                                            onClick={() =>
                                                                handleDeleteCourse(course)
                                                            }
                                                        >
                                                            <Trash2 size={16} />
                                                            {deletingCourseId ===
                                                            course.courseId
                                                                ? "삭제 중"
                                                                : "삭제하기"}
                                                        </button>
                                                    </div>
                                                </article>
                                            )
                                        )}
                                    </div>

                                    <p className="custom-course-limit">
                                        코스는 최대 10개까지 만들 수 있어요.
                                    </p>
                                </>
                            )}
                    </section>
                </div>
            </main>

            <Footer />

            {editingCourse && (
                <div
                    className="custom-course-modal-backdrop"
                    role="presentation"
                    onMouseDown={(event) => {
                        if (event.target === event.currentTarget) {
                            closeEditModal();
                        }
                    }}
                >
                    <section
                        className="custom-course-modal"
                        role="dialog"
                        aria-modal="true"
                        aria-labelledby="custom-course-edit-title"
                    >
                        <header>
                            <div>
                                <p>코스 정보 수정</p>
                                <h2 id="custom-course-edit-title">
                                    직접 만든 코스 편집
                                </h2>
                            </div>
                            <button
                                type="button"
                                aria-label="편집 창 닫기"
                                title="닫기"
                                onClick={closeEditModal}
                                disabled={isSaving}
                            >
                                <X size={20} />
                            </button>
                        </header>

                        <form onSubmit={handleEditSubmit}>
                            <label>
                                <span>코스 제목</span>
                                <input
                                    type="text"
                                    value={editForm.title}
                                    maxLength={200}
                                    onChange={(event) =>
                                        setEditForm((current) => ({
                                            ...current,
                                            title: event.target.value,
                                        }))
                                    }
                                    autoFocus
                                />
                            </label>

                            <label>
                                <span>지역</span>
                                <input
                                    type="text"
                                    value={editForm.region}
                                    maxLength={100}
                                    onChange={(event) =>
                                        setEditForm((current) => ({
                                            ...current,
                                            region: event.target.value,
                                        }))
                                    }
                                />
                            </label>

                            <label>
                                <span>코스 설명</span>
                                <textarea
                                    value={editForm.description}
                                    maxLength={1000}
                                    rows={5}
                                    onChange={(event) =>
                                        setEditForm((current) => ({
                                            ...current,
                                            description: event.target.value,
                                        }))
                                    }
                                />
                            </label>

                            {editError && (
                                <p
                                    className="custom-course-modal-error"
                                    role="alert"
                                >
                                    {editError}
                                </p>
                            )}

                            <div className="custom-course-modal-actions">
                                <button
                                    className="secondary"
                                    type="button"
                                    onClick={closeEditModal}
                                    disabled={isSaving}
                                >
                                    취소
                                </button>
                                <button
                                    type="submit"
                                    disabled={isSaving}
                                >
                                    {isSaving
                                        ? "저장 중..."
                                        : "변경사항 저장"}
                                </button>
                            </div>
                        </form>
                    </section>
                </div>
            )}
        </div>
    );
}


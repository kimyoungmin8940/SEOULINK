import { useState } from "react";
import {
    Bot,
    CreditCard,
    Heart,
    LogOut,
    MapPin,
    Menu,
    MessageSquareText,
    Route,
    UserRound,
    X,
} from "lucide-react";

import { authStore } from "../../store/authStore";
import { requireLogin } from "../../utils/authGuard";
import logoSymbol from "../../assets/images/logo-symbol.png";
import logoText from "../../assets/images/logo-text.png";

// 헤더 가운데에 항상 노출하는 주요 기능 4개
const headerMenuItems = [
    { href: '/courses', label: '추천 코스', Icon: Heart, requiresLogin: true },
    { href: '/map-course', label: '지도 코스 만들기', Icon: MapPin, requiresLogin: true },
    { href: '/reviews', label: '방문 후기', Icon: MessageSquareText, requiresLogin: false },
    { href: '/chatbot', label: 'AI 여행 챗봇', Icon: Bot, requiresLogin: true },
];

// 햄버거 메뉴에는 전체 메뉴 7개를 표시
const sideMenuItems = [
    { href: '/courses', label: '추천 코스', Icon: Heart, requiresLogin: true },
    { href: '/mypage/courses', label: '내 코스 보기', Icon: Route, requiresLogin: true },
    { href: '/map-course', label: '지도 코스 만들기', Icon: MapPin, requiresLogin: true },
    { href: '/reviews', label: '방문 후기', Icon: MessageSquareText, requiresLogin: false },
    { href: '/mypage', label: '마이페이지', Icon: UserRound, requiresLogin: true },
    { href: '/chatbot', label: 'AI 여행 챗봇', Icon: Bot, requiresLogin: true },
    { href: '/payment', label: '이용권 / 결제', Icon: CreditCard, requiresLogin: true },
];

function getMemberDisplayName(member) {
    if (!member) {
        return "사용자";
    }

    return (
        member.nickname?.trim() ||
        member.name?.trim() ||
        member.loginId?.trim() ||
        member.email?.trim() ||
        "사용자"
    );
}

function Header({ variant = "simple" }) {
    const [isOpen, setIsOpen] = useState(false);

    const member = authStore.getMember();
    const isLoggedIn = Boolean(member?.memberId);
    const userName = getMemberDisplayName(member);

    const headerClassName =
        variant === "simple"
            ? "header simple-header"
            : "header";

    const closeMenu = () => {
        setIsOpen(false);
    };

    const handleMenuClick = (
        event,
        requiresLogin,
        href
    ) => {
        if (!requiresLogin || isLoggedIn) {
            closeMenu();
            return;
        }

        event.preventDefault();
        closeMenu();

        sessionStorage.setItem("loginReturnUrl", href);
        requireLogin();
    };

    const handleLogout = () => {
        authStore.clearMember();
        localStorage.removeItem("keepLogin");

        closeMenu();
        window.location.href = "/";
    };

    // 로그인 필수 메뉴는 비로그인 상태에서 이동하지 않고 로그인 안내를 실행합니다.
    const handleProtectedMenuClick = (event, requiresLogin, closeMenu = false) => {
        if (requiresLogin && !isLoggedIn) {
            event.preventDefault();

            if (closeMenu) {
                setIsOpen(false);
            }

            requireLogin();
            return;
        }

        if (closeMenu) {
            setIsOpen(false);
        }
    };

    return (
        <>
            <header className={headerClassName}>
                <div className="header-glass">
                    <a
                        href="/"
                        className="logo"
                        aria-label="메인페이지로 이동"
                    >
                        <img
                            className="logo-symbol"
                            src={logoSymbol}
                            alt=""
                            aria-hidden="true"
                        />

                        <img
                            className="logo-text-img"
                            src={logoText}
                            alt="SEOULLINK"
                        />
                    </a>

                    <nav className="header-main-nav" aria-label="주요 메뉴">
                        {headerMenuItems.map(({ href, label, Icon, requiresLogin }) => (
                            <a
                                className="header-main-nav-item"
                                href={href}
                                key={href}
                                onClick={(event) => handleProtectedMenuClick(event, requiresLogin)}
                            >
                                <Icon className="header-main-nav-icon" size={19} strokeWidth={2} />
                                <span>{label}</span>
                            </a>
                        ))}
                    </nav>

                    <div className="header-right">
                        {isLoggedIn ? (
                            <a
                                className="header-login-btn header-mypage-btn"
                                href="/mypage"
                                aria-label={`${userName}님의 마이페이지로 이동`}
                            >
                                <UserRound
                                    className="login-icon"
                                    size={19}
                                    strokeWidth={2.2}
                                />

                                <span>{userName}님</span>
                            </a>
                        ) : (
                            <a
                                className="header-login-btn"
                                href="/login"
                            >
                                <UserRound
                                    className="login-icon"
                                    size={19}
                                    strokeWidth={2.2}
                                />

                                <span>로그인 / 회원가입</span>
                            </a>
                        )}

                        <button
                            className="floating-menu-btn"
                            type="button"
                            aria-label={
                                isOpen
                                    ? "전체 메뉴 닫기"
                                    : "전체 메뉴 열기"
                            }
                            aria-expanded={isOpen}
                            aria-controls="seoulink-side-menu"
                            onClick={() =>
                                setIsOpen((previous) => !previous)
                            }
                        >
                            {isOpen ? (
                                <X
                                    size={21}
                                    strokeWidth={2.2}
                                />
                            ) : (
                                <Menu
                                    size={21}
                                    strokeWidth={2.2}
                                />
                            )}
                            <Menu className="floating-menu-icon" aria-hidden="true" />
                        </button>
                    </div>
                </div>
            </header>

            {isOpen && (
                <>
                    <button
                        type="button"
                        className="menu-backdrop"
                        aria-label="메뉴 닫기"
                        onClick={closeMenu}
                    />

                    <aside
                        id="seoulink-side-menu"
                        className="side-menu open"
                        aria-label="전체 메뉴"
                    >
                        <button
                            className="side-close"
                            type="button"
                            aria-label="전체 메뉴 닫기"
                            onClick={closeMenu}
                        >
                            <X
                                size={20}
                                strokeWidth={2.2}
                            />
                        </button>

                        <nav className="side-nav">
                            {sideMenuItems.map(({ href, label, Icon, requiresLogin }) => (
                                <a
                                    href={href}
                                    key={href}
                                    onClick={(event) => handleMenuClick(event, requiresLogin, href)}
                                >
                                    <Icon className="side-icon" size={16} strokeWidth={1.9} />
                                    <span>{label}</span>
                                </a>
                            ))}

                            {isLoggedIn && (
                                <>
                                    <div
                                        className="side-divider"
                                        aria-hidden="true"
                                    />

                                    <button
                                        className="side-logout-btn"
                                        type="button"
                                        onClick={handleLogout}
                                    >
                                        <LogOut
                                            className="side-icon"
                                            size={16}
                                            strokeWidth={1.9}
                                        />

                                        <span>로그아웃</span>
                                    </button>
                                </>
                            )}
                        </nav>
                    </aside>
                </>
            )}
        </>
    );
}

export default Header;

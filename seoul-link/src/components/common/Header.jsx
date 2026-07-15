// Header 컴포넌트는 페이지 상단 영역을 담당
// 구성: 로고, 주요 메뉴 4개, 로그인/회원가입 또는 마이페이지 버튼, 햄버거 메뉴 버튼, 오른쪽 사이드 메뉴
import { useState } from 'react';
import {
    Heart,
    MapPin,
    MessageSquareText,
    UserRound,
    Bot,
    CreditCard,
    LogOut,
    Route,
    Menu,
} from 'lucide-react';

import { isLoggedIn as checkIsLoggedIn, requireLogin } from '../../utils/authGuard';
import logoSymbol from '../../assets/images/logo-symbol.png';
import logoText from '../../assets/images/logo-text.png';

// 헤더 가운데에 항상 노출하는 주요 기능 4개
const headerMenuItems = [
    { href: '/courses', label: '추천 코스', Icon: Heart, requiresLogin: true },
    { href: '/map-course', label: '지도 코스 만들기', Icon: MapPin, requiresLogin: true },
    { href: '/reviews', label: '방문 후기', Icon: MessageSquareText, requiresLogin: false },
    { href: '/chatbot', label: 'AI 여행 챗봇', Icon: Bot, requiresLogin: true },
];

// 햄버거 메뉴에는 헤더로 이동하지 않은 개인 메뉴만 표시
const sideMenuItems = [
    { href: '/mypage/courses', label: '내 코스 보기', Icon: Route, requiresLogin: true },
    { href: '/mypage', label: '마이페이지', Icon: UserRound, requiresLogin: true },
    { href: '/payment', label: '이용권 / 결제', Icon: CreditCard, requiresLogin: true },
];

function getStoredUserName() {
    const directName =
        localStorage.getItem('nickname') ||
        localStorage.getItem('userName') ||
        localStorage.getItem('memberName') ||
        localStorage.getItem('name');

    if (directName) {
        return directName;
    }

    const userStorageKeys = ['user', 'member', 'loginUser'];

    for (const key of userStorageKeys) {
        const value = localStorage.getItem(key);

        if (!value) {
            continue;
        }

        try {
            const user = JSON.parse(value);
            const userName = user.nickname || user.userName || user.memberName || user.name || user.email || user.memberId;

            if (userName) {
                return userName;
            }
        } catch {
            return value;
        }
    }

    return '사용자';
}


const logoutStorageKeys = [
    'accessToken',
    'refreshToken',
    'token',
    'nickname',
    'userName',
    'memberName',
    'name',
    'user',
    'member',
    'loginUser',
    'recommendedCourses',
    'myRecommendedCourses',
    'recommendationCourses',
];

function removeLoginStorage() {
    logoutStorageKeys.forEach((key) => {
        localStorage.removeItem(key);
    });
}

function Header({ variant = 'simple' }) {
    // 사이드 메뉴가 열려 있는지 여부를 관리하는 상태
    // false: 메뉴 닫힘 / true: 메뉴 열림
    const [isOpen, setIsOpen] = useState(false);

    // 현재는 accessToken 또는 사용자 정보가 localStorage에 있으면 로그인 상태로 판단합니다.
    // 나중에 실제 로그인 API가 붙으면 로그인 성공 시 accessToken과 nickname을 저장하면 됩니다.
    const isLoggedIn = checkIsLoggedIn();
    const userName = getStoredUserName();

    // 메인페이지에서는 hero 이미지 위에 겹치는 simple-header를 사용하고,
    // 나머지 페이지에서는 기본 header를 사용합니다.
    const headerClassName = variant === 'simple' ? 'header simple-header' : 'header';

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
                    <a href="/" className="logo" aria-label="메인페이지로 이동">
                        <img className="logo-symbol" src={logoSymbol} alt="Seoulink 로고" />
                        <img className="logo-text-img" src={logoText} alt="SEOULINK" />
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
                            <a className="header-login-btn header-mypage-btn" href="/mypage" aria-label="마이페이지로 이동">
                                <UserRound className="login-icon" size={19} strokeWidth={2.2} />
                                <span>{userName}님</span>
                            </a>
                        ) : (
                            <a className="header-login-btn" href="/login">
                                <UserRound className="login-icon" size={19} strokeWidth={2.2} />
                                <span>로그인 / 회원가입</span>
                            </a>
                        )}

                        <button
                            className="floating-menu-btn"
                            type="button"
                            aria-label={isOpen ? '전체 메뉴 닫기' : '전체 메뉴 열기'}
                            aria-expanded={isOpen}
                            onClick={() => setIsOpen((prev) => !prev)}
                        >
                            <Menu className="floating-menu-icon" aria-hidden="true" />
                        </button>
                    </div>
                </div>
            </header>

            {isOpen && (
                <>
                    <div className="menu-backdrop" onClick={() => setIsOpen(false)} />

                    <aside className="side-menu open" aria-label="전체 메뉴">
                        <button
                            className="side-close"
                            type="button"
                            aria-label="전체 메뉴 닫기"
                            onClick={() => setIsOpen(false)}
                        >
                            ×
                        </button>

                        <nav className="side-nav">
                            {sideMenuItems.map(({ href, label, Icon, requiresLogin }) => (
                                <a
                                    href={href}
                                    key={href}
                                    onClick={(event) => handleProtectedMenuClick(event, requiresLogin, true)}
                                >
                                    <Icon className="side-icon" size={16} strokeWidth={1.9} />
                                    <span>{label}</span>
                                </a>
                            ))}

                            {isLoggedIn && (
                                <>
                                    <div className="side-divider" />

                                    <button
                                        className="side-logout-btn"
                                        type="button"
                                        onClick={() => {
                                            removeLoginStorage();
                                            setIsOpen(false);
                                            window.location.href = '/';
                                        }}
                                    >
                                        <LogOut className="side-icon" size={16} strokeWidth={1.9} />
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

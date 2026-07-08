// Header 컴포넌트는 페이지 상단 영역을 담당
// 구성: 로고, 로그인/회원가입 버튼, 햄버거 메뉴 버튼, 오른쪽 사이드 메뉴
import { useState } from 'react';
import {
    Heart,
    MapPin,
    MessageSquareText,
    UserRound,
    Bot,
    CreditCard,
} from 'lucide-react';

import logoSymbol from '../assets/images/logo-symbol.png';
import logoText from '../assets/images/logo-text.png';

function Header() {
    // 사이드 메뉴가 열려 있는지 여부를 관리하는 상태
    // false: 메뉴 닫힘 / true: 메뉴 열림
    const [isOpen, setIsOpen] = useState(false);

    return (
        <>
            {/*
                header 영역
                simple-header 클래스는 이 헤더가 hero 위에 겹쳐 보이도록 position:absolute 스타일을 적용
            */}
            <header className="header simple-header">
                {/*
                    header-glass는 반투명 흰색 배경과 blur 효과가 들어간 실제 헤더 박스
                    전체 헤더 안에서 로고는 왼쪽, 로그인/메뉴 버튼은 오른쪽으로 정렬
                */}
                <div className="header-glass">
                    {/*
                        로고 클릭 시 메인페이지로 이동
                        현재는 href="/"로 연결되어 있고, 라우터 적용 후에는 Link 컴포넌트로 바꿀 수 있음
                    */}
                    <a href="/" className="logo" aria-label="메인페이지로 이동">
                        <img className="logo-symbol" src={logoSymbol} alt="Seoulink 로고" />
                        <img className="logo-text-img" src={logoText} alt="SEOULINK" />
                    </a>

                    <div className="header-right">
                        {/*
                            로그인/회원가입 버튼
                            아직 실제 로그인 페이지 연결 전이라 클릭 이벤트는 넣지 않았고,
                            나중에 onClick 또는 라우터 링크만 연결하면 됨
                        */}
                        <button className="header-login-btn" type="button">
                            <UserRound className="login-icon" size={19} strokeWidth={2.2} />
                            <span>로그인 / 회원가입</span>
                        </button>

                        {/*
                            햄버거 메뉴 버튼
                            클릭하면 isOpen을 true로 바꿔 사이드 메뉴와 배경 오버레이를 보여줌
                        */}
                        <button
                            className="floating-menu-btn"
                            type="button"
                            aria-label="전체 메뉴 열기"
                            onClick={() => setIsOpen(true)}
                        >
                            ☰
                        </button>
                    </div>
                </div>
            </header>

            {/*
                isOpen이 true일 때만 사이드 메뉴를 렌더링
                React에서 조건부 렌더링을 사용한 구조
            */}
            {isOpen && (
                <>
                    {/*
                        메뉴 뒤쪽의 어두운 배경
                        이 영역을 클릭하면 메뉴가 닫히도록 처리했음
                    */}
                    <div className="menu-backdrop" onClick={() => setIsOpen(false)} />

                    {/*
                        오른쪽에서 나타나는 사이드 메뉴
                        open 클래스는 CSS에서 위치/애니메이션을 제어할 때 사용할 수 있음
                    */}
                    <aside className="side-menu open" aria-label="전체 메뉴">
                        {/* X 닫기 버튼입니다. 클릭 시 isOpen을 false로 변경 */}
                        <button
                            className="side-close"
                            type="button"
                            aria-label="전체 메뉴 닫기"
                            onClick={() => setIsOpen(false)}
                        >
                            ×
                        </button>

                        {/*
                            실제 메뉴 링크 목록
                            현재는 href="#" 임시 링크이며, 기능이 완성되면 각 페이지 경로로 변경하면 됨
                        */}
                        <nav className="side-nav">
                            <a href="#">
                                <Heart className="side-icon" size={16} strokeWidth={1.9} />
                                <span>추천 코스</span>
                            </a>

                            <a href="#">
                                <MapPin className="side-icon" size={16} strokeWidth={1.9} />
                                <span>지도 코스 만들기</span>
                            </a>

                            <a href="#">
                                <MessageSquareText className="side-icon" size={16} strokeWidth={1.9} />
                                <span>방문 후기</span>
                            </a>

                            <a href="#">
                                <UserRound className="side-icon" size={16} strokeWidth={1.9} />
                                <span>마이페이지</span>
                            </a>

                            <a href="#">
                                <Bot className="side-icon" size={16} strokeWidth={1.9} />
                                <span>AI 여행 챗봇</span>
                            </a>

                            <a href="#">
                                <CreditCard className="side-icon" size={16} strokeWidth={1.9} />
                                <span>이용권 / 결제</span>
                            </a>
                        </nav>
                    </aside>
                </>
            )}
        </>
    );
}

export default Header;

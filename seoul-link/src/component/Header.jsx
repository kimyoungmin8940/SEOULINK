import "./Header.css";

export default function Header() {
    return (
        <header className="header">
            <div className="logo" onClick={() => window.location.assign("/")}>
                <div className="logo-circle">SL</div>
                <span>SEOULLINK</span>
            </div>

            <nav>
                <a href="#">추천 코스</a>
                <a href="#">지도 코스 만들기</a>
                <a href="#">방문 후기</a>
                <a href="#">마이페이지</a>
            </nav>

            <button
                className="login-header-btn"
                onClick={() => window.location.assign("/login")}
            >
                로그인 / 회원가입
            </button>
        </header>
    );
}

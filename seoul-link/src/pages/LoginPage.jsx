import {
    Compass,
    Eye,
    Heart,
    LockKeyhole,
    Map,
    UserRound,
} from "lucide-react";
import "../styles/LoginPage.css";

function SeoulLinkLogo() {
    return (
        <div className="login-logo" aria-label="SEOULLINK">
            <div className="login-logo-mark">
                <div className="logo-skyline" />
                <div className="logo-river" />
            </div>

            <span className="logo-text">
        SEOUL<span>LINK</span>
      </span>
        </div>
    );
}

function FeatureItem({ icon: Icon, title, description }) {
    return (
        <div className="feature-item">
            <div className="feature-icon">
                <Icon size={34} strokeWidth={1.8} />
            </div>
            <strong>{title}</strong>
            <p>{description}</p>
        </div>
    );
}

export default function LoginPage() {
    return (
        <main className="login-page">
            <div className="login-background" />

            <div className="login-layout">
                <SeoulLinkLogo />

                <section className="login-main">
                    <section className="login-copy">
                        <h1>
                            오늘의 서울은,
                            <br />
                            당신의 <span>취향</span>으로
                            <br />
                            이어집니다
                        </h1>

                        <p className="login-subtitle">
                            감성 가득한 서울 여행,
                            <br />
                            나만의 코스로 발견해보세요.
                        </p>

                        <div className="feature-list">
                            <FeatureItem
                                icon={Compass}
                                title="나만의 코스 추천"
                                description="취향에 맞는 맞춤 여행"
                            />

                            <FeatureItem
                                icon={Map}
                                title="지도 기반 탐색"
                                description="서울의 명소를 한눈에"
                            />

                            <FeatureItem
                                icon={Heart}
                                title="즐겨찾기 & 기록"
                                description="나의 여행을 저장하고 관리"
                            />
                        </div>
                    </section>

                    <section className="login-card" aria-label="로그인">
                        <div className="login-card-header">
                            <h2>로그인</h2>
                            <p>SEOULLINK 계정으로 로그인하세요</p>
                        </div>

                        <form className="login-form">
                            <label className="input-group">
                                <span>아이디</span>

                                <div className="input-box">
                                    <UserRound size={21} />
                                    <input type="text" placeholder="아이디를 입력하세요" />
                                </div>
                            </label>

                            <label className="input-group">
                                <span>비밀번호</span>

                                <div className="input-box">
                                    <LockKeyhole size={20} />
                                    <input type="password" placeholder="비밀번호를 입력하세요" />

                                    <button
                                        type="button"
                                        className="icon-button"
                                        aria-label="비밀번호 보기"
                                    >
                                        <Eye size={21} />
                                    </button>
                                </div>
                            </label>

                            <button type="submit" className="login-button">
                                로그인
                            </button>

                            <div className="divider">
                                <span />
                                <em>또는</em>
                                <span />
                            </div>

                            <button type="button" className="join-button">
                                회원가입
                            </button>
                        </form>
                    </section>
                </section>
            </div>
        </main>
    );
}
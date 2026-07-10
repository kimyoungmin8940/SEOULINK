import { useState } from "react";
import {
    Compass,
    Eye,
    EyeOff,
    Heart,
    LockKeyhole,
    Map,
    UserRound,
} from "lucide-react";
import { login } from "../api/authApi";
import { authStore } from "../store/authStore";
import "../styles/LoginPage.css";

function SeoulLinkLogo() {
    return (
        <a href="/" className="login-logo" aria-label="SEOULLINK 메인페이지">
            <div className="login-logo-mark">
                <div className="logo-skyline" />
                <div className="logo-river" />
            </div>

            <span className="logo-text">
                SEOUL<span>LINK</span>
            </span>
        </a>
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
    const [loginId, setLoginId] = useState("");
    const [password, setPassword] = useState("");
    const [showPassword, setShowPassword] = useState(false);
    const [keepLogin, setKeepLogin] = useState(false);
    const [message, setMessage] = useState("");
    const [loading, setLoading] = useState(false);

    const submitLogin = async (event) => {
        event.preventDefault();

        if (!loginId.trim()) {
            setMessage("아이디를 입력해주세요.");
            return;
        }

        if (!password) {
            setMessage("비밀번호를 입력해주세요.");
            return;
        }

        try {
            setLoading(true);
            setMessage("");

            const member = await login({
                loginId: loginId.trim(),
                password,
            });

            authStore.setMember(member);

            if (keepLogin) {
                localStorage.setItem("keepLogin", "true");
            } else {
                localStorage.removeItem("keepLogin");
            }

            const returnUrl =
                sessionStorage.getItem("loginReturnUrl") || "/";

            sessionStorage.removeItem("loginReturnUrl");
            window.location.href = returnUrl;
        } catch (error) {
            setMessage(error.message);
        } finally {
            setLoading(false);
        }
    };

    const showSocialNotice = (provider) => {
        window.alert(
            `${provider} 로그인은 백엔드 OAuth 구현 후 연결될 예정입니다.`
        );
    };

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

                    <section
                        className="login-card"
                        aria-label="로그인"
                    >
                        <div className="login-card-header">
                            <h2>로그인</h2>
                            <p>SEOULLINK 계정으로 로그인하세요</p>
                        </div>

                        <form
                            className="login-form"
                            onSubmit={submitLogin}
                        >
                            <label className="input-group">
                                <span>아이디</span>

                                <div className="input-box">
                                    <UserRound size={21} />

                                    <input
                                        type="text"
                                        value={loginId}
                                        onChange={(event) =>
                                            setLoginId(event.target.value)
                                        }
                                        placeholder="아이디를 입력하세요"
                                        autoComplete="username"
                                        disabled={loading}
                                    />
                                </div>
                            </label>

                            <label className="input-group">
                                <span>비밀번호</span>

                                <div className="input-box">
                                    <LockKeyhole size={20} />

                                    <input
                                        type={
                                            showPassword
                                                ? "text"
                                                : "password"
                                        }
                                        value={password}
                                        onChange={(event) =>
                                            setPassword(event.target.value)
                                        }
                                        placeholder="비밀번호를 입력하세요"
                                        autoComplete="current-password"
                                        disabled={loading}
                                    />

                                    <button
                                        type="button"
                                        className="icon-button"
                                        aria-label={
                                            showPassword
                                                ? "비밀번호 숨기기"
                                                : "비밀번호 보기"
                                        }
                                        onClick={() =>
                                            setShowPassword(
                                                (previous) => !previous
                                            )
                                        }
                                    >
                                        {showPassword ? (
                                            <EyeOff size={21} />
                                        ) : (
                                            <Eye size={21} />
                                        )}
                                    </button>
                                </div>
                            </label>

                            <div className="login-option-row">
                                <label className="keep-login">
                                    <input
                                        type="checkbox"
                                        checked={keepLogin}
                                        onChange={(event) =>
                                            setKeepLogin(
                                                event.target.checked
                                            )
                                        }
                                    />
                                    로그인 상태 유지
                                </label>

                                <a href="/find-password">
                                    비밀번호 찾기
                                </a>
                            </div>

                            {message && (
                                <p className="login-error" role="alert">
                                    {message}
                                </p>
                            )}

                            <button
                                type="submit"
                                className="login-button"
                                disabled={loading}
                            >
                                {loading ? "로그인 중..." : "로그인"}
                            </button>

                            <div className="divider">
                                <span />
                                <em>또는</em>
                                <span />
                            </div>

                            <div className="social-login-list">
                                <button
                                    type="button"
                                    className="social-login kakao"
                                    onClick={() =>
                                        showSocialNotice("카카오")
                                    }
                                >
                                    카카오 로그인
                                </button>

                                <button
                                    type="button"
                                    className="social-login naver"
                                    onClick={() =>
                                        showSocialNotice("네이버")
                                    }
                                >
                                    네이버 로그인
                                </button>

                                <button
                                    type="button"
                                    className="social-login google"
                                    onClick={() =>
                                        showSocialNotice("Google")
                                    }
                                >
                                    Google 로그인
                                </button>
                            </div>

                            <div className="login-signup-guide">
                                <span>계정이 없으신가요?</span>
                                <a href="/signup">회원가입</a>
                            </div>
                        </form>
                    </section>
                </section>
            </div>
        </main>
    );
}
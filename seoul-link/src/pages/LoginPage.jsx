import { useState } from "react";
import { MapPinned } from "lucide-react";
import "../styles/LoginPage.css";
import "../styles/SignupPage.css";
import Header from "../components/common/Header";
import { apiPost } from "../api/client";
import { BACKEND_ORIGIN } from "../api/apiClient";
import { authStore } from "../store/authStore";
import loginIllustration from "../assets/images/seoul-line-art-transparent.png";
import kakaoLogo from "../assets/images/social/kakao.svg";
import naverLogo from "../assets/images/social/naver.svg";
import googleLogo from "../assets/images/social/google.svg";

export default function Login() {
    const [form, setForm] = useState({
        email: "",
        password: "",
    });

    const [error, setError] = useState("");

    const handleChange = (event) => {
        const { id, value } = event.target;

        setForm((previous) => ({
            ...previous,
            [id]: value,
        }));
    };

    const handleLogin = async () => {
        setError("");

        if (!form.email || !form.password) {
            setError("이메일과 비밀번호를 입력해주세요.");
            return;
        }

        try {
            const member = await apiPost("/members/login", {
                email: form.email,
                password: form.password,
            });

            authStore.setMember(member);

            window.alert("로그인되었습니다.");
            const returnUrl =
                sessionStorage.getItem("loginReturnUrl") || "/";
            sessionStorage.removeItem("loginReturnUrl");
            window.location.assign(returnUrl);
        } catch {
            setError("이메일 또는 비밀번호가 올바르지 않습니다.");
        }
    };

    const handleSubmit = (event) => {
        event.preventDefault();
        handleLogin();
    };

    return (
        <>
            <Header />

            <main className="login-page">
                <section className="login-layout">
                    <div className="login-intro">
                        <div className="login-intro-copy">
                            <p className="login-small">
                                <span aria-hidden="true">
                                    <MapPinned size={22} strokeWidth={1.9} />
                                </span>
                                로그인
                            </p>

                            <h1 className="login-title">
                                서울의 다양한 여행을
                                <br />
                                시작해보세요
                            </h1>

                            <strong>
                                나만의 방식으로 만나는 서울
                            </strong>

                            <p className="login-intro-description">
                                취향에 맞는 코스와 새로운 장소를
                                <br />
                                발견해보세요.
                            </p>
                        </div>

                        <img
                            className="login-illustration"
                            src={loginIllustration}
                            alt=""
                            aria-hidden="true"
                        />
                    </div>

                    <section
                        className="login-card"
                        aria-labelledby="login-form-title"
                    >
                        <h2 id="login-form-title" className="sr-only">
                            서울링크 로그인
                        </h2>

                        <form
                            className="login-form"
                            onSubmit={handleSubmit}
                        >
                            <div className="form-group">
                                <label htmlFor="email">이메일</label>
                                <input
                                    id="email"
                                    type="email"
                                    autoComplete="email"
                                    value={form.email}
                                    onChange={handleChange}
                                    placeholder="이메일을 입력해주세요"
                                />
                            </div>

                            <div className="form-group">
                                <label htmlFor="password">
                                    비밀번호
                                </label>
                                <input
                                    id="password"
                                    type="password"
                                    autoComplete="current-password"
                                    value={form.password}
                                    onChange={handleChange}
                                    placeholder="비밀번호를 입력해주세요"
                                />
                            </div>

                            {error && (
                                <p className="login-error" role="alert">
                                    {error}
                                </p>
                            )}

                            <div className="login-option">
                                <button
                                    className="find-password"
                                    type="button"
                                    onClick={() =>
                                        window.location.assign(
                                            "/find-password"
                                        )
                                    }
                                >
                                    비밀번호 찾기
                                </button>
                            </div>

                            <button
                                className="login-btn"
                                type="submit"
                            >
                                로그인
                            </button>

                            <div className="divider">
                                <span>또는</span>
                            </div>

                            <div className="social-login-list">
                                <button
                                    className="social-btn kakao"
                                    type="button"
                                    onClick={() =>
                                        window.location.assign(
                                            `${BACKEND_ORIGIN}/oauth2/authorization/kakao`
                                        )
                                    }
                                >
                                    <img src={kakaoLogo} alt="" />
                                    <span>카카오로 로그인</span>
                                </button>

                                <button
                                    className="social-btn naver"
                                    type="button"
                                    onClick={() =>
                                        window.location.assign(
                                            `${BACKEND_ORIGIN}/oauth2/authorization/naver`
                                        )
                                    }
                                >
                                    <img src={naverLogo} alt="" />
                                    <span>네이버로 로그인</span>
                                </button>

                                <button
                                    className="social-btn google"
                                    type="button"
                                    onClick={() =>
                                        window.location.assign(
                                            `${BACKEND_ORIGIN}/oauth2/authorization/google`
                                        )
                                    }
                                >
                                    <img src={googleLogo} alt="" />
                                    <span>Google로 로그인</span>
                                </button>
                            </div>

                            <p className="signup">
                                계정이 없으신가요?
                                <button
                                    type="button"
                                    onClick={() =>
                                        window.location.assign("/signup")
                                    }
                                >
                                    회원가입
                                </button>
                            </p>
                        </form>
                    </section>
                </section>
            </main>
        </>
    );
}

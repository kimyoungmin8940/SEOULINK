import { useState } from "react";
import "../styles/LoginPage.css";
import Header from "../component/Header";
import PageBackground from "../component/PageBackground";
import { apiPost } from "../api/client";

export default function Login() {

    const [form, setForm] = useState({
        email: "",
        password: "",
    });

    const [error, setError] = useState("");

    const handleChange = (e) => {
        const { id, value } = e.target;

        setForm((prev) => ({
            ...prev,
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

            localStorage.setItem("member", JSON.stringify(member));

            alert("로그인되었습니다.");
            window.location.assign("/");
        } catch (err) {
            setError("이메일 또는 비밀번호가 올바르지 않습니다.");
        }
    };

    return (
        <PageBackground>
            <Header />

            <main className="login-container">
                <section className="login-card">
                    <p className="login-small">로그인</p>

                    <h1 className="login-title">
                        서울의 다양한 여행을
                        <br />
                        시작해보세요
                    </h1>

                    <div className="form-group">
                        <label htmlFor="email">이메일</label>
                        <input
                            id="email"
                            type="email"
                            value={form.email}
                            onChange={handleChange}
                            placeholder="이메일을 입력해주세요"
                        />
                    </div>

                    <div className="form-group">
                        <label htmlFor="password">비밀번호</label>
                        <input
                            id="password"
                            type="password"
                            value={form.password}
                            onChange={handleChange}
                            placeholder="비밀번호를 입력해주세요"
                        />
                    </div>

                    {error && <p className="login-error">{error}</p>}

                    <div className="login-option">
                        <label className="keep-login">
                            <input type="checkbox" />
                            <span>로그인 상태 유지</span>
                        </label>

                        <button
                            className="find-password"
                            type="button"
                            onClick={() => window.location.assign("/find-password")}
                        >
                            비밀번호 찾기
                        </button>

                    </div>

                    <button
                        className="login-btn"
                        type="button"
                        onClick={handleLogin}
                    >
                        로그인
                    </button>

                    <div className="divider">
                        <span>또는</span>
                    </div>

                    <button className="social-btn kakao" type="button">
                        카카오 로그인
                    </button>

                    <button className="social-btn naver" type="button">
                        네이버 로그인
                    </button>

                    <button className="social-btn google" type="button">
                        Google 로그인
                    </button>

                    <p className="signup">
                        계정이 없으신가요?
                        <span onClick={() => window.location.assign("/signup")}>
                            회원가입
                        </span>
                    </p>
                </section>
            </main>
        </PageBackground>
    );
}

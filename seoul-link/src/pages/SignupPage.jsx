import { useState } from "react";
import Header from "../components/common/Header";
import PageBackground from "../component/PageBackground";
import { apiPost } from "../api/client";
import "../styles/SignupPage.css";

export default function SignupPage() {

    const [form, setForm] = useState({
        email: "",
        password: "",
        passwordCheck: "",
        name: "",
        nickname: "",
        phone: "",
    });

    const [message, setMessage] = useState("");
    const [error, setError] = useState("");

    const handleChange = (e) => {
        const { id, value } = e.target;

        setForm((prev) => ({
            ...prev,
            [id]: value,
        }));
    };

    const handleSignup = async (e) => {
        e.preventDefault();

        setMessage("");
        setError("");

        if (!form.email || !form.password || !form.name) {
            setError("이메일, 비밀번호, 이름은 필수입니다.");
            return;
        }

        if (form.password !== form.passwordCheck) {
            setError("비밀번호가 일치하지 않습니다.");
            return;
        }

        try {
            await apiPost("/members/signup", {
                email: form.email,
                password: form.password,
                name: form.name,
                nickname: form.nickname,
                phone: form.phone,
            });

            setMessage("회원가입이 완료되었습니다.");

            setTimeout(() => {
                window.location.assign("/login");
            }, 1000);
        } catch (err) {
            setError(err.message || "회원가입 중 오류가 발생했습니다.");
        }
    };

    return (
        <>
            <Header />
            <PageBackground>
            <main className="signup-container">
                <section className="signup-card">
                    <p className="signup-small">회원가입</p>

                    <h1 className="signup-title">
                        서울 여행을 함께할
                        <br />
                        계정을 만들어보세요
                    </h1>

                    <form className="signup-form" onSubmit={handleSignup}>
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

                        <div className="form-group">
                            <label htmlFor="passwordCheck">비밀번호 확인</label>
                            <input
                                id="passwordCheck"
                                type="password"
                                value={form.passwordCheck}
                                onChange={handleChange}
                                placeholder="비밀번호를 다시 입력해주세요"
                            />
                        </div>

                        <div className="form-row">
                            <div className="form-group">
                                <label htmlFor="name">이름</label>
                                <input
                                    id="name"
                                    type="text"
                                    value={form.name}
                                    onChange={handleChange}
                                    placeholder="이름"
                                />
                            </div>

                            <div className="form-group">
                                <label htmlFor="nickname">닉네임</label>
                                <input
                                    id="nickname"
                                    type="text"
                                    value={form.nickname}
                                    onChange={handleChange}
                                    placeholder="닉네임"
                                />
                            </div>
                        </div>

                        <div className="form-group">
                            <label htmlFor="phone">전화번호</label>
                            <input
                                id="phone"
                                type="tel"
                                value={form.phone}
                                onChange={handleChange}
                                placeholder="010-0000-0000"
                            />
                        </div>

                        {message && <p className="signup-message success">{message}</p>}
                        {error && <p className="signup-message error">{error}</p>}

                        <button className="signup-btn" type="submit">
                            회원가입
                        </button>
                    </form>

                    <p className="login-link">
                        이미 계정이 있으신가요?
                        <span onClick={() => window.location.assign("/login")}>로그인</span>
                    </p>
                </section>
            </main>
            </PageBackground>
        </>
    );
}

import { useState } from "react";
import { apiPost } from "../api/client";

function SimpleAuthPage({ onLogin }) {
    const [mode, setMode] = useState("login");
    const [form, setForm] = useState({
        email: "",
        password: "",
        name: "",
        nickname: "",
        phone: "",
    });
    const [message, setMessage] = useState("");

    const change = (e) => {
        setForm({
            ...form,
            [e.target.name]: e.target.value,
        });
    };

    const login = async () => {
        try {
            setMessage("");

            const member = await apiPost("/members/login", {
                email: form.email,
                password: form.password,
            });

            onLogin(member);
        } catch (error) {
            setMessage(error.message);
        }
    };

    const signup = async () => {
        try {
            setMessage("");

            const member = await apiPost("/members/signup", {
                email: form.email,
                password: form.password,
                name: form.name,
                nickname: form.nickname,
                phone: form.phone,
            });

            alert("회원가입 완료");
            onLogin(member);
        } catch (error) {
            setMessage(error.message);
        }
    };

    return (
        <main className="simple-page">
            <section className="simple-box">
                <h1>SEOULINK</h1>

                <div className="tabs">
                    <button
                        type="button"
                        className={mode === "login" ? "active" : ""}
                        onClick={() => setMode("login")}
                    >
                        로그인
                    </button>

                    <button
                        type="button"
                        className={mode === "signup" ? "active" : ""}
                        onClick={() => setMode("signup")}
                    >
                        회원가입
                    </button>
                </div>

                <input
                    name="email"
                    placeholder="이메일"
                    value={form.email}
                    onChange={change}
                />

                <input
                    name="password"
                    type="password"
                    placeholder="비밀번호"
                    value={form.password}
                    onChange={change}
                />

                {mode === "signup" && (
                    <>
                        <input
                            name="name"
                            placeholder="이름"
                            value={form.name}
                            onChange={change}
                        />

                        <input
                            name="nickname"
                            placeholder="닉네임"
                            value={form.nickname}
                            onChange={change}
                        />

                        <input
                            name="phone"
                            placeholder="전화번호"
                            value={form.phone}
                            onChange={change}
                        />
                    </>
                )}

                {message && <p className="error">{message}</p>}

                {mode === "login" ? (
                    <button type="button" className="primary" onClick={login}>
                        로그인
                    </button>
                ) : (
                    <button type="button" className="primary" onClick={signup}>
                        회원가입
                    </button>
                )}

                <div className="social-area">
                    <button type="button" disabled>
                        카카오 로그인 준비중
                    </button>
                    <button type="button" disabled>
                        네이버 로그인 준비중
                    </button>
                    <button type="button" disabled>
                        구글 로그인 준비중
                    </button>
                </div>
            </section>
        </main>
    );
}

export default SimpleAuthPage;
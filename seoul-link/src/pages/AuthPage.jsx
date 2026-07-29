import { useState } from "react";
import { postData } from "../api/client";

function AuthPage({ onLogin }) {
    const [mode, setMode] = useState("login");

    const [loginForm, setLoginForm] = useState({
        email: "",
        password: "",
    });

    const [signupForm, setSignupForm] = useState({
        email: "",
        password: "",
        name: "",
        nickname: "",
        phone: "",
    });

    const changeLogin = (event) => {
        const { name, value } = event.target;
        setLoginForm({ ...loginForm, [name]: value });
    };

    const changeSignup = (event) => {
        const { name, value } = event.target;
        setSignupForm({ ...signupForm, [name]: value });
    };

    const submitLogin = async (event) => {
        event.preventDefault();

        try {
            const member = await postData("/members/login", loginForm);
            onLogin(member);
        } catch (error) {
            alert("로그인에 실패했습니다.");
        }
    };

    const submitSignup = async (event) => {
        event.preventDefault();

        if (!signupForm.email.includes("@")) {
            alert("이메일 형식이 올바르지 않습니다.");
            return;
        }

        if (signupForm.password.length < 8) {
            alert("비밀번호는 8자 이상 입력해주세요.");
            return;
        }

        try {
            const member = await postData("/members/signup", signupForm);
            alert("회원가입이 완료되었습니다.");
            onLogin(member);
        } catch (error) {
            alert("회원가입에 실패했습니다.");
        }
    };

    return (
        <main className="authPage">
            <section className="authHero">
                <div className="authText">
                    <span>SEOULINK ACCOUNT</span>
                    <h1>
                        서울 여행의 시작을<br />
                        당신의 계정에 담아보세요
                    </h1>
                    <p>취향 검사 결과, 저장한 코스, 결제 내역과 챗봇 기록을 한 곳에서 관리합니다.</p>
                </div>

                <div className="authCard">
                    <div className="tabButtons">
                        <button
                            className={mode === "login" ? "active" : ""}
                            onClick={() => setMode("login")}
                        >
                            로그인
                        </button>
                        <button
                            className={mode === "signup" ? "active" : ""}
                            onClick={() => setMode("signup")}
                        >
                            회원가입
                        </button>
                    </div>

                    {mode === "login" && (
                        <form onSubmit={submitLogin} className="formBox">
                            <label>이메일</label>
                            <input
                                name="email"
                                value={loginForm.email}
                                onChange={changeLogin}
                                placeholder="seoulink@email.com"
                            />

                            <label>비밀번호</label>
                            <input
                                name="password"
                                type="password"
                                value={loginForm.password}
                                onChange={changeLogin}
                                placeholder="비밀번호"
                            />

                            <button className="primaryButton">로그인</button>
                        </form>
                    )}

                    {mode === "signup" && (
                        <form onSubmit={submitSignup} className="formBox">
                            <label>이메일</label>
                            <input
                                name="email"
                                value={signupForm.email}
                                onChange={changeSignup}
                                placeholder="seoulink@email.com"
                            />

                            <label>비밀번호</label>
                            <input
                                name="password"
                                type="password"
                                value={signupForm.password}
                                onChange={changeSignup}
                                placeholder="8자 이상"
                            />

                            <label>이름</label>
                            <input
                                name="name"
                                value={signupForm.name}
                                onChange={changeSignup}
                                placeholder="홍길동"
                            />

                            <label>닉네임</label>
                            <input
                                name="nickname"
                                value={signupForm.nickname}
                                onChange={changeSignup}
                                placeholder="서울여행자"
                            />

                            <label>휴대폰</label>
                            <input
                                name="phone"
                                value={signupForm.phone}
                                onChange={changeSignup}
                                placeholder="010-0000-0000"
                            />

                            <button className="primaryButton">회원가입</button>
                        </form>
                    )}
                </div>
            </section>
        </main>
    );
}

export default AuthPage;
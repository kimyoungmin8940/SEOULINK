<<<<<<< HEAD
import { useState } from "react";
import "./App.css";

const API = "http://localhost:8080/api";

function App() {
    const savedMember = localStorage.getItem("member");
    const [member, setMember] = useState(savedMember ? JSON.parse(savedMember) : null);
    const [mode, setMode] = useState("login");
    const [message, setMessage] = useState("");
    const [idChecked, setIdChecked] = useState(false);

    const [form, setForm] = useState({
        loginId: "",
        password: "",
        passwordConfirm: "",
        name: "",
        nickname: "",
        email: "",
    });

    const change = (e) => {
        setForm({ ...form, [e.target.name]: e.target.value });
        if (e.target.name === "loginId") {
            setIdChecked(false);
        }
    };

    const request = async (path, options = {}) => {
        const response = await fetch(`${API}${path}`, options);
        const text = await response.text();

        if (!response.ok) {
            throw new Error(text || "요청 실패");
        }

        return text ? JSON.parse(text) : null;
    };

    const checkLoginId = async () => {
        try {
            if (!form.loginId) {
                alert("아이디를 입력해주세요.");
                return;
            }

            const available = await request(`/members/check-login-id?loginId=${form.loginId}`);

            if (available) {
                alert("사용 가능한 아이디입니다.");
                setIdChecked(true);
            } else {
                alert("이미 사용 중인 아이디입니다.");
                setIdChecked(false);
            }
        } catch (error) {
            setMessage(error.message);
        }
    };

    const signup = async () => {
        try {
            setMessage("");

            if (!idChecked) {
                alert("아이디 중복검사를 해주세요.");
                return;
            }

            if (form.password !== form.passwordConfirm) {
                alert("비밀번호 확인이 일치하지 않습니다.");
                return;
            }

            const data = await request("/members/signup", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                },
                body: JSON.stringify(form),
            });

            alert("회원가입 완료");
            setMember(data);
            localStorage.setItem("member", JSON.stringify(data));
        } catch (error) {
            setMessage(error.message);
        }
    };

    const login = async () => {
        try {
            setMessage("");

            const data = await request("/members/login", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                },
                body: JSON.stringify({
                    loginId: form.loginId,
                    password: form.password,
                }),
            });

            setMember(data);
            localStorage.setItem("member", JSON.stringify(data));
        } catch (error) {
            setMessage(error.message);
        }
    };

    const logout = () => {
        setMember(null);
        localStorage.removeItem("member");
    };

    if (member) {
        return (
            <main className="page">
                <section className="box">
                    <h1>마이페이지 확인</h1>
                    <p>회원번호: {member.memberId}</p>
                    <p>아이디: {member.loginId}</p>
                    <p>이름: {member.name}</p>
                    <p>닉네임: {member.nickname || "-"}</p>
                    <p>이메일: {member.email}</p>

                    <button onClick={logout}>로그아웃</button>
                </section>
            </main>
        );
    }

    return (
        <main className="page">
            <section className="box">
                <h1>SEOULINK</h1>

                <div className="tabs">
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

                <label>아이디</label>
                <div className="row">
                    <input
                        name="loginId"
                        value={form.loginId}
                        onChange={change}
                        placeholder="아이디"
                    />

                    {mode === "signup" && (
                        <button type="button" onClick={checkLoginId}>
                            중복검사
                        </button>
                    )}
                </div>

                <label>비밀번호</label>
                <input
                    name="password"
                    type="password"
                    value={form.password}
                    onChange={change}
                    placeholder="영문+숫자 8자 이상"
                />

                {mode === "signup" && (
                    <>
                        <label>비밀번호 확인</label>
                        <input
                            name="passwordConfirm"
                            type="password"
                            value={form.passwordConfirm}
                            onChange={change}
                            placeholder="비밀번호 다시 입력"
                        />

                        <label>이름</label>
                        <input
                            name="name"
                            value={form.name}
                            onChange={change}
                            placeholder="이름"
                        />

                        <label>닉네임</label>
                        <input
                            name="nickname"
                            value={form.nickname}
                            onChange={change}
                            placeholder="닉네임"
                        />

                        <label>이메일</label>
                        <input
                            name="email"
                            value={form.email}
                            onChange={change}
                            placeholder="email@example.com"
                        />
                    </>
                )}

                {message && <pre className="error">{message}</pre>}

                {mode === "login" ? (
                    <button className="primary" onClick={login}>
                        로그인
                    </button>
                ) : (
                    <button className="primary" onClick={signup}>
                        회원가입
                    </button>
                )}
            </section>
        </main>
    );
}

export default App;
=======
// App.css에서 전체 스타일 묶음(styles/index.css)을 불러옴
// 이 프로젝트는 컴포넌트별 CSS 파일을 styles 폴더에 나누고,
// App.css -> styles/index.css 순서로 한 번에 import하는 구조
import './App.css';

// 실제 페이지 이동 구조는 routes/Router.jsx에서 관리
import Router from './routes/Router';

function App() {
    // 프로젝트의 최상위 화면
    // Router가 현재 주소에 맞는 페이지를 골라서 렌더링함
    return <Router />;
}

export default App;
>>>>>>> origin/goeun

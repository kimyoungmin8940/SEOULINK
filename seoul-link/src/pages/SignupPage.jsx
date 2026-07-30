import { useState } from "react";
import { Eye, EyeOff, MapPinned } from "lucide-react";
import Header from "../components/common/Header";
import { apiPost } from "../api/client";
import signupIllustration from "../assets/images/seoul-line-art-transparent.png";
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
    const [showPassword, setShowPassword] = useState(false);
    const [showPasswordCheck, setShowPasswordCheck] = useState(false);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [message, setMessage] = useState("");
    const [error, setError] = useState("");

    const handleChange = (event) => {
        const { id, value } = event.target;

        setForm((previous) => ({
            ...previous,
            [id]: value,
        }));
    };

    const handleSignup = async (event) => {
        event.preventDefault();
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

        setIsSubmitting(true);

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
        } catch (requestError) {
            setError(
                requestError.message ||
                    "회원가입 중 오류가 발생했습니다."
            );
        } finally {
            setIsSubmitting(false);
        }
    };

    return (
        <>
            <Header />

            <main className="signup-page">
                <section className="signup-layout">
                    <div className="signup-intro">
                        <div className="signup-intro-copy">
                            <p className="signup-small">
                                <span aria-hidden="true">
                                    <MapPinned
                                        size={22}
                                        strokeWidth={1.9}
                                    />
                                </span>
                                회원가입
                            </p>

                            <h1 className="signup-title">
                                서울의 다양한 여행을
                                <br />
                                시작해보세요
                            </h1>

                            <strong>
                                나만의 방식으로 만나는 서울
                            </strong>

                            <p className="signup-intro-description">
                                취향에 맞는 코스와 새로운 장소를
                                <br />
                                발견해보세요.
                            </p>
                        </div>

                        <img
                            className="signup-illustration"
                            src={signupIllustration}
                            alt=""
                            aria-hidden="true"
                        />
                    </div>

                    <section
                        className="signup-card"
                        aria-labelledby="signup-form-title"
                    >
                        <div className="signup-card-inner">
                            <h2 id="signup-form-title">
                                회원가입
                            </h2>

                            <form
                                className="signup-form"
                                onSubmit={handleSignup}
                            >
                                <div className="form-group">
                                    <label htmlFor="email">
                                        이메일
                                    </label>
                                    <input
                                        id="email"
                                        type="email"
                                        autoComplete="email"
                                        value={form.email}
                                        onChange={handleChange}
                                        placeholder="이메일을 입력해주세요"
                                    />
                                </div>

                                <div className="form-row">
                                    <div className="form-group">
                                        <label htmlFor="password">
                                            비밀번호
                                        </label>
                                        <div className="password-field">
                                            <input
                                                id="password"
                                                type={
                                                    showPassword
                                                        ? "text"
                                                        : "password"
                                                }
                                                autoComplete="new-password"
                                                value={form.password}
                                                onChange={handleChange}
                                                placeholder="영문, 숫자 포함 8자 이상"
                                            />
                                            <button
                                                className="password-toggle"
                                                type="button"
                                                aria-label={
                                                    showPassword
                                                        ? "비밀번호 숨기기"
                                                        : "비밀번호 보기"
                                                }
                                                aria-pressed={showPassword}
                                                onClick={() =>
                                                    setShowPassword(
                                                        (visible) =>
                                                            !visible
                                                    )
                                                }
                                            >
                                                {showPassword ? (
                                                    <EyeOff
                                                        size={19}
                                                        strokeWidth={1.8}
                                                    />
                                                ) : (
                                                    <Eye
                                                        size={19}
                                                        strokeWidth={1.8}
                                                    />
                                                )}
                                            </button>
                                        </div>
                                    </div>

                                    <div className="form-group">
                                        <label htmlFor="passwordCheck">
                                            비밀번호 확인
                                        </label>
                                        <div className="password-field">
                                            <input
                                                id="passwordCheck"
                                                type={
                                                    showPasswordCheck
                                                        ? "text"
                                                        : "password"
                                                }
                                                autoComplete="new-password"
                                                value={form.passwordCheck}
                                                onChange={handleChange}
                                                placeholder="비밀번호를 다시 입력해주세요"
                                            />
                                            <button
                                                className="password-toggle"
                                                type="button"
                                                aria-label={
                                                    showPasswordCheck
                                                        ? "비밀번호 확인 숨기기"
                                                        : "비밀번호 확인 보기"
                                                }
                                                aria-pressed={
                                                    showPasswordCheck
                                                }
                                                onClick={() =>
                                                    setShowPasswordCheck(
                                                        (visible) =>
                                                            !visible
                                                    )
                                                }
                                            >
                                                {showPasswordCheck ? (
                                                    <EyeOff
                                                        size={19}
                                                        strokeWidth={1.8}
                                                    />
                                                ) : (
                                                    <Eye
                                                        size={19}
                                                        strokeWidth={1.8}
                                                    />
                                                )}
                                            </button>
                                        </div>
                                    </div>
                                </div>

                                <div className="form-row">
                                    <div className="form-group">
                                        <label htmlFor="name">
                                            이름
                                        </label>
                                        <input
                                            id="name"
                                            type="text"
                                            autoComplete="name"
                                            value={form.name}
                                            onChange={handleChange}
                                            placeholder="이름을 입력해주세요"
                                        />
                                    </div>

                                    <div className="form-group">
                                        <label htmlFor="nickname">
                                            닉네임
                                        </label>
                                        <input
                                            id="nickname"
                                            type="text"
                                            autoComplete="nickname"
                                            value={form.nickname}
                                            onChange={handleChange}
                                            placeholder="닉네임을 입력해주세요"
                                        />
                                    </div>
                                </div>

                                <div className="form-group">
                                    <label htmlFor="phone">
                                        휴대폰 번호
                                    </label>
                                    <input
                                        id="phone"
                                        type="tel"
                                        inputMode="tel"
                                        autoComplete="tel"
                                        value={form.phone}
                                        onChange={handleChange}
                                        placeholder="숫자만 입력해주세요"
                                    />
                                </div>

                                <div
                                    className="signup-feedback"
                                    aria-live="polite"
                                >
                                    {message && (
                                        <p className="signup-message success">
                                            {message}
                                        </p>
                                    )}
                                    {error && (
                                        <p
                                            className="signup-message error"
                                            role="alert"
                                        >
                                            {error}
                                        </p>
                                    )}
                                </div>

                                <button
                                    className="signup-btn"
                                    type="submit"
                                    disabled={isSubmitting}
                                >
                                    {isSubmitting
                                        ? "가입 중..."
                                        : "회원가입"}
                                </button>

                                <p className="login-link">
                                    이미 계정이 있으신가요?
                                    <button
                                        type="button"
                                        onClick={() =>
                                            window.location.assign(
                                                "/login"
                                            )
                                        }
                                    >
                                        로그인
                                    </button>
                                </p>
                            </form>
                        </div>
                    </section>
                </section>
            </main>
        </>
    );
}

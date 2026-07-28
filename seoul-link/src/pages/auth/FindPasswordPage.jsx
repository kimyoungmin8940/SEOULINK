import { useState } from "react";
import {
    Check,
    CheckCircle2,
    Eye,
    EyeOff,
    MapPinned,
} from "lucide-react";
import Header from "../../components/common/Header";
import {
    resetPassword,
    verifyPasswordResetMember,
} from "../../api/authApi";
import passwordResetIllustration from "../../assets/images/signup-seoul-line-art.png";
import "../../styles/FindPasswordPage.css";

const INITIAL_FORM = {
    name: "",
    email: "",
    newPassword: "",
    passwordConfirm: "",
};

export default function FindPasswordPage() {
    const [form, setForm] = useState(INITIAL_FORM);
    const [isVerified, setIsVerified] = useState(false);
    const [isVerifying, setIsVerifying] = useState(false);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [showPassword, setShowPassword] = useState(false);
    const [showPasswordConfirm, setShowPasswordConfirm] =
        useState(false);
    const [error, setError] = useState("");

    const handleChange = (event) => {
        const { id, value } = event.target;

        setForm((previous) => ({
            ...previous,
            [id]: value,
        }));
        setError("");

        if (id === "name" || id === "email") {
            setIsVerified(false);
        }
    };

    const handleVerify = async () => {
        setError("");

        if (!form.name.trim() || !form.email.trim()) {
            setError("이름과 이메일을 모두 입력해주세요.");
            return;
        }

        setIsVerifying(true);

        try {
            await verifyPasswordResetMember({
                name: form.name.trim(),
                email: form.email.trim(),
            });
            setIsVerified(true);
            window.alert("회원 정보가 확인되었습니다.");
        } catch (requestError) {
            setIsVerified(false);
            setError(
                requestError.message ||
                    "일치하는 회원 정보를 찾을 수 없습니다."
            );
        } finally {
            setIsVerifying(false);
        }
    };

    const handleSubmit = async (event) => {
        event.preventDefault();
        setError("");

        if (!isVerified) {
            setError("먼저 회원 정보를 확인해주세요.");
            return;
        }

        if (form.newPassword.length < 8) {
            setError("새 비밀번호는 8자 이상 입력해주세요.");
            return;
        }

        if (form.newPassword !== form.passwordConfirm) {
            setError("새 비밀번호가 서로 일치하지 않습니다.");
            return;
        }

        setIsSubmitting(true);

        try {
            await resetPassword({
                name: form.name.trim(),
                email: form.email.trim(),
                newPassword: form.newPassword,
            });

            window.alert(
                "비밀번호가 변경되었습니다. 새 비밀번호로 로그인해주세요."
            );
            window.location.assign("/login");
        } catch (requestError) {
            setError(
                requestError.message ||
                    "비밀번호 변경 중 오류가 발생했습니다."
            );
        } finally {
            setIsSubmitting(false);
        }
    };

    return (
        <>
            <Header />

            <main className="password-reset-page">
                <section className="password-reset-layout">
                    <div className="password-reset-intro">
                        <div className="password-reset-intro-copy">
                            <p className="password-reset-small">
                                <span aria-hidden="true">
                                    <MapPinned
                                        size={22}
                                        strokeWidth={1.9}
                                    />
                                </span>
                                비밀번호 찾기
                            </p>

                            <h1 className="password-reset-title">
                                서울의 다양한 여행을
                                <br />
                                시작해보세요
                            </h1>

                            <strong>
                                나만의 방식으로 만나는 서울
                            </strong>

                            <p className="password-reset-description">
                                취향에 맞는 코스와 새로운 장소를
                                <br />
                                발견해보세요.
                            </p>
                        </div>

                        <img
                            className="password-reset-illustration"
                            src={passwordResetIllustration}
                            alt=""
                            aria-hidden="true"
                        />
                    </div>

                    <section
                        className="password-reset-card"
                        aria-labelledby="password-reset-title"
                    >
                        <div className="password-reset-card-inner">
                            <h2 id="password-reset-title">
                                비밀번호 재설정
                            </h2>
                            <p className="password-reset-guide">
                                가입한 회원 정보를 확인하고 새로운
                                비밀번호를 설정해주세요.
                            </p>

                            <form
                                className="password-reset-form"
                                onSubmit={handleSubmit}
                            >
                                <div className="password-reset-field">
                                    <label htmlFor="name">이름</label>
                                    <input
                                        id="name"
                                        type="text"
                                        autoComplete="name"
                                        value={form.name}
                                        onChange={handleChange}
                                        placeholder="이름을 입력해주세요"
                                    />
                                </div>

                                <div className="password-reset-field">
                                    <label htmlFor="email">이메일</label>
                                    <div className="password-reset-email-row">
                                        <input
                                            id="email"
                                            type="email"
                                            autoComplete="email"
                                            value={form.email}
                                            onChange={handleChange}
                                            placeholder="이메일을 입력해주세요"
                                        />
                                        <button
                                            className="password-reset-verify"
                                            type="button"
                                            disabled={
                                                isVerified ||
                                                isVerifying
                                            }
                                            onClick={handleVerify}
                                        >
                                            {isVerified && (
                                                <Check
                                                    size={18}
                                                    strokeWidth={2.2}
                                                />
                                            )}
                                            {isVerified
                                                ? "확인 완료"
                                                : isVerifying
                                                  ? "확인 중..."
                                                  : "확인"}
                                        </button>
                                    </div>
                                </div>

                                {isVerified && (
                                    <div
                                        className="password-reset-success"
                                        role="status"
                                    >
                                        <CheckCircle2
                                            size={19}
                                            strokeWidth={2.2}
                                        />
                                        회원 정보가 확인되었습니다.
                                    </div>
                                )}

                                <div className="password-reset-field">
                                    <label htmlFor="newPassword">
                                        새로운 비밀번호
                                    </label>
                                    <div className="password-reset-password">
                                        <input
                                            id="newPassword"
                                            type={
                                                showPassword
                                                    ? "text"
                                                    : "password"
                                            }
                                            autoComplete="new-password"
                                            value={form.newPassword}
                                            onChange={handleChange}
                                            placeholder="영문, 숫자 포함 8자 이상"
                                            disabled={!isVerified}
                                        />
                                        <button
                                            type="button"
                                            aria-label={
                                                showPassword
                                                    ? "비밀번호 숨기기"
                                                    : "비밀번호 보기"
                                            }
                                            aria-pressed={showPassword}
                                            disabled={!isVerified}
                                            onClick={() =>
                                                setShowPassword(
                                                    (visible) =>
                                                        !visible
                                                )
                                            }
                                        >
                                            {showPassword ? (
                                                <EyeOff size={19} />
                                            ) : (
                                                <Eye size={19} />
                                            )}
                                        </button>
                                    </div>
                                </div>

                                <div className="password-reset-field">
                                    <label htmlFor="passwordConfirm">
                                        새로운 비밀번호 확인
                                    </label>
                                    <div className="password-reset-password">
                                        <input
                                            id="passwordConfirm"
                                            type={
                                                showPasswordConfirm
                                                    ? "text"
                                                    : "password"
                                            }
                                            autoComplete="new-password"
                                            value={form.passwordConfirm}
                                            onChange={handleChange}
                                            placeholder="새로운 비밀번호를 다시 입력해주세요"
                                            disabled={!isVerified}
                                        />
                                        <button
                                            type="button"
                                            aria-label={
                                                showPasswordConfirm
                                                    ? "비밀번호 확인 숨기기"
                                                    : "비밀번호 확인 보기"
                                            }
                                            aria-pressed={
                                                showPasswordConfirm
                                            }
                                            disabled={!isVerified}
                                            onClick={() =>
                                                setShowPasswordConfirm(
                                                    (visible) =>
                                                        !visible
                                                )
                                            }
                                        >
                                            {showPasswordConfirm ? (
                                                <EyeOff size={19} />
                                            ) : (
                                                <Eye size={19} />
                                            )}
                                        </button>
                                    </div>
                                </div>

                                <div
                                    className="password-reset-feedback"
                                    aria-live="polite"
                                >
                                    {error && (
                                        <p role="alert">{error}</p>
                                    )}
                                </div>

                                <button
                                    className="password-reset-submit"
                                    type="submit"
                                    disabled={
                                        !isVerified || isSubmitting
                                    }
                                >
                                    {isSubmitting
                                        ? "변경 중..."
                                        : "변경 완료"}
                                </button>

                                <button
                                    className="password-reset-login-link"
                                    type="button"
                                    onClick={() =>
                                        window.location.assign("/login")
                                    }
                                >
                                    로그인으로 돌아가기
                                </button>
                            </form>
                        </div>
                    </section>
                </section>
            </main>
        </>
    );
}

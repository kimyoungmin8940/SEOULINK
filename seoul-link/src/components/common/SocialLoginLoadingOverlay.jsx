import { Sparkles } from "lucide-react";
import "../../styles/social-login-loading.css";

export default function SocialLoginLoadingOverlay() {
    return (
        <div
            className="social-login-loading"
            role="status"
            aria-live="polite"
        >
            <section className="social-login-loading__dialog">
                <div className="social-login-loading__icon">
                    <Sparkles size={27} strokeWidth={1.9} />
                </div>

                <span className="social-login-loading__eyebrow">
                    SEOULINK ACCOUNT
                </span>

                <h2>소셜 로그인 중이에요</h2>

                <p>
                    회원 정보를 확인하고 마이페이지를
                    <br />
                    준비하고 있어요.
                </p>

                <div
                    className="social-login-loading__dots"
                    aria-hidden="true"
                >
                    <i />
                    <i />
                    <i />
                </div>

                <small>
                    화면을 닫지 않고 잠시만 기다려 주세요.
                </small>
            </section>
        </div>
    );
}
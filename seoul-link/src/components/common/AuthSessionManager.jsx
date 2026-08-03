import { useEffect } from "react";
import { authStore } from "../../store/authStore";

const AUTO_LOGOUT_MESSAGE =
    "로그인 후 30분이 지나 자동으로 로그아웃되었습니다.";

export default function AuthSessionManager() {
    useEffect(() => {
        let timeoutId;

        const clearLogoutTimer = () => {
            if (timeoutId) {
                window.clearTimeout(timeoutId);
                timeoutId = undefined;
            }
        };

        const checkSession = () => {
            clearLogoutTimer();

            // 로그인 정보가 없으면 타이머를 만들지 않음
            if (!authStore.hasStoredSession()) {
                return;
            }

            const remainingTime =
                authStore.getSessionExpiresAt() - Date.now();

            // 30분이 지났으면 로그아웃
            if (
                !Number.isFinite(remainingTime) ||
                remainingTime <= 0
            ) {
                authStore.clearMember();

                window.alert(AUTO_LOGOUT_MESSAGE);
                window.location.replace("/login");
                return;
            }

            // 남은 시간 후 다시 검사
            timeoutId = window.setTimeout(
                checkSession,
                remainingTime
            );
        };

        const handleVisibilityChange = () => {
            if (document.visibilityState === "visible") {
                checkSession();
            }
        };

        checkSession();

        window.addEventListener("auth-changed", checkSession);
        window.addEventListener("focus", checkSession);

        document.addEventListener(
            "visibilitychange",
            handleVisibilityChange
        );

        return () => {
            clearLogoutTimer();

            window.removeEventListener(
                "auth-changed",
                checkSession
            );

            window.removeEventListener(
                "focus",
                checkSession
            );

            document.removeEventListener(
                "visibilitychange",
                handleVisibilityChange
            );
        };
    }, []);

    return null;
}
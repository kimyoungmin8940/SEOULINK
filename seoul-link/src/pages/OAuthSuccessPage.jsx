import { useEffect } from "react";
import { authStore } from "../store/authStore";

export default function OAuthSuccessPage() {
    useEffect(() => {
        const params = new URLSearchParams(window.location.search);
        const memberId = Number(params.get("memberId"));
        const email = params.get("email");
        const name = params.get("name");

        if (!memberId || !email) {
            window.location.replace("/login");
            return;
        }
        authStore.setMember({ memberId, email, name, loginType: params.get("loginType") || "SOCIAL" });
        const returnUrl = sessionStorage.getItem("loginReturnUrl") || "/";
        sessionStorage.removeItem("loginReturnUrl");
        window.location.replace(returnUrl);
    }, []);

    return <p>소셜 로그인 처리 중입니다.</p>;
}

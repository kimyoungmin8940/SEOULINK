import { useEffect } from "react";
import { authStore } from "../store/authStore";

export default function OAuthSuccessPage() {
    useEffect(() => {
        const params = new URLSearchParams(window.location.search);

        const memberId = Number(params.get("memberId"));
        const email = params.get("email");
        const name = params.get("name");
        const loginType =
            params.get("loginType") || "SOCIAL";

        if (!memberId || !email) {
            window.location.replace("/login");
            return;
        }

        authStore.setMember({
            memberId,
            email,
            name,
            loginType,
        });

        const returnUrl = sessionStorage.getItem("loginReturnUrl") || "/";
        sessionStorage.removeItem("loginReturnUrl");

        if (returnUrl.startsWith("/mypage")) {
            sessionStorage.setItem(
                "showSocialLoginLoading",
                "true"
            );
        } else {
            sessionStorage.removeItem("showSocialLoginLoading");
        }

        window.location.replace(returnUrl);
    }, []);

    return null;
}

import { authStore } from "../store/authStore";

export function isLoggedIn() {
    return authStore.isLoggedIn();
}

export function requireLogin(
    message = "로그인이 필요한 서비스입니다. 로그인 후 이용해주세요."
) {
    if (isLoggedIn()) {
        return true;
    }

    window.alert(message);

    const returnUrl =
        window.location.pathname +
        window.location.search +
        window.location.hash;

    sessionStorage.setItem("loginReturnUrl", returnUrl);
    window.location.href = "/login";

    return false;
}

export function handleProtectedLinkClick(event, message) {
    if (isLoggedIn()) {
        return true;
    }

    event.preventDefault();
    requireLogin(message);
    return false;
}
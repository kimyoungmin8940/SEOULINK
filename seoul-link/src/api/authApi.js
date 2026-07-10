import { apiClient } from "./apiClient";

export function login(data) {
    return apiClient.post("/members/login", data);
}

export function signup(data) {
    return apiClient.post("/members/signup", data);
}

export function checkLoginId(loginId) {
    return apiClient.get(
        `/members/check-login-id?loginId=${encodeURIComponent(loginId)}`
    );
}

export function checkEmail(email) {
    return apiClient.get(
        `/members/check-email?email=${encodeURIComponent(email)}`
    );
}

export function checkNickname(nickname) {
    return apiClient.get(
        `/members/check-nickname?nickname=${encodeURIComponent(nickname)}`
    );
}
import { apiClient } from "./apiClient";

export function login(data) {
    return apiClient.post("/members/login", data);
}

export function signup(data) {
    return apiClient.post("/members/signup", data);
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

export function verifyPasswordResetMember(data) {
    return apiClient.post("/members/password-reset/verify", data);
}

export function resetPassword(data) {
    return apiClient.post("/members/password-reset", data);
}

export function withdrawMember(memberId) {
    return apiClient.delete(`/members/${memberId}`);
}

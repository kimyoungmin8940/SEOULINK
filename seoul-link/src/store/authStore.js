const MEMBER_STORAGE_KEY = "member";
const AUTH_EXPIRES_AT_KEY = "authExpiresAt";

// 로그인 유지 시간: 30분
export const AUTH_SESSION_DURATION_MS = 30 * 60 * 1000;

const LOGIN_STORAGE_KEYS = [
    MEMBER_STORAGE_KEY,
    AUTH_EXPIRES_AT_KEY,
    "accessToken",
    "refreshToken",
    "token",
    "nickname",
    "userName",
    "memberName",
    "name",
    "user",
    "loginUser",
    "memberId",
];

function dispatchAuthChanged() {
    window.dispatchEvent(new Event("auth-changed"));
}

function removeLoginStorage(storage) {
    LOGIN_STORAGE_KEYS.forEach((key) => {
        storage.removeItem(key);
    });
}

export const authStore = {
    getMember() {
        const savedMember =
            sessionStorage.getItem(MEMBER_STORAGE_KEY);

        if (!savedMember) {
            return null;
        }

        const expiresAt = this.getSessionExpiresAt();

        if (
            !Number.isFinite(expiresAt) ||
            Date.now() >= expiresAt
        ) {
            return null;
        }

        try {
            return JSON.parse(savedMember);
        } catch {
            sessionStorage.removeItem(MEMBER_STORAGE_KEY);
            sessionStorage.removeItem(AUTH_EXPIRES_AT_KEY);
            return null;
        }
    },

    setMember(member) {
        sessionStorage.setItem(
            MEMBER_STORAGE_KEY,
            JSON.stringify(member)
        );

        sessionStorage.setItem(
            AUTH_EXPIRES_AT_KEY,
            String(Date.now() + AUTH_SESSION_DURATION_MS)
        );

        // 기존 localStorage 로그인 정보 삭제
        removeLoginStorage(localStorage);

        dispatchAuthChanged();
    },

    clearMember() {
        removeLoginStorage(sessionStorage);
        removeLoginStorage(localStorage);

        dispatchAuthChanged();
    },

    getSessionExpiresAt() {
        return Number(
            sessionStorage.getItem(AUTH_EXPIRES_AT_KEY)
        );
    },

    hasStoredSession() {
        return Boolean(
            sessionStorage.getItem(MEMBER_STORAGE_KEY)
        );
    },

    isLoggedIn() {
        const member = this.getMember();
        return Boolean(member?.memberId);
    },
};
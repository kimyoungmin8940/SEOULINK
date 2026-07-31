const MEMBER_STORAGE_KEY = "member";

export const authStore = {
    getMember() {
        const savedMember = localStorage.getItem(MEMBER_STORAGE_KEY);

        if (!savedMember) {
            return null;
        }

        try {
            return JSON.parse(savedMember);
        } catch {
            localStorage.removeItem(MEMBER_STORAGE_KEY);
            return null;
        }
    },

    setMember(member) {
        localStorage.setItem(
            MEMBER_STORAGE_KEY,
            JSON.stringify(member)
        );

        window.dispatchEvent(new Event("auth-changed"));
    },

    clearMember() {
        localStorage.removeItem(MEMBER_STORAGE_KEY);
        window.dispatchEvent(new Event("auth-changed"));
    },

    isLoggedIn() {
        const member = this.getMember();
        return Boolean(member?.memberId);
    },
};
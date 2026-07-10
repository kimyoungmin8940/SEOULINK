// 로그인 토큰 저장/삭제를 한 곳에서 관리하는 간단한 저장소입니다.
// Zustand 같은 상태관리 라이브러리를 쓰게 되면 이 파일을 교체하면 됩니다.

export const authStore = {
    getToken() {
        return localStorage.getItem('accessToken');
    },
    setToken(token) {
        localStorage.setItem('accessToken', token);
    },
    clearToken() {
        localStorage.removeItem('accessToken');
    },
};

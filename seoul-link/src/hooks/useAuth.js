// 로그인 상태를 여러 화면에서 공통으로 쓸 때 확장할 커스텀 훅 자리입니다.
export function useAuth() {
    const accessToken = localStorage.getItem('accessToken');

    return {
        isLoggedIn: Boolean(accessToken),
        accessToken,
    };
}

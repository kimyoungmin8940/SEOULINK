// authGuard.js
// 프론트 임시 단계에서 로그인 여부를 확인하고,
// 로그인이 필요한 기능을 눌렀을 때 안내 후 로그인 페이지로 보내는 공통 유틸입니다.

export function isLoggedIn() {
    return Boolean(
        localStorage.getItem('accessToken') ||
        localStorage.getItem('nickname') ||
        localStorage.getItem('userName') ||
        localStorage.getItem('memberName') ||
        localStorage.getItem('user') ||
        localStorage.getItem('member') ||
        localStorage.getItem('loginUser'),
    );
}

export function requireLogin(message = '로그인이 필요한 서비스입니다. 로그인 후 이용해주세요.') {
    if (isLoggedIn()) {
        return true;
    }

    alert(message);
    window.location.href = '/login';
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

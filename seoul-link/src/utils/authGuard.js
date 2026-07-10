// authGuard.js
// 프론트 임시 단계에서 로그인 여부를 확인하고,
// 로그인이 필요한 기능을 눌렀을 때 안내 후 로그인 페이지로 보내는 공통 유틸입니다.

// React 개발 모드의 StrictMode에서는 컴포넌트가 두 번 검사될 수 있습니다.
// 라우터에서 requireLogin()이 연속 호출되더라도 알림과 이동이 한 번만 실행되도록 막습니다.
let isLoginRedirecting = false;

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

    // 이미 첫 번째 로그인 안내가 실행되어 로그인 페이지로 이동 중이라면
    // StrictMode나 중복 이벤트로 들어온 두 번째 호출은 무시합니다.
    if (isLoginRedirecting) {
        return false;
    }

    isLoginRedirecting = true;
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

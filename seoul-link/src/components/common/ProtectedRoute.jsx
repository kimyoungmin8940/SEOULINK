/**
 * TODO: react-router-dom 구조로 전환할 때 로그인 전용 경로를 감쌀 컴포넌트입니다.
 *
 * 현재 프로젝트는 `routes/Router.jsx`가 pathname을 직접 확인하고
 * `utils/authGuard.js`로 로그인 여부를 검사하므로 이 파일을 사용하지 않습니다.
 * 추후 BrowserRouter를 적용하면 로그인 상태에 따라 children/Outlet을 보여주거나
 * 로그인 페이지로 Navigate하도록 구현합니다. 이동 전 현재 주소를 state에 담아
 * 로그인 성공 후 원래 화면으로 돌아갈 수 있게 하는 것이 좋습니다.
 */

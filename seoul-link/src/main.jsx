// React 애플리케이션의 시작점(entry point)
// Vite가 index.html을 읽은 뒤, 여기에서 App 컴포넌트를 #root 영역에 렌더링
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';

// 전역 CSS 파일
// 이 파일 안에서 styles/index.css를 통해 폰트, 변수, 섹션별 CSS를 한 번에 불러옴
import './index.css';

// 실제 화면 구조를 담당하는 최상위 컴포넌트
import App from './App.jsx';

// index.html에 있는 <div id="root"></div>를 찾아 React 화면을 붙임
// StrictMode는 개발 중 잠재적인 문제를 더 쉽게 찾도록 도와주는 React 검사 모드
createRoot(document.getElementById('root')).render(
    <StrictMode>
        <App />
    </StrictMode>,
);

console.log(
    "카카오 지도 키 로드:",
    Boolean(import.meta.env.VITE_KAKAO_MAP_KEY)
);

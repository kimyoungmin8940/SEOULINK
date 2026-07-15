// App.css에서 전체 스타일 묶음(styles/index.css)을 불러옴
// 이 프로젝트는 컴포넌트별 CSS 파일을 styles 폴더에 나누고,
// App.css -> styles/index.css 순서로 한 번에 import하는 구조
import './App.css';

// 실제 페이지 이동 구조는 routes/Router.jsx에서 관리
import Router from './routes/Router';
import PhotoFilterDefinitions from './components/common/PhotoFilterDefinitions';

function App() {
    // 프로젝트의 최상위 화면
    // Router가 현재 주소에 맞는 페이지를 골라서 렌더링함
    return (
        <>
            {/* 전체 콘텐츠 사진이 공통으로 참조하는 메인 색 필터 */}
            <PhotoFilterDefinitions />
            <Router />
        </>
    );
}

export default App;

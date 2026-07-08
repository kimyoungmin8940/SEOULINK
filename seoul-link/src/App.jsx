// App.css에서 전체 스타일 묶음(styles/index.css)을 불러옴
// 이 프로젝트는 컴포넌트별 CSS 파일을 styles 폴더에 나누고,
// App.css -> styles/index.css 순서로 한 번에 import하는 구조
import './App.css';

// 현재는 메인 페이지만 구현되어 있으므로 Home 페이지만 연결
// 나중에 react-router-dom을 적용하면 여기에서 Routes/Route 구조로 확장
import Home from './pages/Home';

function App() {
    // 프로젝트의 최상위 화면
    // 지금은 바로 Home을 보여주지만, 추후 로그인/마이페이지/추천코스 등 라우팅이 들어갈 수 있음
    return <Home />;
}

export default App;

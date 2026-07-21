import './App.css';
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
  return <Router />;
}

export default App;

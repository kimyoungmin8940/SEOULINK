import { ConnectedLayout } from '../../components/common/ConnectedLayout';
function MyFavoritesPage() { return <ConnectedLayout title="찜 목록" description="현재 DB 구조에는 찜 테이블과 API가 없어 이 기능은 연결 대상에서 제외했습니다."><div className="connected-state">찜 기능을 사용하려면 FAVORITE 테이블과 /api/favorites API를 추가해야 합니다.</div></ConnectedLayout>; }
export default MyFavoritesPage;

import PagePlaceholder from '../components/common/PagePlaceholder';

function NotFoundPage() {
    return (
        <PagePlaceholder
            title="페이지를 찾을 수 없습니다"
            description="주소가 잘못되었거나 아직 연결되지 않은 화면입니다."
            links={[{ href: '/', label: '메인으로 이동' }]}
        />
    );
}

export default NotFoundPage;

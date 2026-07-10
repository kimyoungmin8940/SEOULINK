import PagePlaceholder from '../../components/common/PagePlaceholder';

function FindPasswordPage() {
    return (
        <PagePlaceholder
            title="비밀번호 찾기"
            description="이메일 인증 또는 임시 비밀번호 발급 기능을 연결할 자리입니다."
            links={[{ href: '/login', label: '로그인으로 이동' }]}
        />
    );
}

export default FindPasswordPage;

import PagePlaceholder from '../../components/common/PagePlaceholder';

function LoginPage() {
    return (
        <PagePlaceholder
            title="로그인"
            description="회원가입, 로그인, JWT 연동 화면이 들어갈 자리입니다."
            links={[{ href: '/signup', label: '회원가입' }, { href: '/find-password', label: '비밀번호 찾기' }]}
        />
    );
}

export default LoginPage;

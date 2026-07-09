import PagePlaceholder from '../../components/common/PagePlaceholder';

function SignupPage() {
    return (
        <PagePlaceholder
            title="회원가입"
            description="아이디 중복 확인, 이메일 인증, 비밀번호 암호화 흐름을 연결할 자리입니다."
            links={[{ href: '/login', label: '로그인으로 이동' }]}
        />
    );
}

export default SignupPage;

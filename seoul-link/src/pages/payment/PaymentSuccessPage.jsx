import PagePlaceholder from '../../components/common/PagePlaceholder';

function PaymentSuccessPage() {
    return (
        <PagePlaceholder
            title="결제 성공"
            description="결제가 완료된 뒤 보여줄 화면입니다."
            links={[{ href: '/chatbot', label: '챗봇 사용하기' }, { href: '/mypage', label: '마이페이지' }]}
        />
    );
}

export default PaymentSuccessPage;

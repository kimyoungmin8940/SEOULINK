import PagePlaceholder from '../../components/common/PagePlaceholder';

function PaymentPage() {
    return (
        <PagePlaceholder
            title="이용권 / 결제"
            description="토스페이먼츠, 카카오페이 등 결제 API를 연결할 자리입니다."
            links={[{ href: '/chatbot', label: '챗봇 보기' }]}
        />
    );
}

export default PaymentPage;

import PagePlaceholder from '../../components/common/PagePlaceholder';

function PaymentFailPage() {
    return (
        <PagePlaceholder
            title="결제 실패"
            description="결제가 실패하거나 취소되었을 때 보여줄 화면입니다."
            links={[{ href: '/payment', label: '다시 결제하기' }]}
        />
    );
}

export default PaymentFailPage;

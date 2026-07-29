import PagePlaceholder from '../../components/common/PagePlaceholder';

function PaymentFailPage() {
  // 토스 결제 취소·실패 후 사용자가 다시 결제를 시도할 수 있도록 안내한다.
  return (
    <PagePlaceholder
      title="결제 실패"
      description="결제가 실패하거나 취소되었을 때 보여주는 화면입니다."
      links={[{ href: '/payment', label: '다시 결제하기' }]}
    />
  );
}

export default PaymentFailPage;

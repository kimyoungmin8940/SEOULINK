import { useEffect } from 'react';
import PagePlaceholder from '../../components/common/PagePlaceholder';
import { recordPaymentFailure } from '../../api/paymentApi';

function PaymentFailPage() {
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const orderId = params.get('orderId');
    const code = params.get('code') || '';
    const reason = params.get('message') || '결제 승인에 실패했습니다.';

    if (!orderId) return;

    // 토스의 사용자 취소 코드는 취소, 나머지는 실패 상태로 구분해 결제 이력에 남긴다.
    recordPaymentFailure({
      orderId,
      reason,
      canceled: code === 'PAY_PROCESS_CANCELED',
    }).catch(() => {});
  }, []);

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

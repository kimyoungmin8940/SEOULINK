import { useEffect, useState } from 'react';
import { CheckCircle2, LoaderCircle } from 'lucide-react';
import Header from '../../components/common/Header';
import { confirmPayment } from '../../api/paymentApi';

function PaymentSuccessPage() {
  const [state, setState] = useState('loading');
  const [message, setMessage] = useState('결제를 확인하고 이용권을 활성화하고 있습니다.');
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const paymentKey = params.get('paymentKey'); const orderId = params.get('orderId'); const amount = Number(params.get('amount'));
    if (!paymentKey || !orderId || !Number.isFinite(amount)) { setState('error'); setMessage('결제 결과 정보가 올바르지 않습니다.'); return; }
    confirmPayment({ paymentKey, orderId, amount }).then(() => { setState('success'); setMessage('결제가 완료되었습니다. 이제 AI 여행 플래너를 이용해 보세요.'); }).catch((error) => { setState('error'); setMessage(error?.message || '결제 승인에 실패했습니다. 고객센터로 문의해 주세요.'); });
  }, []);
  return <main className="payment-result-page"><Header variant="simple" /><section className="payment-result-card">{state === 'loading' ? <LoaderCircle className="result-loader" /> : <CheckCircle2 />}<h1>{state === 'success' ? '결제가 완료되었습니다' : state === 'loading' ? '결제를 확인하고 있습니다' : '결제 확인이 필요합니다'}</h1><p>{message}</p>{state === 'success' ? <a href="/chatbot">AI 여행 플래너 시작하기</a> : state === 'error' && <a href="/payment">다시 결제하기</a>}</section></main>;
}
export default PaymentSuccessPage;

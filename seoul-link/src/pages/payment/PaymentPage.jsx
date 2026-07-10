import { useMemo, useState } from 'react';
import * as PortOne from '@portone/browser-sdk/v2';
import { CalendarDays, Check, Clock3, CreditCard, LockKeyhole, MapPinned, Plane, RefreshCw } from 'lucide-react';
import Header from '../../components/common/Header';
import { confirmPayment, requestPayment as createPaymentOrder } from '../../api/paymentApi';
import { authStore } from '../../store/authStore';
import heroSeoul from '../../assets/images/hero-seoul-main.png';

const passes = [
  { id: 'day', eyebrow: 'SEOUL DAY PASS', name: '서울 하루권', days: 1, price: 2900 },
  { id: 'week', eyebrow: 'SEOUL WEEK PASS', name: '서울 일주일권', days: 7, price: 7900, popular: true },
  { id: 'month', eyebrow: 'SEOUL MONTH PASS', name: '서울 한달권', days: 30, price: 19900 },
];

const paymentMethods = [
  { id: 'CARD', label: '신용 / 체크카드', active: true },
  { id: 'KAKAO', label: '카카오페이' },
  { id: 'NAVER', label: '네이버페이' },
  { id: 'TOSS', label: '토스페이' },
];

const pad = (value) => String(value).padStart(2, '0');
const toInputDate = (date) => `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
const formatDate = (date) => `${date.getFullYear()}.${pad(date.getMonth() + 1)}.${pad(date.getDate())} (${['일','월','화','수','목','금','토'][date.getDay()]})`;

function PaymentPage() {
  const [selected, setSelected] = useState(passes[1]);
  const [startDate, setStartDate] = useState(toInputDate(new Date()));
  const [method, setMethod] = useState('CARD');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const start = useMemo(() => new Date(`${startDate}T00:00:00`), [startDate]);
  const end = useMemo(() => {
    const value = new Date(start);
    value.setDate(value.getDate() + selected.days - 1);
    return value;
  }, [start, selected.days]);

  const handlePayment = async () => {
    const member = authStore.getMember();
    if (!member?.memberId) return setError('로그인 후 구매할 수 있습니다.');
    if (!import.meta.env.VITE_PORTONE_CHANNEL_KEY) return setError('포트원 테스트 채널 키를 설정해 주세요.');

    const paymentId = `SEOULLINK_${crypto.randomUUID()}`;
    setLoading(true);
    setError('');

    try {
      await createPaymentOrder({
        memberId: member.memberId,
        productName: `${selected.name} · ${selected.days}일`,
        amount: selected.price,
        paymentMethod: method,
        paymentProvider: 'PORTONE',
        orderId: paymentId,
      });

      const response = await PortOne.requestPayment({
        storeId: import.meta.env.VITE_PORTONE_STORE_ID,
        channelKey: import.meta.env.VITE_PORTONE_CHANNEL_KEY,
        paymentId,
        orderName: `${selected.name} · ${selected.days}일`,
        totalAmount: selected.price,
        currency: 'KRW',
        payMethod: 'CARD',
        customer: {
          customerId: String(member.memberId),
          fullName: member.name,
          email: member.email,
        },
      });

      if (response?.code != null) throw new Error(response.message || '결제가 취소되었거나 실패했습니다.');
      await confirmPayment(paymentId);
      window.location.assign('/payment/success');
    } catch (e) {
      setError(e?.message || '결제 처리 중 문제가 발생했습니다.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <main className="pass-payment-page" style={{ '--seoul-hero': `url(${heroSeoul})` }}>
      <Header variant="simple" />
      <div className="pass-payment-overlay" />
      <section className="pass-payment-layout">
        <div className="pass-content">
          <div className="pass-intro">
            <span>AI 여행 플래너</span>
            <h1>서울 AI 챗봇과 함께<br />나만의 여행을 완성하세요</h1>
            <p>여행 기간을 선택하고 AI 챗봇으로<br />맞춤 코스부터 일정 관리까지 편리하게 이용해 보세요.</p>
          </div>

          <div className="pass-ticket-grid">
            {passes.map((pass) => (
              <button type="button" key={pass.id} className={`pass-ticket ${selected.id === pass.id ? 'selected' : ''}`} onClick={() => setSelected(pass)}>
                {selected.id === pass.id && <span className="ticket-check"><Check size={18} /></span>}
                <small>{pass.eyebrow}</small>
                <strong>{pass.name} · {pass.days}일</strong>
                <div className="ticket-divider" />
                <span>이용 기간</span>
                <p>{formatDate(start)}<br />~ {formatDate(new Date(new Date(start).setDate(start.getDate() + pass.days - 1)))}</p>
                <footer><i /><b>{pass.price.toLocaleString()}<em>원</em></b></footer>
              </button>
            ))}
          </div>

          <div className="pass-benefits">
            <h2>AI 챗봇 이용<br />포함 기능</h2>
            <div><span><MapPinned /></span><p><b>맞춤 코스</b><small>관심사와 여행 스타일에 맞는<br />나만의 코스를 추천받아요.</small></p></div>
            <div><span><RefreshCw /></span><p><b>실시간 일정 수정</b><small>여행 중에도 AI와 함께<br />일정을 자유롭게 조정해요.</small></p></div>
            <div><span><CalendarDays /></span><p><b>지도 저장</b><small>내 여행 정보를 지도에 저장하고<br />언제든지 다시 확인해요.</small></p></div>
            <p className="benefit-notice"><Clock3 size={14} /> AI 챗봇은 이용 기간 동안 자유롭게 이용하실 수 있습니다.</p>
          </div>
        </div>

        <aside className="pass-order-panel">
          <div className="panel-title"><h2>주문 정보</h2><span><Plane /></span></div>
          <label className="date-label" htmlFor="pass-start-date">이용 시작일 · 결제 완료일 기준</label>
          <input id="pass-start-date" className="date-input" type="date" value={startDate} disabled onChange={(event) => setStartDate(event.target.value)} />
          <div className="date-summary"><span>이용 종료일</span><b>{formatDate(end)}</b></div>
          <div className="date-summary"><span>선택한 기간</span><b>{selected.days}일</b></div>
          <div className="order-total"><span>최종 결제 금액</span><strong>{selected.price.toLocaleString()}<em>원</em></strong></div>

          <h3 className="method-title">결제 수단 선택</h3>
          <div className="pass-method-grid">
            {paymentMethods.map((item) => (
              <button type="button" key={item.id} disabled={!item.active} className={method === item.id ? 'selected' : ''} onClick={() => setMethod(item.id)}>
                <span>{item.id === 'CARD' ? <CreditCard size={18} /> : item.label.slice(0, 1)}</span>{item.label}
                {!item.active && <small>준비 중</small>}
              </button>
            ))}
          </div>

          <button type="button" className="pass-purchase-button" disabled={loading} onClick={handlePayment}>{loading ? '결제 확인 중...' : '구매하기'}</button>
          {error && <p className="pass-payment-error">{error}</p>}
          <p className="pass-secure"><LockKeyhole size={13} /> 안전하고 암호화된 결제 환경을 제공합니다.</p>
        </aside>
      </section>
    </main>
  );
}

export default PaymentPage;

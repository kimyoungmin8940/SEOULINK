import { useEffect, useMemo, useRef, useState } from 'react';
import { loadTossPayments } from '@tosspayments/tosspayments-sdk';
import { CalendarDays, Check, Clock3, LockKeyhole, MapPinned, RefreshCw, Sparkles, Ticket, Utensils, CarFront, MessageCircle } from 'lucide-react';
import Header from '../../components/common/Header';
import { requestPayment as createPaymentOrder } from '../../api/paymentApi';
import { authStore } from '../../store/authStore';
import heroSeoul from '../../assets/images/hero-seoul-main.png';
import hanokImage from '../../assets/images/moods/mood-hanok-photo.png';
import cafeImage from '../../assets/images/moods/mood-rainy-cafe.png';

const passes = [
  { id: 'day', eyebrow: 'SEOUL DAY PASS', name: '하루 패스', days: 1, price: 9900 },
  { id: 'week', eyebrow: 'SEOUL WEEK PASS', name: '위클리 패스', days: 7, price: 29900 },
  { id: 'month', eyebrow: 'SEOUL TRAVEL PASS', name: '트래블 패스', days: 30, price: 69900, popular: true },
];

const pad = (value) => String(value).padStart(2, '0');
const toInputDate = (date) => `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
const formatDate = (date) => `${date.getFullYear()}.${pad(date.getMonth() + 1)}.${pad(date.getDate())}`;

function PaymentPage() {
  const [selected, setSelected] = useState(passes[2]);
  const [startDate] = useState(toInputDate(new Date()));
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [widgets, setWidgets] = useState(null);
  const paymentWidgetRef = useRef(null);
  const agreementWidgetRef = useRef(null);
  const start = useMemo(() => new Date(`${startDate}T00:00:00`), [startDate]);
  const end = useMemo(() => new Date(start.getFullYear(), start.getMonth(), start.getDate() + selected.days - 1), [start, selected.days]);

  useEffect(() => {
    const member = authStore.getMember();
    const clientKey = import.meta.env.VITE_TOSS_CLIENT_KEY;
    if (!member?.memberId || !clientKey) {
      setError('VITE_TOSS_CLIENT_KEY를 설정하면 토스 결제수단이 표시됩니다.');
      return undefined;
    }
    let active = true;
    (async () => {
      try {
        const tossPayments = await loadTossPayments(clientKey);
        const customerKeyStorage = `toss-customer-key:${member.memberId}`;
        let customerKey = localStorage.getItem(customerKeyStorage);
        if (!customerKey) {
          customerKey = crypto.randomUUID();
          localStorage.setItem(customerKeyStorage, customerKey);
        }
        const nextWidgets = tossPayments.widgets({ customerKey });
        await nextWidgets.setAmount({ currency: 'KRW', value: selected.price });
        paymentWidgetRef.current = await nextWidgets.renderPaymentMethods({ selector: '#toss-payment-methods', variantKey: 'DEFAULT' });
        agreementWidgetRef.current = await nextWidgets.renderAgreement({ selector: '#toss-payment-agreement', variantKey: 'AGREEMENT' });
        if (active) setWidgets(nextWidgets);
      } catch {
        if (active) setError('토스 결제위젯을 불러오지 못했습니다. 클라이언트 키를 확인해 주세요.');
      }
    })();
    return () => { active = false; paymentWidgetRef.current?.destroy(); agreementWidgetRef.current?.destroy(); };
  }, []);

  useEffect(() => { widgets?.setAmount({ currency: 'KRW', value: selected.price }); }, [selected.price, widgets]);

  const handlePayment = async () => {
    const member = authStore.getMember();
    if (!member?.memberId) return setError('로그인 후 이용권을 구매할 수 있습니다.');
    if (!widgets) return setError('결제수단을 준비 중입니다. 잠시 후 다시 시도해 주세요.');
    const orderId = `SEOULLINK_${crypto.randomUUID()}`;
    setLoading(true); setError('');
    try {
      await createPaymentOrder({ memberId: member.memberId, productName: `${selected.name} (${selected.days}일)`, amount: selected.price, paymentMethod: 'TOSS_WIDGET', paymentProvider: 'TOSS', orderId });
      await widgets.requestPayment({ orderId, orderName: `${selected.name} (${selected.days}일)`, customerEmail: member.email, customerName: member.nickname || member.name, successUrl: `${window.location.origin}/payment/success`, failUrl: `${window.location.origin}/payment/fail` });
    } catch (e) {
      setError(e?.message || '결제를 시작하지 못했습니다.');
      setLoading(false);
    }
  };

  return <main className="pass-payment-page payment-reference-page" style={{ '--seoul-hero': `url(${heroSeoul})` }}>
    <Header variant="simple" /><div className="pass-payment-overlay" />
    <section className="payment-reference-shell">
      <header className="payment-reference-heading"><h1>여행 기간에 맞는 AI 이용권을 선택하세요</h1><p>AI가 나만의 서울 여행을 계획하고 실시간으로 도와드려요.</p></header>
      <div className="payment-reference-plans">{passes.map((pass) => <button type="button" key={pass.id} className={`payment-reference-plan ${selected.id === pass.id ? 'selected' : ''}`} onClick={() => setSelected(pass)}>
        {pass.popular && <b className="payment-reference-badge">BEST VALUE <Sparkles /></b>}<span className="payment-reference-radio">{selected.id === pass.id && <Check />}</span><h2>{pass.name} ({pass.days}일)</h2><p className="payment-reference-subtitle">{pass.days === 1 ? '짧고 알찬 하루 여행에 추천' : pass.days === 7 ? '여유로운 일정의 여행에 추천' : '한 달 서울 여행의 든든한 동반자'}</p><img src={pass.days === 1 ? hanokImage : pass.days === 7 ? cafeImage : heroSeoul} alt="" /><b className="payment-reference-recommend">이런 분께 추천해요</b><p className="payment-reference-copy">{pass.days === 1 ? '당일치기 일정으로 핵심 명소만 알차게 둘러보고 싶어요.' : pass.days === 7 ? '서울을 여유롭게 둘러보고 다양한 동네를 경험하고 싶어요.' : '한 달 동안 서울을 깊이 있게 경험하고 나만의 속도로 여행하고 싶어요.'}</p><div className="payment-reference-divider" /><b className="payment-reference-includes">포함 AI 기능</b><ul><li><MapPinned /> AI 맞춤 여행 일정 추천</li><li><CarFront /> 실시간 동선 & 교통 추천</li><li><Utensils /> 명소·맛집·체험 정보 안내</li><li><MessageCircle /> 여행 중 실시간 AI 챗봇</li></ul><footer><span>이용 기간<b>{pass.days}일</b></span><span>가격<b>{pass.price.toLocaleString()}원</b></span></footer>
      </button>)}</div>
      <section className="payment-reference-order"><div className="payment-reference-product"><small>선택한 이용권</small><img src={selected.days === 1 ? hanokImage : selected.days === 7 ? cafeImage : heroSeoul} alt="" /><div><b>{selected.name} ({selected.days}일)</b><p>{selected.days === 30 ? '한 달 서울 여행의 든든한 동반자' : 'AI와 함께 만드는 나만의 서울 여행'}</p><strong>{selected.price.toLocaleString()}원</strong></div></div><div className="payment-reference-dates"><small>이용 기간</small><b>{formatDate(start)} <i>~</i> {formatDate(end)}</b><p>결제 즉시 이용이 시작됩니다.</p></div><div className="payment-reference-widget"><small>결제 수단</small><div id="toss-payment-methods" className="toss-payment-methods" /></div><div className="payment-reference-agreement"><small>약관 동의</small><div id="toss-payment-agreement" className="toss-payment-agreement" /></div><div className="payment-reference-action"><button type="button" disabled={loading || !widgets} onClick={handlePayment}><LockKeyhole /> {loading ? '결제창을 여는 중...' : `${selected.price.toLocaleString()}원 결제하고 이용 시작하기`}</button>{error && <p className="pass-payment-error">{error}</p>}<small>결제 즉시 이용이 시작되며, 이용 기간 종료 전 알림을 보내드려요.</small></div></section>
    </section></main>;
}

export default PaymentPage;

import { useEffect, useMemo, useRef, useState } from 'react';
import { loadTossPayments } from '@tosspayments/tosspayments-sdk';
import { Check, Clock3, LockKeyhole, MapPinned, MessageCircle, Utensils, CarFront, X } from 'lucide-react';
import Header from '../../components/common/Header';
import { requestPayment as createPaymentOrder } from '../../api/paymentApi';
import { authStore } from '../../store/authStore';
import heroSeoul from '../../assets/images/hero-seoul-main.png';
import hanokImage from '../../assets/images/moods/mood-hanok-photo.png';
import cafeImage from '../../assets/images/moods/mood-rainy-cafe.png';

const t = {
  heading: '\uC5EC\uD589 \uAE30\uAC04\uC5D0 \uB9DE\uB294 AI \uC774\uC6A9\uAD8C\uC744 \uC120\uD0DD\uD558\uC138\uC694',
  subheading: 'AI\uAC00 \uB098\uB9CC\uC758 \uC11C\uC6B8 \uC5EC\uD589\uC744 \uACC4\uD68D\uD558\uACE0 \uC2E4\uC2DC\uAC04\uC73C\uB85C \uB3C4\uC640\uB4DC\uB824\uC694.',
  selected: '\uC120\uD0DD\uD55C \uC774\uC6A9\uAD8C',
  recommend: '\uC774\uB7F0 \uBD84\uAED8 \uCD94\uCC9C\uD574\uC694',
  includes: '\uD3EC\uD568 AI \uAE30\uB2A5',
  period: '\uC774\uC6A9 \uAE30\uAC04',
  price: '\uAC00\uACA9',
  payment: '\uC774\uC6A9\uAD8C \uACB0\uC81C',
  method: '\uACB0\uC81C \uC218\uB2E8',
  agreement: '\uC57D\uAD00 \uB3D9\uC758',
  start: '\uACB0\uC81C \uC989\uC2DC \uC774\uC6A9\uC774 \uC2DC\uC791\uB429\uB2C8\uB2E4.',
  day: '\uC77C',
  payNow: '\uACB0\uC81C\uD558\uAE30',
  separator: ' \u00B7 ',
  notice: '\uACB0\uC81C \uC989\uC2DC \uC774\uC6A9\uC774 \uC2DC\uC791\uB418\uBA70, \uC774\uC6A9 \uAE30\uAC04 \uC885\uB8CC \uC804 \uC54C\uB9BC\uC744 \uBCF4\uB0B4\uB4DC\uB824\uC694.',
  serverUnavailable: '\uACB0\uC81C \uC11C\uBC84\uC5D0 \uC5F0\uACB0\uD560 \uC218 \uC5C6\uC2B5\uB2C8\uB2E4. \uBC31\uC5D4\uB4DC\uAC00 8080 \uD3EC\uD2B8\uC5D0\uC11C \uC2E4\uD589 \uC911\uC778\uC9C0 \uD655\uC778\uD574 \uC8FC\uC138\uC694.',
};

const passes = [
  { id: 'day', name: '\uD558\uB8E8 \uD328\uC2A4', days: 1, price: 9900, image: hanokImage, description: '\uC9E7\uACE0 \uC54C\uCC2C \uD558\uB8E8 \uC5EC\uD589\uC5D0 \uCD94\uCC9C', recommendation: '\uB2F9\uC77C\uCE58\uAE30 \uC77C\uC815\uC73C\uB85C \uD575\uC2EC \uBA85\uC18C\uB9CC \uC54C\uCC28\uAC8C \uB458\uB7EC\uBCF4\uACE0 \uC2F6\uC5B4\uC694.' },
  { id: 'week', name: '\uC704\uD074\uB9AC \uD328\uC2A4', days: 7, price: 29900, image: cafeImage, description: '\uC5EC\uC720\uB85C\uC6B4 \uC77C\uC815\uC758 \uC5EC\uD589\uC5D0 \uCD94\uCC9C', recommendation: '\uC11C\uC6B8\uC744 \uC5EC\uC720\uB86D\uAC8C \uB458\uB7EC\uBCF4\uACE0 \uB2E4\uC591\uD55C \uB3D9\uB124\uB97C \uACBD\uD5D8\uD558\uACE0 \uC2F6\uC5B4\uC694.' },
  { id: 'month', name: '\uD2B8\uB798\uBE14 \uD328\uC2A4', days: 30, price: 69900, image: heroSeoul, description: '\uD55C \uB2EC \uC11C\uC6B8 \uC5EC\uD589\uC758 \uB4E0\uB4E0\uD55C \uB3D9\uBC18\uC790', recommendation: '\uD55C \uB2EC \uB3D9\uC548 \uC11C\uC6B8\uC744 \uAE4A\uC774 \uC788\uAC8C \uACBD\uD5D8\uD558\uACE0 \uB098\uB9CC\uC758 \uC18D\uB3C4\uB85C \uC5EC\uD589\uD558\uACE0 \uC2F6\uC5B4\uC694.', popular: true },
];

const features = [
  { Icon: MapPinned, text: 'AI \uB9DE\uCDA4 \uC5EC\uD589 \uC77C\uC815 \uCD94\uCC9C' },
  { Icon: CarFront, text: '\uC2E4\uC2DC\uAC04 \uB3D9\uC120 & \uAD50\uD1B5 \uCD94\uCC9C' },
  { Icon: Utensils, text: '\uBA85\uC18C\u00B7\uB9DB\uC9D1\u00B7\uCCB4\uD5D8 \uC815\uBCF4 \uC548\uB0B4' },
  { Icon: MessageCircle, text: '\uC5EC\uD589 \uC911 \uC2E4\uC2DC\uAC04 AI \uCC57\uBD07' },
];

const pad = (value) => String(value).padStart(2, '0');
const formatDate = (date) => `${date.getFullYear()}.${pad(date.getMonth() + 1)}.${pad(date.getDate())}`;
const won = (price) => `${price.toLocaleString()}\uC6D0`;

function PaymentPage() {
  const [selected, setSelected] = useState(passes[2]);
  const [isPaymentOpen, setIsPaymentOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [widgets, setWidgets] = useState(null);
  const paymentWidgetRef = useRef(null);
  const agreementWidgetRef = useRef(null);
  const start = useMemo(() => new Date(), []);
  const end = useMemo(() => new Date(start.getFullYear(), start.getMonth(), start.getDate() + selected.days - 1), [start, selected.days]);

  // 결제 모달이 열릴 때만 토스 결제수단·약관 위젯을 생성한다.
  useEffect(() => {
    if (!isPaymentOpen) return undefined;
    let active = true;
    // 선택한 이용권 금액으로 토스 결제수단·약관 위젯을 화면에 렌더링한다.
const mountWidgets = async () => {
      try {
        const clientKey = import.meta.env.VITE_TOSS_CLIENT_KEY;
        if (!clientKey) throw new Error('\uD074\uB77C\uC774\uC5B8\uD2B8 \uD0A4\uB97C \uD655\uC778\uD574 \uC8FC\uC138\uC694.');
        const tossPayments = await loadTossPayments(clientKey);
        const member = authStore.getMember();
        const customerKey = `seoulink-${String(member?.memberId ?? 'guest')}`;
        const paymentWidgets = tossPayments.widgets({ customerKey });
        await paymentWidgets.setAmount({ currency: 'KRW', value: selected.price });
        paymentWidgetRef.current = await paymentWidgets.renderPaymentMethods({ selector: '#toss-payment-methods', variantKey: 'DEFAULT' });
        agreementWidgetRef.current = await paymentWidgets.renderAgreement({ selector: '#toss-payment-agreement', variantKey: 'AGREEMENT' });
        if (active) setWidgets(paymentWidgets);
      } catch (mountError) {
        if (active) setError(mountError?.message || '\uD1A0\uC2A4 \uACB0\uC81C\uC704\uC82F\uC744 \uBD88\uB7EC\uC624\uC9C0 \uBABB\uD588\uC2B5\uB2C8\uB2E4.');
      }
    };
    mountWidgets();
    return () => {
      active = false;
      paymentWidgetRef.current?.destroy?.();
      agreementWidgetRef.current?.destroy?.();
      paymentWidgetRef.current = null;
      agreementWidgetRef.current = null;
    };
  }, [isPaymentOpen, selected.price]);

  // 결제 모달을 열기 전에 이전 오류 메시지를 초기화한다.
const openPayment = () => { setError(''); setIsPaymentOpen(true); };
  // 결제 진행 중에는 모달을 닫지 않아 위젯 상태가 중단되지 않도록 한다.
  // 진행 중인 결제는 유지하고, 안전한 상태에서만 위젯과 모달을 해제한다.
const closePayment = () => {
    if (loading) return;
    setWidgets(null);
    setIsPaymentOpen(false);
  };

  // 서버 주문을 먼저 생성한 후 토스 결제창으로 승인 과정을 시작한다.
  const handlePayment = async () => {
    const member = authStore.getMember();
    if (!member?.memberId) return setError('\uB85C\uADF8\uC778 \uD6C4 \uC774\uC6A9\uAD8C\uC744 \uAD6C\uB9E4\uD560 \uC218 \uC788\uC2B5\uB2C8\uB2E4.');
    if (!widgets) return setError('\uACB0\uC81C \uC218\uB2E8\uC744 \uC900\uBE44\uD558\uACE0 \uC788\uC2B5\uB2C8\uB2E4. \uC7A0\uC2DC \uD6C4 \uB2E4\uC2DC \uC2DC\uB3C4\uD574 \uC8FC\uC138\uC694.');
    const orderId = `SEOULINK_${crypto.randomUUID()}`;
    setLoading(true);
    setError('');
    try {
      await createPaymentOrder({ memberId: member.memberId, productName: `${selected.name} (${selected.days}\uC77C)`, amount: selected.price, paymentMethod: 'TOSS_WIDGET', paymentProvider: 'TOSS', orderId });
      await widgets.requestPayment({ orderId, orderName: `${selected.name} (${selected.days}\uC77C)`, customerEmail: member.email, customerName: member.nickname || member.name, successUrl: `${window.location.origin}/payment/success`, failUrl: `${window.location.origin}/payment/fail` });
    } catch (requestError) {
      setError(requestError?.message === 'Failed to fetch' ? t.serverUnavailable : (requestError?.message || '\uACB0\uC81C\uB97C \uC2DC\uC791\uD558\uC9C0 \uBABB\uD588\uC2B5\uB2C8\uB2E4. \uB2E4\uC2DC \uC2DC\uB3C4\uD574 \uC8FC\uC138\uC694.'));
      setLoading(false);
    }
  };

  return (
    <main className="pass-payment-page payment-reference-page" style={{ '--seoul-hero': `url(${heroSeoul})` }}>
      <Header variant="simple" />
      <div className="pass-payment-overlay" />
      <section className="payment-reference-shell">
        <header className="payment-reference-heading"><h1>{t.heading}</h1><p>{t.subheading}</p></header>
        <div className="payment-reference-plans">
          {passes.map((pass) => <button type="button" key={pass.id} className={`payment-reference-plan ${selected.id === pass.id ? 'selected' : ''}`} onClick={() => setSelected(pass)}>
            {pass.popular && <b className="payment-reference-badge">BEST PICK</b>}
            <span className="payment-reference-radio">{selected.id === pass.id && <Check />}</span>
            <h2>{pass.name} <small>({pass.days}{t.day})</small></h2>
            <p className="payment-reference-subtitle">{pass.description}</p>
            <img src={pass.image} alt="" />
            <b className="payment-reference-recommend">{t.recommend}</b><p className="payment-reference-copy">{pass.recommendation}</p>
            <div className="payment-reference-divider" />
            <b className="payment-reference-includes">{t.includes}</b>
            <ul>{features.map(({ Icon, text }) => <li key={text}><Icon />{text}</li>)}</ul>
            <footer><span>{t.period}<b>{pass.days}{t.day}</b></span><span>{t.price}<b>{won(pass.price)}</b></span></footer>
          </button>)}
        </div>
        <div className="payment-reference-open-action">
          <div className="payment-reference-open-summary">
            <span><LockKeyhole /></span>
            <div><small>{t.selected}</small><b>{selected.name} <em>({selected.days}{t.day})</em></b></div>
          </div>
          <div className="payment-reference-open-total"><small>{t.price}</small><strong>{won(selected.price)}</strong></div>
          <button type="button" onClick={openPayment}><LockKeyhole />{won(selected.price)} {t.payNow}</button>
        </div>
      </section>
      {isPaymentOpen && <div className="payment-modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) closePayment(); }}>
        <section className="payment-modal" role="dialog" aria-modal="true" aria-label={t.payment}>
          <header className="payment-modal-header"><div><small>SEOULLINK PASS</small><h2>{t.payment}</h2></div><button className="payment-modal-close" type="button" onClick={closePayment} aria-label="\uACB0\uC81C\uCC3D \uB2EB\uAE30"><X /></button></header>
          <div className="payment-modal-body">
            <section className="payment-modal-product"><small>{t.selected}</small><img src={selected.image} alt="" /><div><b>{selected.name} ({selected.days}{t.day})</b><p>{selected.description}</p><strong>{won(selected.price)}</strong></div></section>
            <section className="payment-modal-period"><small>{t.period}</small><div><b>{formatDate(start)}</b><span>~</span><b>{formatDate(end)}</b></div><p><Clock3 />{t.start}</p></section>
            <section className="payment-modal-widget"><small>{t.method}</small><div id="toss-payment-methods" /></section>
            <footer className="payment-modal-footer">
              <section className="payment-modal-agreement"><small>{t.agreement}</small><div id="toss-payment-agreement" /></section>
              <button type="button" disabled={loading || !widgets} onClick={handlePayment}><LockKeyhole /><span>{loading ? '\uACB0\uC81C \uCC3D\uC744 \uC5EC\uB294 \uC911\uC785\uB2C8\uB2E4...' : `${won(selected.price)} \uACB0\uC81C\uD558\uACE0 \uC774\uC6A9 \uC2DC\uC791\uD558\uAE30`}</span></button>{error && <p>{error}</p>}<small>{t.notice}</small>
            </footer>
          </div>
        </section>
      </div>}
    </main>
  );
}

export default PaymentPage;

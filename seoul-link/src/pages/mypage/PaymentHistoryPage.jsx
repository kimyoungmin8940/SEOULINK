import { useEffect, useMemo, useState } from 'react';
import {
    BadgeCheck,
    Bookmark,
    BriefcaseBusiness,
    CalendarDays,
    ChevronDown,
    ChevronUp,
    CreditCard,
    FileText,
    MessageCircle,
    Pencil,
    Plus,
    ReceiptText,
    Route,
    Sparkles,
    Ticket,
    UserRound,
    WalletCards,
} from 'lucide-react';
import Header from '../../components/common/Header';
import Footer from '../../components/common/Footer';
import MypageSidebar from '../../components/common/MypageSidebar';
import { cancelPayment, getMyPayments } from '../../api/paymentApi';
import { authStore } from '../../store/authStore';
import '../../styles/mypage.css';
import '../../styles/payment-history.css';
import '../../styles/mypage-sidebar-unified.css';

const menuItems = [
    { label: '내 여행 정보', path: '/mypage', Icon: BriefcaseBusiness },
    { label: '저장한 추천 코스', path: '/mypage/courses', Icon: Bookmark },
    { label: '직접 만든 코스', path: '/mypage/custom-courses', Icon: Route },
    { label: '내가 쓴 후기와 댓글', path: '/mypage/reviews', Icon: MessageCircle },
    { label: '취향 검사 결과', path: '/mypage/travel-type', Icon: Sparkles },
    { label: '결제 내역', path: '/mypage/payments', Icon: CreditCard },
];

const passMeta = (payment) => {
    const source = `${payment.productName || ''} ${payment.amount || ''}`;
    if (source.includes('30') || payment.amount === 69900) return { label: '트래블 패스', days: 30, tone: 'blue' };
    if (source.includes('7') || payment.amount === 29900) return { label: '위클리 패스', days: 7, tone: 'cyan' };
    return { label: '하루 패스', days: 1, tone: 'green' };
};

function PassIllustration({ days }) {
    if (days === 30) {
        return <svg viewBox="0 0 48 48" aria-label="트래블 패스 여행 가방">
            <path d="M8 16.5c0-4.7 3.8-8.5 8.5-8.5h15C36.2 8 40 11.8 40 16.5V33c0 4.4-3.6 8-8 8H16c-4.4 0-8-3.6-8-8V16.5Z" fill="#B9D8FF" opacity=".55" />
            <path d="M18 14v-2.3c0-2.1 1.7-3.7 3.7-3.7h4.6c2 0 3.7 1.6 3.7 3.7V14" fill="none" stroke="#5277B9" strokeLinecap="round" strokeWidth="2.8" />
            <rect x="9" y="14" width="30" height="24" rx="7" fill="#6F9EE8" />
            <rect x="11.5" y="16.5" width="25" height="16" rx="4.8" fill="#87B5F4" />
            <path d="M9 23h30" stroke="#557FC5" strokeWidth="2" />
            <rect x="21" y="20.5" width="6" height="7" rx="2" fill="#FFF4D8" />
            <path d="m23 22 2 1.6-2 1.6v-3.2Z" fill="#EF9C45" />
            <circle cx="15" cy="40" r="2" fill="#5277B9" /><circle cx="33" cy="40" r="2" fill="#5277B9" />
            <path d="m35.8 10.4 1.1 2.4 2.5 1.1-2.5 1.1-1.1 2.4-1.1-2.4-2.5-1.1 2.5-1.1 1.1-2.4Z" fill="#FFF2A6" />
        </svg>;
    }
    if (days === 7) {
        return <svg viewBox="0 0 48 48" aria-label="위클리 패스 주간 캘린더">
            <rect x="8" y="9" width="32" height="31" rx="8" fill="#F1E3D6" />
            <rect x="10.5" y="12" width="27" height="25" rx="5.5" fill="#FFF" />
            <path d="M10.5 19h27" stroke="#C9A98B" strokeWidth="2.3" />
            <rect x="15" y="7" width="4" height="9" rx="2" fill="#9C7A60" />
            <rect x="29" y="7" width="4" height="9" rx="2" fill="#9C7A60" />
            <circle cx="17" cy="25" r="2" fill="#D9C0A6" /><circle cx="24" cy="25" r="2" fill="#BFA082" /><circle cx="31" cy="25" r="2" fill="#D9C0A6" />
            <path d="m16.5 32 3 2.5 5-5.5" fill="none" stroke="#A98268" strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.4" />
            <path d="m34.7 7.7.8 1.7 1.8.8-1.8.8-.8 1.7-.8-1.7-1.8-.8 1.8-.8.8-1.7Z" fill="#E7C4A1" />
        </svg>;
    }
    return <svg viewBox="0 0 48 48" aria-label="하루 패스 서울 산책">
        <circle cx="31.5" cy="15.5" r="8" fill="#F4A7B7" />
        <path d="M31.5 4.5v3M31.5 23.5v3M20.5 15.5h3M39.5 15.5h3M23.7 7.7l2.1 2.1M37.2 21.2l2.1 2.1M39.3 7.7l-2.1 2.1M25.8 21.2l-2.1 2.1" stroke="#D6768C" strokeLinecap="round" strokeWidth="2" />
        <path d="M7 35.5c4-7.7 10.3-11.5 18.8-11.5 5.5 0 10.6 1.9 15.2 5.7v8.8H7v-2.9Z" fill="#8ED4F6" />
        <path d="M7 38c5.3-4.2 10.9-5.4 16.9-3.5 5.2 1.7 10.9 1.3 17.1-1.2v5.2H7V38Z" fill="#5CADE4" />
        <path d="m15 38 7-8 5 4 6-7 8 11H15Z" fill="#4A92D6" />
        <path d="M10.2 23.3c0-3.2 2.5-5.7 5.7-5.7 2.5 0 4.6 1.6 5.4 3.8.5-.2 1.1-.3 1.7-.3 2.8 0 5 2.2 5 5H10.2v-2.8Z" fill="#FFF" opacity=".95" />
        <circle cx="31.5" cy="15.5" r="3.2" fill="#FAD2DA" />
    </svg>;
}

const formatMoney = (amount) => `${Number(amount || 0).toLocaleString('ko-KR')}원`;
const formatDate = (value) => value
    ? new Intl.DateTimeFormat('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date(value)).replace(/\. /g, '.')
    : '-';
const formatDateTime = (value) => value
    ? new Intl.DateTimeFormat('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date(value)).replace(/\. /g, '.')
    : '-';

const isPaid = (payment) => ['PAID', 'DONE'].includes(payment.paymentStatus);

function paymentState(payment) {
    if (payment.paymentStatus === 'CANCELED') return { label: '결제 취소', key: 'canceled' };
    if (payment.paymentStatus === 'FAILED') return { label: '결제 실패', key: 'failed' };
    if (!isPaid(payment)) return { label: '결제 대기', key: 'pending' };
    if (payment.expiredAt && new Date(payment.expiredAt) > new Date()) return { label: '결제 완료', key: 'completed', canUse: true };
    return { label: '이용 종료', key: 'ended' };
}

// 결제 내역은 회원별 서버 데이터로 렌더링한다. 삭제는 화면에서만 숨기는 것이 아니라
// 해당 결제 내역을 대상으로 API를 호출한 뒤 목록을 다시 동기화한다.
export default function PaymentHistoryPage() {
    const member = authStore.getMember() || {};
    const [payments, setPayments] = useState([]);
    const [statusFilter, setStatusFilter] = useState('ALL');
    const [openId, setOpenId] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [cancellingPaymentId, setCancellingPaymentId] = useState(null);

    const userName = member.nickname?.trim() || member.name?.trim() || '여행자';
    const email = member.email || 'user@seoulink.com';

    const handleProfileEdit = () => {
        if ((member.loginType || 'LOCAL') !== 'LOCAL') {
            window.alert('프로필 정보는 해당 소셜 서비스에서 관리해 주세요.');
            return;
        }
        window.location.assign('/mypage/profile-edit');
    };

    useEffect(() => {
        if (!member.memberId) {
            setError('로그인 정보를 확인할 수 없습니다. 다시 로그인해 주세요.');
            setLoading(false);
            return;
        }

        getMyPayments(member.memberId)
            .then((data) => {
                const next = (Array.isArray(data) ? data : [])
                    .filter((payment) => payment.paymentStatus !== 'READY');
                setPayments(next);
                setOpenId(null);
            })
            .catch((requestError) => setError(requestError?.message || '결제 내역을 불러오지 못했습니다.'))
            .finally(() => setLoading(false));
    }, [member.memberId]);

    const filteredPayments = useMemo(
        () => payments.filter((payment) => statusFilter === 'ALL' || paymentState(payment).key === statusFilter),
        [payments, statusFilter],
    );
    const paidPayments = useMemo(() => payments.filter(isPaid), [payments]);
    const totalAmount = useMemo(() => paidPayments.reduce((sum, payment) => sum + Number(payment.amount || 0), 0), [paidPayments]);
    const activePayment = useMemo(() => paidPayments.find((payment) => paymentState(payment).canUse), [paidPayments]);

    const printReceipt = (payment) => {
        const meta = passMeta(payment);
        const receipt = window.open('', '_blank', 'width=600,height=760');
        if (!receipt) return;
        receipt.document.write(`<title>SEOULLINK 결제 영수증</title><body style="font-family:Arial,sans-serif;padding:40px;color:#17345f"><h1>SEOULLINK 결제 영수증</h1><hr/><p>상품: ${meta.label} (${meta.days}일)</p><p>결제 금액: ${formatMoney(payment.amount)}</p><p>주문 번호: ${payment.orderId || '-'}</p><p>결제 일시: ${formatDateTime(payment.paidAt || payment.createdAt)}</p><p>결제 수단: ${payment.paymentMethod || payment.paymentProvider || '-'}</p></body>`);
        receipt.document.close();
        receipt.print();
    };

    const cancelPaymentRequest = async (payment) => {
        if (!member.memberId || cancellingPaymentId) return;

        const approved = window.confirm(
            '결제를 취소하면 이용권을 더 이상 사용할 수 없습니다. 취소하시겠습니까?',
        );
        if (!approved) return;

        try {
            setCancellingPaymentId(payment.paymentId);

            const canceledPayment = await cancelPayment(
                payment.paymentId,
                member.memberId,
                '사용자 요청',
            );

            setPayments((current) =>
                current.map((item) =>
                    item.paymentId === payment.paymentId
                        ? { ...item, ...canceledPayment }
                        : item,
                ),
            );
        } catch (requestError) {
            setError(requestError?.message || '결제 취소에 실패했습니다.');
        } finally {
            setCancellingPaymentId(null);
        }
    };

    return (
        <main className="payment-history-page">
            <Header />
            <section className="payment-history-shell">
                {false && (<aside className="mypage-v3-sidebar payment-history-side">
                    <section className="mypage-v3-profile">
                        <div className="mypage-v3-avatar"><UserRound size={54} strokeWidth={1.5} /></div>
                        <strong>{userName}님</strong>
                        <span>{email}</span>
                        <button className="mypage-profile-edit" type="button" onClick={handleProfileEdit}>
                            <Pencil size={16} />프로필 수정
                        </button>
                    </section>
                    <nav className="mypage-v3-menu" aria-label="마이페이지 메뉴">
                        {menuItems.map(({ label, path, Icon }) => (
                            <a key={label} className={path === '/mypage/payments' ? 'active' : ''} href={path}>
                                <Icon size={20} strokeWidth={1.8} />
                                <span>{label}</span>
                            </a>
                        ))}
                    </nav>
                    <a className="mypage-retest" href="/map-course?category=palace-culture"><Plus size={18} />지도 코스 만들기</a>
                </aside>)}
                <MypageSidebar activePath="/mypage/payments" />

                <section className="payment-history-main">
                    <header className="payment-history-heading"><h1>결제 내역</h1><p>구매한 AI 여행 챗봇 이용권을 확인하세요.</p></header>

                    <section className="payment-summary-grid">
                        <article><span className="summary-icon blue"><WalletCards /></span><div><small>총 결제 금액</small><strong>{formatMoney(totalAmount)}</strong></div></article>
                        <article><span className="summary-icon cyan"><ReceiptText /></span><div><small>총 구매 건수</small><strong>{paidPayments.length}건</strong></div></article>
                        <article><span className="summary-icon orange"><Ticket /></span><div><small>현재 이용 중인 이용권</small><strong>{activePayment ? passMeta(activePayment).label : '이용권 없음'}</strong></div></article>
                    </section>

                    <section className="payment-history-toolbar"><div className="toolbar-period"><CalendarDays />최근 결제 내역</div><label>상태 <select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}><option value="ALL">전체 상태</option><option value="completed">결제 완료</option><option value="ended">이용 종료</option><option value="pending">결제 대기</option><option value="failed">결제 실패</option><option value="canceled">결제 취소</option></select></label></section>

                    {loading && <p className="payment-history-state">결제 내역을 불러오는 중입니다.</p>}
                    {error && <p className="payment-history-state error">{error}</p>}
                    {!loading && !error && filteredPayments.length === 0 && <p className="payment-history-state">표시할 결제 내역이 없습니다.</p>}

                    <section className="payment-history-list">
                        {filteredPayments.map((payment) => {
                            const meta = passMeta(payment);
                            const state = paymentState(payment);
                            const isOpen = openId === payment.paymentId;
                            const boughtAt = payment.paidAt || payment.createdAt;
                            return <article className={`payment-history-card ${isOpen ? 'open' : ''}`} key={payment.paymentId}>
                                <button className="payment-card-top" type="button" onClick={() => setOpenId(isOpen ? null : payment.paymentId)}>
                                    <span className={`payment-pass-mark ${meta.tone}`}>
                                        <PassIllustration days={meta.days} />
                                    </span>
                                    <span className="payment-card-title"><b>{meta.label} <em>({meta.days}일)</em></b><small>{meta.days === 30 ? '한 달 서울 여행의 든든한 동반자' : meta.days === 7 ? '여유로운 일주일 여행에 추천' : '짧고 알찬 하루 여행에 추천'}</small></span>
                                    <span className="payment-card-date"><small>구매일</small><b>{formatDate(boughtAt)}</b></span>
                                    <span className="payment-card-date"><small>이용 기간</small><b>{formatDate(boughtAt)} ~ {formatDate(payment.expiredAt)}</b></span>
                                    <span className={`payment-state ${state.key}`}>{state.label}</span>
                                    <strong className="payment-card-amount">{formatMoney(payment.amount)}</strong>
                                    {isOpen ? <ChevronUp /> : <ChevronDown />}
                                </button>
                                {isOpen && <div className="payment-card-detail">
                                    <div className="detail-item"><small>결제 수단</small><b>{payment.paymentMethod || payment.paymentProvider || '-'}</b></div>
                                    <div className="detail-item"><small>주문 번호</small><b>{payment.orderId || '-'}</b></div>
                                    <div className="detail-item"><small>결제 일시</small><b>{formatDateTime(boughtAt)}</b></div>
                                    <div className="payment-history-actions">{payment.paymentStatus === 'PAID' && <button type="button" className="payment-delete-button" disabled={cancellingPaymentId === payment.paymentId} onClick={() => cancelPaymentRequest(payment)}>{cancellingPaymentId === payment.paymentId ? '취소 처리 중...' : '결제 취소'}</button>}<button type="button" className="receipt-button" onClick={() => printReceipt(payment)}><FileText />영수증 보기</button>{state.canUse && <a className="chatbot-button" href="/chatbot"><BadgeCheck />AI 챗봇 이용하기</a>}</div>
                                </div>}
                            </article>;
                        })}
                    </section>
                </section>
            </section>
            <Footer />
        </main>
    );
}

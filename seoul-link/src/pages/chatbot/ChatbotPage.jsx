import { useEffect, useMemo, useRef, useState } from 'react';
import { Bot, Bookmark, ChevronRight, CircleHelp, Clock3, Coffee, MapPinned, MessageCircle, Moon, Navigation, Paperclip, Plus, Send, Sparkles, Umbrella, UserRound, UsersRound } from 'lucide-react';
import Header from '../../components/common/Header';
import { askChatbot, getChatbotHistory } from '../../api/chatbotApi';
import { authStore } from '../../store/authStore';
import heroSeoul from '../../assets/images/hero-seoul-main.png';
import hanokImage from '../../assets/images/moods/mood-hanok-photo.png';
import cafeImage from '../../assets/images/moods/mood-rainy-cafe.png';
import nightImage from '../../assets/images/cta-seoul-night.jpg';

const prompts = [
  { text: '성수 감성 카페', icon: Coffee }, { text: '부모님과 서울 여행', icon: UsersRound }, { text: '비 오는 날 코스', icon: Umbrella }, { text: '야경 데이트', icon: Moon },
];
const recent = ['1일 서울 여행 코스 추천', '성수동 데이트 코스 추천', '아이와 가볼 만한 곳', '비 오는 날 실내 코스'];
const saved = [
  { title: '북촌 한옥마을 산책 코스', image: hanokImage, date: '2025.06.21' }, { title: '한강 야경 데이트 코스', image: nightImage, date: '2025.06.18' }, { title: '홍대 감성 플레이스 코스', image: cafeImage, date: '2025.06.15' },
];
const initialMessages = [{ role: 'bot', text: '서울 1일 여행 코스를 추천해 드릴게요!\n전통과 현대, 감성과 미식을 모두 즐길 수 있는 알찬 코스입니다.', time: '오전 10:30', course: true }];
const formatTime = (value = new Date()) => new Date(value).toLocaleTimeString('ko-KR', { hour: 'numeric', minute: '2-digit' });

function ChatbotPage() {
  const member = authStore.getMember();
  const [messages, setMessages] = useState(initialMessages);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [histories, setHistories] = useState([]);
  const bottomRef = useRef(null);

  useEffect(() => {
    if (!member?.memberId) return;
    getChatbotHistory(member.memberId).then((items = []) => setHistories(items)).catch(() => setHistories([]));
  }, [member?.memberId]);
  useEffect(() => bottomRef.current?.scrollIntoView({ behavior: 'smooth' }), [messages, loading]);

  const conversationTitle = useMemo(() => histories[0]?.question || recent[0], [histories]);
  const submit = async (value = input) => {
    const question = value.trim();
    if (!question || loading) return;
    const time = formatTime();
    setMessages((prev) => [...prev, { role: 'user', text: question, time }]);
    setInput(''); setLoading(true); setError('');
    try {
      const result = await askChatbot({ memberId: member?.memberId, question, travelConcept: '맞춤 서울 여행' });
      const answer = result?.answer || result?.courseSummary || '요청하신 여행 스타일에 맞는 서울 코스를 준비했어요.';
      setMessages((prev) => [...prev, { role: 'bot', text: answer, time: formatTime(result?.createdAt), course: true }]);
      setHistories((prev) => [result, ...prev]);
    } catch (e) {
      setError(e?.message || '챗봇을 불러오지 못했습니다. 이용권 상태를 확인해 주세요.');
    } finally { setLoading(false); }
  };

  return <main className="reference-chat-page" style={{ '--chat-hero': `url(${heroSeoul})` }}>
    <Header variant="simple" /><div className="reference-chat-overlay" />
    <section className="reference-chat-shell">
      <aside className="reference-sidebar">
        <div className="reference-bot-intro"><span className="reference-orbit"><Bot /></span><div><b>AI 여행 플래너</b><p>서울 여행을 더 스마트하게.<br />AI가 맞춤 코스를 제안해 드려요.</p></div></div>
        <button className="reference-new-chat" type="button" onClick={() => { setMessages([]); setError(''); }}><Plus /> 새 여행 만들기</button>
        <section className="reference-side-section"><header><b>최근 대화</b><a href="#history">전체 보기 <ChevronRight /></a></header><div id="history">{(histories.length ? histories.slice(0, 4).map((item) => item.question) : recent).map((title, index) => <button type="button" className={index === 0 ? 'active' : ''} key={`${title}-${index}`} onClick={() => index === 0 && setMessages(initialMessages)}><MessageCircle /><span>{title}<small>{index === 0 ? '방금 전' : `${index + 1}일 전`}</small></span></button>)}</div></section>
        <section className="reference-side-section reference-saved"><header><b>저장한 코스</b><a href="/mypage/courses">전체 보기 <ChevronRight /></a></header>{saved.map((item) => <a className="reference-saved-item" href="/mypage/courses" key={item.title}><img src={item.image} alt="" /><span><b>{item.title}</b><small>저장일 {item.date}</small></span></a>)}</section>
        <a className="reference-pass-card" href="/payment"><span><Sparkles /> AI 이용권</span><b>이용권 구매하기 <ChevronRight /></b><small>결제 후 AI 플래너를 이용할 수 있어요.</small></a>
      </aside>
      <section className="reference-room">
        <header className="reference-room-title"><div><span><Bot /></span><section><b>AI 여행 플래너</b><small><i /> 온라인 · 언제든 질문하세요</small></section></div><p><UserRound /> {member?.nickname || member?.name || '여행자'}님</p></header>
        <div className="reference-content"><div className="reference-welcome"><h1>어떤 서울 여행을 꿈꾸시나요?</h1><p>원하는 스타일을 선택하거나 자유롭게 대화를 시작해 보세요.</p><nav>{prompts.map(({ text, icon: Icon }) => <button type="button" key={text} onClick={() => submit(text)}><Icon /> {text}</button>)}</nav></div>
          <div className="reference-conversation"><div className="reference-user-question"><small>{conversationTitle}</small><span>{conversationTitle}</span><UserRound /></div>{messages.map((message, index) => <Message key={`${message.time}-${index}`} message={message} />)}{loading && <div className="reference-thinking"><i /><i /><i /> AI가 여행 코스를 만드는 중이에요.</div>}{error && <div className="reference-error"><CircleHelp /> {error} <a href="/payment">이용권 구매하기</a></div>}<div ref={bottomRef} /></div>
        </div>
        <div className="reference-composer"><form onSubmit={(event) => { event.preventDefault(); submit(); }}><div><textarea rows="1" value={input} onChange={(event) => setInput(event.target.value)} onKeyDown={(event) => { if (event.key === 'Enter' && !event.shiftKey) { event.preventDefault(); submit(); } }} placeholder="원하는 여행을 자유롭게 이야기해 주세요" /><nav><button type="button" aria-label="첨부"><Paperclip /></button><button type="button" aria-label="위치"><MapPinned /></button></nav></div><button className="reference-send" type="submit" disabled={!input.trim() || loading} aria-label="보내기"><Send /></button></form><p>AI가 생성한 정보는 참고용으로 활용해 주세요.</p></div>
      </section>
    </section>
  </main>;
}

function Message({ message }) {
  if (message.role === 'user') return <div className="reference-message user"><div><p>{message.text}</p><small>{message.time}</small></div></div>;
  return <div className="reference-message bot"><span><Bot /></span><div><b>AI 여행 플래너 <small>{message.time}</small></b><article><p>{message.text}</p>{message.course && <CoursePreview />}</article></div></div>;
}
function CoursePreview() {
  const stops = [{ time: '10:00', title: '북촌 한옥마을', detail: '고즈넉한 한옥의 분위기 속에서 산책과 사진을 즐겨보세요.', image: hanokImage }, { time: '13:00', title: '성수동 카페거리', detail: '감각적인 카페와 편집숍에서 여유로운 시간을 보내세요.', image: cafeImage }, { time: '18:30', title: '한강공원 (여의도/반포)', detail: '아름다운 야경과 함께 하루를 마무리해 보세요.', image: nightImage }];
  return <section className="reference-course-card">{stops.map((stop, index) => <div className="reference-stop" key={stop.title}><div><i>{index + 1}</i><time>{stop.time}</time></div><img src={stop.image} alt="" /><section><b>{stop.title}</b><p>{stop.detail}</p><small>추천 활동 · 서울 여행</small></section></div>)}<footer><span><Clock3 /> 총 소요 시간 <b>약 8시간 30분</b></span><span><Navigation /> 예상 이동 시간 <b>약 1시간 10분</b></span><span><MapPinned /> 예상 비용 <b>약 55,000원</b></span></footer><nav><a href="/map-course"><MapPinned /> 지도에서 보기</a><button type="button"><Bookmark /> 코스로 저장</button></nav></section>;
}
export default ChatbotPage;

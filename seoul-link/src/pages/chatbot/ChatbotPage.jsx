import { useEffect, useMemo, useRef, useState } from 'react';
import { Bookmark, Bot, CalendarDays, ChevronRight, Copy, ImagePlus, Map, MapPin, MessageCircleMore, Plus, Send, Sparkles, ThumbsDown, ThumbsUp } from 'lucide-react';
import Header from '../../components/common/Header';
import { askChatbot, getChatbotHistory } from '../../api/chatbotApi';
import { authStore } from '../../store/authStore';
import heroSeoul from '../../assets/images/hero-seoul-main.png';
import museumImage from '../../assets/images/cta-seoul-night.jpg';
import cafeImage from '../../assets/images/moods/mood-rainy-cafe.png';
import marketImage from '../../assets/images/moods/mood-local-food.png';

const suggestions = ['비 오는 날 실내 코스', '서울 맛집 추천', '한강 야경 명소', '아이와 가볼 만한 곳', '당일치기 여행지'];
const sampleStops = [
  { time: '10:00 - 12:00', name: '국립중앙박물관', description: '다양한 전시와 체험이 가능한 대표 박물관', area: '용산', image: museumImage },
  { time: '12:30 - 14:00', name: '한옥 감성 카페', description: '서울의 분위기를 느끼며 쉬어가는 시간', area: '종로', image: cafeImage },
  { time: '14:30 - 17:00', name: '광장시장', description: '먹거리와 볼거리가 가득한 전통 시장', area: '종로', image: marketImage },
];

const formatTime = (value = new Date()) => new Date(value).toLocaleTimeString('ko-KR', { hour: 'numeric', minute: '2-digit' });

function ChatbotPage() {
  const member = authStore.getMember();
  const [histories, setHistories] = useState([]);
  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const bottomRef = useRef(null);

  useEffect(() => {
    getChatbotHistory(member.memberId).then((items = []) => {
      setHistories(items);
      const restored = [...items].reverse().slice(-4).flatMap((item) => [
        { role: 'user', text: item.question, time: formatTime(item.createdAt) },
        { role: 'bot', text: item.answer, time: formatTime(item.createdAt), course: true },
      ]);
      setMessages(restored);
    }).catch(() => setHistories([]));
  }, [member.memberId]);

  useEffect(() => bottomRef.current?.scrollIntoView({ behavior: 'smooth' }), [messages, loading]);

  const activeTitle = useMemo(() => messages.find((item) => item.role === 'user')?.text || '새로운 서울 여행 대화', [messages]);

  const submit = async (value = input) => {
    const question = value.trim();
    if (!question || loading) return;
    const time = formatTime();
    setMessages((prev) => [...prev, { role: 'user', text: question, time }]);
    setInput(''); setLoading(true); setError('');
    try {
      const result = await askChatbot({ memberId: member.memberId, question, travelConcept: '맞춤 서울 여행' });
      setMessages((prev) => [...prev, { role: 'bot', text: result.answer, time: formatTime(result.createdAt), course: true }]);
      setHistories((prev) => [result, ...prev]);
    } catch (e) {
      setError(e.message || 'AI 답변을 불러오지 못했습니다.');
    } finally { setLoading(false); }
  };

  return (
    <main className="ai-chat-page" style={{ '--chat-bg': `url(${heroSeoul})` }}>
      <Header variant="simple" />
      <div className="ai-chat-overlay" />
      <div className="ai-chat-shell">
        <aside className="ai-history-panel">
          <button type="button" className="ai-new-chat" onClick={() => { setMessages([]); setError(''); }}><Plus /> 새 대화</button>
          <h2>최근 대화</h2>
          <div className="ai-history-list">
            {histories.slice(0, 6).map((item, index) => <button type="button" className={index === 0 ? 'active' : ''} key={item.chatId}><MessageCircleMore /><span><b>{item.question}</b><small>{formatTime(item.createdAt)}</small></span></button>)}
            {!histories.length && <p className="empty-history">아직 저장된 대화가 없습니다.</p>}
          </div>
          <a href="/payment" className="ai-pass-status"><CalendarDays /><span><b>7일권 · 체험 중</b><small>AI 여행 플래너 이용권</small></span><ChevronRight /></a>
        </aside>

        <section className="ai-conversation">
          <header className="ai-help-header"><h1>무엇을 도와드릴까요?</h1><div>{suggestions.map((item) => <button type="button" key={item} onClick={() => submit(item)}>{item}</button>)}</div></header>
          <div className="ai-message-scroll">
            {!messages.length && <WelcomeMessage name={member.nickname || member.name} onSelect={submit} />}
            {messages.map((message, index) => <ChatMessage key={`${message.time}-${index}`} message={message} />)}
            {loading && <div className="ai-thinking"><span><Bot /></span><p>서울 여행 코스를 만들고 있어요<i /><i /><i /></p></div>}
            {error && <div className="ai-chat-error">{error}<a href="/payment"> 이용권 확인하기</a></div>}
            <div ref={bottomRef} />
          </div>
          <form className="ai-composer" onSubmit={(event) => { event.preventDefault(); submit(); }}>
            <textarea rows="2" value={input} onChange={(event) => setInput(event.target.value)} onKeyDown={(event) => { if (event.key === 'Enter' && !event.shiftKey) { event.preventDefault(); submit(); } }} placeholder="서울 여행에 대해 무엇이든 물어보세요" />
            <button type="submit" aria-label="메시지 보내기" disabled={!input.trim() || loading}><Send /></button>
            <div><button type="button"><ImagePlus /> 사진 업로드</button><button type="button"><Map /> 지도로 질문</button><button type="button" onClick={() => submit(activeTitle)}>추천 코스 다시 짜기</button></div>
          </form>
          <p className="ai-disclaimer">AI가 생성한 답변은 참고용으로 활용해 주세요.</p>
        </section>
      </div>
    </main>
  );
}

function WelcomeMessage({ name, onSelect }) {
  return <div className="ai-welcome"><span><Bot /></span><h2>{name}님, 어떤 서울 여행을 계획하고 계신가요?</h2><p>날씨, 동행, 취향을 알려주시면 맞춤 일정을 만들어 드릴게요.</p><button type="button" onClick={() => onSelect('비 오는 날 서울에서 실내 위주로 하루 코스 추천해줘!')}><Sparkles /> 예시 코스 바로 받아보기</button></div>;
}

function ChatMessage({ message }) {
  if (message.role === 'user') return <div className="ai-user-message"><p>{message.text}</p><time>{message.time}</time></div>;
  return <div className="ai-bot-message"><span className="ai-bot-avatar"><Bot /></span><div className="ai-bot-content"><p className="ai-answer">{message.text}</p>{message.course && <CourseCard />}<div className="ai-feedback"><button type="button"><ThumbsUp /></button><button type="button"><ThumbsDown /></button><button type="button"><Copy /></button></div></div></div>;
}

function CourseCard() {
  return <article className="ai-course-card"><header><div><Sparkles /><h3>서울 AI 추천 하루 코스</h3></div><p><span>소요 시간 <b>약 7시간</b></span><span>예상 비용 <b>1인 38,000원~</b></span></p></header>
    <div className="ai-stop-list">{sampleStops.map((stop, index) => <div className="ai-stop" key={stop.name}><i>{index + 1}</i><img src={stop.image} alt="" /><div><time>{stop.time}</time><h4>{stop.name}</h4><p>{stop.description}</p><small>{stop.area}</small></div><nav><a href="/map-course"><MapPin /> 지도 보기</a><button type="button"><Bookmark /> 저장하기</button></nav></div>)}</div>
    <footer><b>TIP</b> 운영시간과 휴무일은 방문 전에 한 번 더 확인해 주세요.</footer>
  </article>;
}

export default ChatbotPage;

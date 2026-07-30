import { useEffect, useRef, useState } from 'react';
import { Bot, CircleHelp, Coffee, Compass, MessageCircle, Moon, Paperclip, Plus, Send, Sparkles, Umbrella, UserRound, UsersRound } from 'lucide-react';
import Header from '../../components/common/Header';
import { askChatbot, getChatbotHistory } from '../../api/chatbotApi';
import { authStore } from '../../store/authStore';
import heroSeoul from '../../assets/images/hero-seoul-main.png';
import '../../styles/chatbot-history-layout.css';

const prompts = [
  { text: '성수 감성 카페', icon: Coffee },
  { text: '부모님과 서울 여행', icon: UsersRound },
  { text: '비 오는 날 코스', icon: Umbrella },
  { text: '야경 데이트', icon: Moon },
];

const initialMessages = [{
  role: 'bot',
  text: '안녕하세요. 서울 여행 AI 플래너예요.\n여행 날짜, 인원, 원하는 분위기를 알려주시면 바로 맞춤 코스를 제안해 드릴게요.',
  time: '지금',
}];

const formatTime = (value = new Date()) => new Date(value).toLocaleTimeString('ko-KR', { hour: 'numeric', minute: '2-digit' });

// 질문의 핵심 키워드를 이력·통계에 쓸 짧은 여행 테마로 정리한다.
const travelConceptFor = (question) => {
  if (/카페|커피|성수|익선동/.test(question)) return '카페 투어';
  if (/맛집|식당|먹|시장/.test(question)) return '맛집 여행';
  if (/야경|밤|노을/.test(question)) return '야경 여행';
  if (/데이트|연인/.test(question)) return '데이트 여행';
  if (/부모님|아이|가족/.test(question)) return '가족 여행';
  if (/혼자|힐링|산책/.test(question)) return '혼자 힐링 여행';
  if (/비|실내|전시|박물관/.test(question)) return '실내 문화 여행';
  return '맞춤 서울 여행';
};

function ChatbotPage() {
  const member = authStore.getMember();
  const [messages, setMessages] = useState(initialMessages);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [histories, setHistories] = useState([]);
  const [isHistoryOpen, setIsHistoryOpen] = useState(false);
  const [activeHistoryIndex, setActiveHistoryIndex] = useState(null);
  const bottomRef = useRef(null);
  const scrollAreaRef = useRef(null);

  // 로그인 회원의 이전 대화를 불러와 최근 대화 목록에 표시한다.
  useEffect(() => {
    if (!member?.memberId) return;
    getChatbotHistory(member.memberId)
      .then((items = []) => setHistories(items))
      .catch(() => setHistories([]));
  }, [member?.memberId]);

  // 새 메시지·응답·오류가 추가되면 대화 영역을 마지막 메시지까지 자동 스크롤한다.
  useEffect(() => {
    const scrollArea = scrollAreaRef.current;
    if (scrollArea) {
      scrollArea.scrollTo({ top: scrollArea.scrollHeight, behavior: 'smooth' });
    }
  }, [messages, loading, error]);

  const historyItems = histories.slice(0, 5);

  // 사용자 메시지를 즉시 표시한 뒤 AI 추천을 요청한다.
  const submit = async (value = input) => {
    const question = value.trim();
    if (!question || loading) return;

    setMessages((previous) => [...previous, { role: 'user', text: question, time: formatTime() }]);
    setInput('');
    setLoading(true);
    setError('');

    try {
      const result = await askChatbot({
        memberId: member?.memberId,
        question,
        travelConcept: travelConceptFor(question),
      });
      const answer = result?.answer || result?.courseSummary || '요청하신 여행 스타일에 맞는 서울 여행 아이디어를 준비했어요.';
      setMessages((previous) => [...previous, { role: 'bot', text: answer, time: formatTime(result?.createdAt) }]);
      setHistories((previous) => [result, ...previous]);
    } catch (requestError) {
      setError(requestError?.message || '챗봇 응답을 불러오지 못했습니다. 이용권 상태와 서버 연결을 확인해 주세요.');
    } finally {
      setLoading(false);
    }
  };

  // 새 대화를 시작할 때 입력값과 선택한 기록을 초기화한다.
  const resetConversation = () => {
    setMessages(initialMessages);
    setInput('');
    setError('');
    setActiveHistoryIndex(null);
  };

  // 선택한 이전 대화의 질문과 답변을 현재 대화창에 복원한다.
const openHistory = (item, index) => {
    setActiveHistoryIndex(index);
    if (item?.answer || item?.courseSummary) {
      setMessages([
        { role: 'user', text: item.question || '', time: formatTime(item.createdAt) },
        { role: 'bot', text: item.answer || item.courseSummary, time: formatTime(item.createdAt) },
      ]);
      setError('');
    } else {
      resetConversation();
    }
    setIsHistoryOpen(false);
  };

  return (
    <main className="chatbot-page" style={{ '--chat-hero': `url(${heroSeoul})` }}>
      <Header variant="simple" />
      <div className="chatbot-page-overlay" />

      <section className="chatbot-workspace">
        <aside className={`chatbot-sidebar ${isHistoryOpen ? 'history-open' : ''}`}>
          <div className="chatbot-brand-block">
            <span className="chatbot-brand-icon"><Bot /></span>
            <div><strong>AI 여행 플래너</strong><p>나만의 서울 여행을<br />간결하게 계획해 보세요.</p></div>
          </div>

          <button className="chatbot-new-button" type="button" onClick={() => { resetConversation(); setIsHistoryOpen(false); }}><Plus /> 새 대화 만들기</button>

          <section className="chatbot-history-section">
            <header><span>최근 대화</span><small>{histories.length ? `${histories.length}개` : '최근'}</small></header>
            <div className="chatbot-history-list">
              {historyItems.length === 0 && <p className="empty-history">아직 대화 내역이 없습니다.</p>}
              {historyItems.map((item, index) => (
                <button key={`${item.question}-${index}`} type="button" className={activeHistoryIndex === index ? 'active' : ''} onClick={() => openHistory(item, index)}>
                  <MessageCircle /><span>{item.question}</span>
                </button>
              ))}
            </div>
          </section>

          <a className="chatbot-pass-card" href="/mypage/payments">
            <span><Sparkles /> AI 이용권</span>
            <strong>결제 내역 확인하기 <span>→</span></strong>
            <small>결제 후 여행 플래너를 이용할 수 있어요.</small>
          </a>
        </aside>

        <section className="chatbot-room">
          <header className="chatbot-room-header">
            <div className="chatbot-room-title"><span><Bot /></span><div><strong>AI 여행 플래너</strong><small><i /> 언제든 여행을 물어보세요</small></div></div>
            <div className="chatbot-room-actions"><button className="chatbot-history-toggle" type="button" onClick={() => setIsHistoryOpen((open) => !open)} aria-expanded={isHistoryOpen}><MessageCircle /> 최근 대화</button><span className="chatbot-member"><UserRound /> {member?.nickname || member?.name || '여행자'}</span></div>
          </header>

          <div className="chatbot-scroll-area" ref={scrollAreaRef}>
            <section className="chatbot-welcome">
              <span><Compass /></span>
              <div><h1>어떤 서울 여행을 계획하고 있나요?</h1><p>원하는 여행 스타일을 선택하거나, 아래에 자유롭게 입력해 주세요.</p></div>
              <nav>{prompts.map(({ text, icon: Icon }) => <button type="button" key={text} onClick={() => submit(text)}><Icon /> {text}</button>)}</nav>
            </section>

            <section className="chatbot-message-list" aria-live="polite">
              {messages.map((message, index) => <Message key={`${message.time}-${index}`} message={message} />)}
              {loading && <div className="chatbot-thinking"><Bot /><span>여행 코스를 정리하고 있어요<i /><i /><i /></span></div>}
              {error && <div className="chatbot-error"><CircleHelp /><span>{error}</span><a href="/payment">이용권 보기</a></div>}
              <div ref={bottomRef} />
            </section>
          </div>

          <div className="chatbot-composer">
            <form onSubmit={(event) => { event.preventDefault(); submit(); }}>
              <textarea rows="1" value={input} onChange={(event) => setInput(event.target.value)} onKeyDown={(event) => {
                if (event.key === 'Enter' && !event.shiftKey) { event.preventDefault(); submit(); }
              }} placeholder="예: 부모님과 함께 갈 조용한 서울 당일치기 코스를 추천해 줘" />
              <button type="button" className="chatbot-attachment" aria-label="첨부"><Paperclip /></button>
              <button className="chatbot-send" type="submit" disabled={!input.trim() || loading} aria-label="보내기"><Send /></button>
            </form>
            <p>AI 답변은 여행 계획을 위한 참고 정보입니다.</p>
          </div>
        </section>
      </section>
    </main>
  );
}

// 발신자 역할에 따라 서로 다른 메시지 말풍선 구조를 렌더링한다.
function Message({ message }) {
  if (message.role === 'user') {
    return <article className="chatbot-message chatbot-message-user"><div><p>{message.text}</p><time>{message.time}</time></div></article>;
  }
  return <article className="chatbot-message chatbot-message-bot"><span><Bot /></span><div><strong>AI 여행 플래너 <time>{message.time}</time></strong><p>{message.text}</p></div></article>;
}

export default ChatbotPage;

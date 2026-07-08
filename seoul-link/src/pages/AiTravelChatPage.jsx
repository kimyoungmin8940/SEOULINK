import { useEffect, useState } from "react";
import { chatbotApi } from "../api/chatbotApi";
import "../styles/AiTravelChatPage.css";

const DEFAULT_MEMBER_ID = 1;

export default function AiTravelChatPage() {
    const [memberId, setMemberId] = useState(DEFAULT_MEMBER_ID);
    const [travelConcept, setTravelConcept] = useState("서울 감성 데이트 코스");
    const [question, setQuestion] = useState("비 오는 날 실내 위주로 갈 수 있는 서울 코스를 추천해줘");
    const [answer, setAnswer] = useState("");
    const [payments, setPayments] = useState([]);
    const [histories, setHistories] = useState([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState("");

    useEffect(() => {
        loadStatus();
    }, []);

    async function loadStatus() {
        setError("");

        try {
            const [paymentData, historyData] = await Promise.all([
                chatbotApi.payments(memberId),
                chatbotApi.histories(memberId),
            ]);

            setPayments(paymentData || []);
            setHistories(historyData || []);
        } catch (err) {
            setError(err.message);
        }
    }

    async function handleBuyPass() {
        setLoading(true);
        setError("");

        try {
            await chatbotApi.buyPass(memberId);
            await loadStatus();
        } catch (err) {
            setError(err.message);
        } finally {
            setLoading(false);
        }
    }

    async function handleAsk(event) {
        event.preventDefault();

        setLoading(true);
        setError("");
        setAnswer("");

        try {
            const result = await chatbotApi.ask({
                memberId,
                question,
                travelConcept,
            });

            setAnswer(result.answer || "");
            await loadStatus();
        } catch (err) {
            setError(err.message);
        } finally {
            setLoading(false);
        }
    }

    const activePayment = payments.find(
        (payment) => payment.paymentStatus === "PAID" && payment.remainCount > 0
    );

    return (
        <main className="test-page">
            <section className="test-panel">
                <h1>OpenAI 챗봇 테스트</h1>
                <p className="description">
                    백엔드의 <b>/api/chatbot/ask</b> 호출이 정상 동작하는지 확인하는 임시 화면입니다.
                </p>

                <div className="status-box">
                    <div>
                        <span>회원 ID</span>
                        <input
                            type="number"
                            value={memberId}
                            onChange={(event) => setMemberId(Number(event.target.value))}
                        />
                    </div>

                    <div>
                        <span>이용권 상태</span>
                        <strong>
                            {activePayment
                                ? `사용 가능 ${activePayment.remainCount}회`
                                : "사용 가능한 이용권 없음"}
                        </strong>
                    </div>

                    <button type="button" onClick={loadStatus} disabled={loading}>
                        상태 새로고침
                    </button>

                    <button type="button" onClick={handleBuyPass} disabled={loading}>
                        테스트 이용권 만들기
                    </button>
                </div>

                <form className="chat-form" onSubmit={handleAsk}>
                    <label>
                        여행 컨셉
                        <input
                            value={travelConcept}
                            onChange={(event) => setTravelConcept(event.target.value)}
                            placeholder="예: 서울 감성 데이트 코스"
                        />
                    </label>

                    <label>
                        질문
                        <textarea
                            value={question}
                            onChange={(event) => setQuestion(event.target.value)}
                            placeholder="예: 비 오는 날 실내 위주 코스 추천해줘"
                            rows={5}
                        />
                    </label>

                    <button type="submit" disabled={loading}>
                        {loading ? "요청 중..." : "AI 추천 요청"}
                    </button>
                </form>

                {error && (
                    <section className="result error">
                        <h2>에러</h2>
                        <pre>{error}</pre>
                    </section>
                )}

                {answer && (
                    <section className="result">
                        <h2>AI 답변</h2>
                        <pre>{answer}</pre>
                    </section>
                )}

                <section className="result">
                    <h2>최근 히스토리</h2>

                    {histories.length === 0 ? (
                        <p>아직 히스토리가 없습니다.</p>
                    ) : (
                        histories.slice(0, 5).map((history) => (
                            <article className="history-card" key={history.chatId}>
                                <strong>{history.travelConcept}</strong>
                                <p>{history.question}</p>
                                <small>courseId: {history.courseId}</small>
                            </article>
                        ))
                    )}
                </section>
            </section>
        </main>
    );
}
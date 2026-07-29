import { useEffect, useState } from "react";
import { apiGet } from "../api/client";

function SimpleMyPage({ member, onLogout }) {
    const [myPage, setMyPage] = useState(null);
    const [message, setMessage] = useState("");

    useEffect(() => {
        apiGet(`/mypage/${member.memberId}`)
            .then((data) => {
                setMyPage(data);
            })
            .catch((error) => {
                setMessage(error.message);
            });
    }, [member.memberId]);

    return (
        <main className="simple-page">
            <section className="simple-box wide">
                <h1>마이페이지</h1>

                <div className="member-info">
                    <p>회원 ID: {member.memberId}</p>
                    <p>이메일: {member.email}</p>
                    <p>이름: {member.name}</p>
                    <p>닉네임: {member.nickname || "-"}</p>
                </div>

                <button type="button" onClick={onLogout}>
                    로그아웃
                </button>

                {message && <p className="error">{message}</p>}

                {myPage && (
                    <div className="mypage-data">
                        <h2>마이페이지 API 결과</h2>

                        <h3>회원 정보</h3>
                        <pre>{JSON.stringify(myPage.member, null, 2)}</pre>

                        <h3>여행 타입</h3>
                        <pre>{JSON.stringify(myPage.travelType, null, 2)}</pre>

                        <h3>내 코스</h3>
                        <pre>{JSON.stringify(myPage.courses, null, 2)}</pre>

                        <h3>결제 내역</h3>
                        <pre>{JSON.stringify(myPage.payments, null, 2)}</pre>

                        <h3>챗봇 기록</h3>
                        <pre>{JSON.stringify(myPage.chatbotHistories, null, 2)}</pre>

                        <h3>리뷰</h3>
                        <pre>{JSON.stringify(myPage.reviews, null, 2)}</pre>
                    </div>
                )}
            </section>
        </main>
    );
}

export default SimpleMyPage;
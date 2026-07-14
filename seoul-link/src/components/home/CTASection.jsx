// CTASection은 페이지 하단에서 사용자의 다음 행동을 유도하는 배너
// CTA는 Call To Action의 약자로, 여기서는 "취향 검사 시작하기" 버튼을 강조
import { ArrowRight, Sparkles } from 'lucide-react';
import ctaImg from '../../assets/images/cta-seoul-night.jpg';

function CTASection() {
    return (
        <section
            className="cta"
            // CSS 변수 --cta-image에 배경 이미지를 전달
            // cta.css에서는 background-image: var(--cta-image) 형태로 이 값을 사용
            style={{ '--cta-image': `url(${ctaImg})` }}
        >
            {/* 왼쪽 문구 영역*/}
            <div className="cta-copy">
                <h2>
                    몇 가지 질문으로<br />
                    나만의 서울 코스를 받아보세요
                </h2>
                <p>오늘의 기분에 맞는 서울 여행을 시작해보세요.</p>
            </div>

            {/* 취향 검사 전에 여행 기본 정보 입력 화면으로 이동하는 CTA 버튼 */}
            <a
                className="cta-button"
                href="/travel-info"
            >
                <Sparkles className="cta-button-icon" size={20} strokeWidth={2.2} />
                <span>취향 검사 시작하기</span>
                <ArrowRight className="cta-button-arrow" size={19} strokeWidth={2.3} />
            </a>
        </section>
    );
}

export default CTASection;

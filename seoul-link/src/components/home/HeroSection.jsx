// HeroSection은 메인페이지에서 가장 먼저 보이는 큰 비주얼 영역
// 배경 이미지, 대표 문구, 주요 버튼 2개를 보여줌
import heroImg from '../../assets/images/hero-seoul-main.png';
import { Sparkles, Map } from 'lucide-react';
import { handleProtectedLinkClick } from '../../utils/authGuard';

function HeroSection() {
    return (
        <section
            className="hero"
            // 히어로는 hero.css의 전용 그라데이션 필터를 이미 사용하므로
            // 전체 사진 공통 10% 필터에서는 제외해 이중 적용을 방지
            data-photo-filter="off"
            // React에서 배경 이미지를 동적으로 넣기 위해 inline style을 사용
            // hero.css에서는 이 이미지 위에 기존 파란 필터/어두운 오버레이를 ::before로 얹음
            style={{ backgroundImage: `url(${heroImg})` }}
        >
            {/* hero-content는 문구와 버튼을 감싸는 텍스트 박스*/}
            <div className="hero-content">
                {/*
                    h1 문구는 줄 단위로 span을 나눴음
                    이렇게 하면 CSS에서 각 줄의 위치, 굵기, 간격을 따로 조정하기 쉬움
                */}
                <h1 className="hero-title">
                    <span className="hero-line">오늘의 서울은,</span>
                    <span className="hero-line">
                        당신의 <span className="taste-word">취향</span>으로
                    </span>
                    <span className="hero-line hero-line-last">이어집니다</span>
                </h1>

                {/* 대표 문구 아래에 들어가는 짧은 설명 */}
                <p>
                    감성 가득한 서울 여행,<br />
                    나만의 코스로 발견해보세요.
                </p>

                {/*
                    메인 액션 버튼 영역
                    취향 검사 시작 버튼과 추천 코스 보기 버튼을 나란히 배치
                */}
                <div className="hero-buttons">
                    <a
                        className="primary-btn"
                        href="/travel-info"
                    >
                        <Sparkles className="btn-icon primary-icon" size={22} strokeWidth={2.2} />
                        <span className="btn-text">취향 검사 시작</span>
                    </a>

                    <a
                        className="secondary-btn"
                        href="/courses"
                        onClick={(event) => handleProtectedLinkClick(event)}
                    >
                        <Map className="btn-icon secondary-icon" size={23} strokeWidth={2} />
                        <span className="btn-text">추천 코스 보기</span>
                    </a>
                </div>
            </div>
        </section>
    );
}

export default HeroSection;

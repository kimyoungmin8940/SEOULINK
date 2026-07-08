// MoodSection은 "지금 이 순간, 어떤 서울이 끌리시나요?" 영역
// 사용자가 분위기별로 서울 코스를 탐색할 수 있게 보여주는 무드 카드 섹션
import {
    Camera,
    ChevronLeft,
    ChevronRight,
    CloudRain,
    Footprints,
    Heart,
    Sun,
    Utensils,
} from 'lucide-react';

import sunset from '../assets/images/moods/mood-sunset-seoul.png';
import rain from '../assets/images/moods/mood-rainy-cafe.png';
import alley from '../assets/images/moods/mood-walking-alley.png';
import night from '../assets/images/moods/mood-date-night.png';
import hanok from '../assets/images/moods/mood-hanok-photo.png';
import food from '../assets/images/moods/mood-local-food.png';

// 무드 카드 데이터
// image: 카드 배경 이미지
// title: 두 줄로 나눠 보여주기 위해 배열로 작성
// Icon: 카드 위에 띄울 lucide-react 아이콘 컴포넌트
const moods = [
    { image: sunset, title: ['노을이', '예쁜 서울'], Icon: Sun },
    { image: rain, title: ['비 오는 날의', '카페'], Icon: CloudRain },
    { image: alley, title: ['혼자 걷기 좋은', '골목'], Icon: Footprints },
    { image: night, title: ['데이트하기', '좋은 밤'], Icon: Heart },
    { image: hanok, title: ['사진 찍기 좋은', '한옥길'], Icon: Camera },
    { image: food, title: ['로컬처럼', '먹는 하루'], Icon: Utensils },
];

function MoodSection() {
    return (
        <section className="section mood-section">
            {/* mood-shell은 흰색 카드형 배경을 만드는 바깥 박스 */}
            <div className="mood-shell">
                {/* 섹션 제목과 전체 보기 버튼이 있는 상단 영역*/}
                <div className="mood-header">
                    <div className="mood-title-group">
                        <h2>지금 이 순간, 어떤 서울이 끌리시나요?</h2>
                        <p>서울의 다양한 무드를 한 번에 만나보세요.</p>
                    </div>

                    {/* 추후 무드 전체 목록 페이지로 연결하면 됨 */}
                    <button className="mood-more-btn" type="button">
                        전체 보기
                        <ChevronRight size={16} strokeWidth={2.2} />
                    </button>
                </div>

                {/*
                    카드 목록을 감싸는 영역
                    좌우 화살표 버튼은 현재 디자인용이며, 실제 슬라이드 기능을 붙일 때 사용할 수 있음
                */}
                <div className="mood-carousel">
                    <button className="mood-nav mood-nav-left" type="button" aria-label="이전 무드 보기">
                        <ChevronLeft size={24} strokeWidth={2.4} />
                    </button>

                    <div className="mood-list">
                        {moods.map(({ image, title, Icon }) => (
                            <button className="mood-card" type="button" key={title.join(' ')}>
                                <img src={image} alt={title.join(' ')} />

                                {/*
                                    이미지 위에 어두운 그라데이션과 아이콘/문구를 올리는 영역
                                    실제 배경 그라데이션은 mood.css의 .mood-overlay에서 처리
                                */}
                                <div className="mood-overlay">
                                    <Icon className="mood-icon" size={34} strokeWidth={1.35} />
                                    <strong>
                                        {title.map((line) => (
                                            <span key={line}>{line}</span>
                                        ))}
                                    </strong>
                                </div>
                            </button>
                        ))}
                    </div>

                    <button className="mood-nav mood-nav-right" type="button" aria-label="다음 무드 보기">
                        <ChevronRight size={24} strokeWidth={2.4} />
                    </button>
                </div>
            </div>
        </section>
    );
}

export default MoodSection;

// RecommendSection은 메인페이지의 "취향에 맞는 추천 코스" 영역
// 현재는 화면 확인을 위한 임시 데이터(courses)를 사용하고 있음
// 실제 백엔드 데이터가 생기면 courses 배열 대신 API 응답 데이터를 넣으면 됨
import CourseCard from './CourseCard';
import { Sparkles, ChevronRight } from 'lucide-react';

import rainyCafe from '../assets/images/moods/mood-rainy-cafe.png';
import sunsetSeoul from '../assets/images/moods/mood-sunset-seoul.png';
import hanokPhoto from '../assets/images/moods/mood-hanok-photo.png';
import walkingAlley from '../assets/images/moods/mood-walking-alley.png';

// 추천 코스 임시 데이터
// 실제 프로젝트에서는 서버에서 받아온 코스 목록으로 대체하면 됨
// CourseCard 컴포넌트는 아래 속성들을 사용
// - courseId: React key로 사용할 고유 번호
// - title: 코스 제목
// - description: 코스 설명
// - imageUrl: 카드 상단 이미지
// - duration: 예상 소요 시간
// - area: 지역명
// - tags: 카드 하단에 보일 태그 배열
const courses = [
    {
        courseId: 1,
        title: '성수 카페 & 감성 코스',
        description: '감성 카페와 카페 앞 공간을 따라 걷는 성수 하루',
        imageUrl: rainyCafe,
        duration: '약 5시간',
        area: '성수동',
        tags: ['카페투어', '감성', '핫플'],
    },
    {
        courseId: 2,
        title: '한강 노을 산책 코스',
        description: '노을과 함께하는 한강 산책과 맛집 투어',
        imageUrl: sunsetSeoul,
        duration: '약 4시간',
        area: '여의도 · 반포',
        tags: ['한강', '노을', '데이트'],
    },
    {
        courseId: 3,
        title: '익선동 골목 데이트 코스',
        description: '한옥 골목에서 즐기는 감성 데이트',
        imageUrl: hanokPhoto,
        duration: '약 3시간',
        area: '익선동',
        tags: ['골목', '데이트', '카페'],
    },
    {
        courseId: 4,
        title: '북촌 감성 산책 코스',
        description: '한옥과 갤러리가 어우러진 북촌 산책',
        imageUrl: walkingAlley,
        duration: '약 4시간',
        area: '북촌',
        tags: ['북촌', '문화', '사진명소'],
    },
];

function RecommendSection() {
    return (
        <section className="section">
            {/* 섹션 상단 제목/설명/더보기 버튼 영역 */}
            <div className="course-heading">
                <div className="course-heading-left">
                    <div className="course-heading-title">
                        <Sparkles className="course-heading-icon" size={21} strokeWidth={2.2} />
                        <h2>취향에 맞는 추천 코스</h2>
                    </div>

                    <p>지금 가장 인기 있는 코스를 미리 만나보세요.</p>
                </div>

                {/*
                    전체 보기 버튼
                    추후 추천 코스 목록 페이지가 생기면 해당 경로로 이동하도록 연결하면 됨
                */}
                <button className="course-more-btn" type="button">
                    전체 보기
                    <ChevronRight size={16} strokeWidth={2.2} />
                </button>
            </div>

            {/*
                추천 코스 카드 목록
                courses 배열을 반복하면서 CourseCard 컴포넌트에 course 객체를 전달함
            */}
            <div className="course-grid">
                {courses.map((course) => (
                    <CourseCard key={course.courseId} course={course} />
                ))}
            </div>
        </section>
    );
}

export default RecommendSection;

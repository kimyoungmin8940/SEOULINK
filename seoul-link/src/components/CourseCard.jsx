// CourseCard는 추천 코스 하나를 카드 형태로 보여주는 재사용 컴포넌트
// RecommendSection에서 courses 배열을 map으로 돌리면서 이 컴포넌트에 course 데이터를 전달
import { Heart, Clock3, MapPin } from 'lucide-react';

function CourseCard({ course }) {
    return (
        <article className="course-card">
            {/* 코스 대표 이미지 영역*/}
            <div className="course-img-wrap">
                {/* imageUrl이 있을 때만 이미지를 렌더링*/}
                {course.imageUrl && (
                    <img src={course.imageUrl} alt={course.title} />
                )}

                {/*
                    찜하기 버튼
                    현재는 디자인용 버튼이고, 나중에 로그인 사용자 찜 기능 API와 연결하면 됨
                */}
                <button className="heart-btn" type="button" aria-label="찜하기">
                    <Heart className="heart-icon" size={24} strokeWidth={1.85} />
                </button>
            </div>

            {/* 코스 제목, 설명, 소요시간, 지역, 태그를 보여주는 정보 영역*/}
            <div className="course-info">
                <h3>{course.title}</h3>
                <p>{course.description}</p>

                {/* 소요시간과 지역 정보. 아이콘과 텍스트를 한 줄에 배치*/}
                <div className="course-meta">
                    <span>
                        <Clock3 size={14} strokeWidth={2} />
                        {course.duration}
                    </span>
                    <span>
                        <MapPin size={14} strokeWidth={2} />
                        {course.area}
                    </span>
                </div>

                {/* 코스 태그 목록. 예: #카페투어 #감성 #핫플 */}
                <div className="tags">
                    {course.tags.map((tag) => (
                        <span key={tag}>#{tag}</span>
                    ))}
                </div>
            </div>
        </article>
    );
}

export default CourseCard;

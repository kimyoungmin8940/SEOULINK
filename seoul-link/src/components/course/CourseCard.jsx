// CourseCard는 추천 코스 하나를 카드 형태로 보여주는 재사용 컴포넌트
// RecommendSection에서 courses 배열을 map으로 돌리면서 이 컴포넌트에 course 데이터를 전달
import { Clock3, MapPin } from 'lucide-react';
import { requireLogin } from '../../utils/authGuard';

const COURSE_DETAIL_ENTRY_KEY = 'seoulinkCourseDetailEntry';

function getCourseId(course) {
    const courseId = Number(
        course?.courseId
        ?? course?.savedCourseId
        ?? course?.recommendationId
        ?? course?.id,
    );

    return Number.isInteger(courseId) && courseId > 0 ? courseId : null;
}

function CourseCard({
    course,
    detailPath,
    requiresLogin = false,
}) {
    // 부모 컴포넌트가 전달한 상세 경로로 이동하고,
    // 경로가 없으면 일반 코스 상세 페이지로 이동
    const moveToRecommendedCourseDetail = () => {
        if (requiresLogin && !requireLogin()) {
            return;
        }

        const courseId = getCourseId(course);
        const targetPath = detailPath || (courseId ? `/courses/${courseId}` : null);

        if (!targetPath || !courseId) {
            window.alert('코스 정보를 확인할 수 없습니다. 목록을 새로고침해 주세요.');
            return;
        }

        const targetId = Number(targetPath.split('/').filter(Boolean).pop());

        // 상세 화면이 메인/전체 목록 중 실제 진입 위치로 돌아갈 수 있도록
        // 클릭한 카드의 요약 정보와 현재 주소를 한 번만 전달합니다.
        sessionStorage.setItem(COURSE_DETAIL_ENTRY_KEY, JSON.stringify({
            detailId: Number.isInteger(targetId) && targetId > 0 ? targetId : courseId,
            returnPath: `${window.location.pathname}${window.location.search}`,
            summary: {
                ...course,
                coverImageUrl: course.coverImageUrl || course.imageUrl || null,
            },
        }));

        window.location.href = targetPath;
    };

    const handleKeyDown = (event) => {
        if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            moveToRecommendedCourseDetail();
        }
    };

    return (
        <article
            className="course-card"
            role="link"
            tabIndex={0}
            onClick={moveToRecommendedCourseDetail}
            onKeyDown={handleKeyDown}
            aria-label={`${course.title} 추천 코스 상세보기`}
        >
            {/* 코스 대표 이미지 영역*/}
            <div className="course-img-wrap">
                {/* imageUrl이 있을 때만 이미지를 렌더링*/}
                {course.imageUrl && (
                    <img src={course.imageUrl} alt={course.title} />
                )}
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
                    {(course.tags || []).map((tag) => (
                        <span key={tag}>#{tag}</span>
                    ))}
                </div>
            </div>
        </article>
    );
}

export default CourseCard;

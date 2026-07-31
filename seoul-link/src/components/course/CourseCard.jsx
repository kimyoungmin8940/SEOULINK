// 추천 코스 하나를 카드 형태로 보여주는 재사용 컴포넌트
import { Clock3, Heart, MapPin } from 'lucide-react';

import { requireLogin } from '../../utils/authGuard';
import {
    getCourseCoverImageUrls,
    getCourseFallbackImage,
} from '../../utils/courseImage';
import CourseImage from './CourseImage';

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
    variant = 'default',
    showHeart = false,
    maxTags = null,
}) {
    const coverImageUrls = getCourseCoverImageUrls(course);
    const fallbackImageUrl = course?.coverFallbackImageUrl
        || getCourseFallbackImage(course);
    const visibleTags = Number.isInteger(maxTags)
        ? (course.tags || []).slice(0, maxTags)
        : (course.tags || []);

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

        // 목록과 상세가 동일한 대표사진 후보를 사용하도록 현재 카드 요약도 함께 전달한다.
        sessionStorage.setItem(COURSE_DETAIL_ENTRY_KEY, JSON.stringify({
            detailId: Number.isInteger(targetId) && targetId > 0 ? targetId : courseId,
            returnPath: `${window.location.pathname}${window.location.search}`,
            summary: {
                ...course,
                coverImageUrl: coverImageUrls[0] || null,
                coverImageUrls,
                coverFallbackImageUrl: fallbackImageUrl,
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

    const cardClassName = [
        'course-card',
        variant === 'home' ? 'course-card--home' : '',
    ].filter(Boolean).join(' ');

    return (
        <article
            className={cardClassName}
            role="link"
            tabIndex={0}
            onClick={moveToRecommendedCourseDetail}
            onKeyDown={handleKeyDown}
            aria-label={`${course.title} 추천 코스 상세보기`}
        >
            <div className="course-img-wrap">
                <CourseImage
                    imageUrls={coverImageUrls}
                    fallbackImageUrl={fallbackImageUrl}
                    alt={`${course.title} 대표 이미지`}
                    fallbackLabel="예시 사진"
                    fallbackLabelClassName="course-card-example-image-label"
                />

                {showHeart && (
                    <span className="course-card-heart" aria-hidden="true">
                        <Heart size={24} strokeWidth={1.9} />
                    </span>
                )}
            </div>

            <div className="course-info">
                <h3>{course.title}</h3>
                <p>{course.description}</p>

                <div className="course-meta">
                    {course.duration && (
                        <span>
                            <Clock3 size={14} strokeWidth={2} />
                            {course.duration}
                        </span>
                    )}
                    {course.area && (
                        <span>
                            <MapPin size={14} strokeWidth={2} />
                            {course.area}
                        </span>
                    )}
                </div>

                <div className="tags">
                    {visibleTags.map((tag) => (
                        <span key={tag}>#{tag}</span>
                    ))}
                </div>
            </div>
        </article>
    );
}

export default CourseCard;

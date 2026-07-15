// MoodSection은 "지금 이 순간, 어떤 서울이 끌리시나요?" 영역
// 취향 검사 결과와 별개로, 미리 만들어진 테마별 코스를 탐색하는 입구 역할
import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';
import { flushSync } from 'react-dom';
import {
    Camera,
    ChevronLeft,
    ChevronRight,
    CloudRain,
    Footprints,
    Heart,
    Palette,
    Sun,
    Utensils,
} from 'lucide-react';

import sunset from '../../assets/images/moods/mood-sunset-seoul.png';
import rain from '../../assets/images/moods/mood-rainy-cafe.png';
import alley from '../../assets/images/moods/mood-walking-alley.png';
import night from '../../assets/images/moods/mood-date-night.png';
import hanok from '../../assets/images/moods/mood-hanok-photo.png';
import food from '../../assets/images/moods/mood-local-food.png';

// 무드 카드 데이터
// themeCode: 테마별 추천 코스 목록 페이지로 이동할 때 쓰는 코드
// image: 카드 배경 이미지
// title: 두 줄로 나눠 보여주기 위해 배열로 작성
// Icon: 카드 위에 띄울 lucide-react 아이콘 컴포넌트
const moods = [
    { themeCode: 'sunset', image: sunset, title: ['노을이', '예쁜 서울'], Icon: Sun },
    { themeCode: 'rainy-cafe', image: rain, title: ['비 오는 날의', '카페'], Icon: CloudRain },
    { themeCode: 'walking-alley', image: alley, title: ['혼자 걷기 좋은', '골목'], Icon: Footprints },
    { themeCode: 'night-date', image: night, title: ['데이트하기', '좋은 밤'], Icon: Heart },
    { themeCode: 'hanok-photo', image: hanok, title: ['사진 찍기 좋은', '한옥길'], Icon: Camera },
    { themeCode: 'local-food', image: food, title: ['로컬처럼', '먹는 하루'], Icon: Utensils },
];

// 같은 카드 묶음을 앞·가운데·뒤에 이어 붙여 양방향으로 끊김 없이 순환시킴
const LOOP_COPY_COUNT = 3;
const INITIAL_TRACK_INDEX = moods.length;
const SLIDE_DURATION = 430;

// CSS의 반응형 카드 개수와 동일하게 유지해야 양끝 미리보기 위치가 정확히 맞음
const getVisibleMoodCount = () => {
    if (typeof window === 'undefined') return 5;
    if (window.matchMedia('(max-width: 720px)').matches) return 1;
    if (window.matchMedia('(max-width: 1100px)').matches) return 3;
    return 5;
};

function MoodSection() {
    const viewportRef = useRef(null);
    const trackRef = useRef(null);
    const currentIndexRef = useRef(INITIAL_TRACK_INDEX);
    const pendingStepsRef = useRef(0);
    const isMovingRef = useRef(false);
    const slideTimerRef = useRef(null);
    const resetFrameRef = useRef(null);
    const resizeFrameRef = useRef(null);
    const runQueuedMoveRef = useRef(() => {});

    const [currentTrackIndex, setCurrentTrackIndex] = useState(INITIAL_TRACK_INDEX);
    const [visibleCount, setVisibleCount] = useState(getVisibleMoodCount);

    const moveToThemeCourses = (themeCode) => {
        window.location.assign(`/courses/themes/${themeCode}`);
    };

    // 선택된 첫 번째 카드를 가운데 카드 영역의 시작점에 정확히 맞춤
    const alignToIndex = useCallback((trackIndex, behavior = 'auto') => {
        const viewport = viewportRef.current;
        const track = trackRef.current;
        const targetCard = track?.querySelector('[data-track-index="' + trackIndex + '"]');

        if (!viewport || !track || !targetCard) return;

        // getBoundingClientRect()는 미리보기 카드의 scale()까지 반영하므로
        // 왼쪽 이동 때만 위치가 밀린다. transform 영향을 받지 않는 실제 카드 폭으로 계산한다.
        const trackStyle = window.getComputedStyle(track);
        const cardStyle = window.getComputedStyle(targetCard);
        const cardWidth = Number.parseFloat(cardStyle.width) || targetCard.offsetWidth;
        const cardGap = Number.parseFloat(trackStyle.columnGap || trackStyle.gap) || 0;
        const cardLeft = trackIndex * (cardWidth + cardGap);
        const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

        viewport.scrollTo({
            left: Math.max(0, cardLeft),
            behavior: reduceMotion ? 'auto' : behavior,
        });
    }, []);

    // 빠르게 여러 번 눌러도 입력을 순서대로 처리해 카드가 건너뛰거나 멈추지 않게 함
    const runQueuedMove = useCallback(() => {
        if (isMovingRef.current || pendingStepsRef.current === 0) return;

        const direction = pendingStepsRef.current > 0 ? 1 : -1;
        pendingStepsRef.current -= direction;
        isMovingRef.current = true;

        const targetIndex = currentIndexRef.current + direction;
        currentIndexRef.current = targetIndex;
        setCurrentTrackIndex(targetIndex);
        alignToIndex(targetIndex, 'smooth');

        const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
        const finishDelay = reduceMotion ? 30 : SLIDE_DURATION;

        window.clearTimeout(slideTimerRef.current);
        slideTimerRef.current = window.setTimeout(() => {
            let normalizedIndex = targetIndex;

            // 복제 묶음에 도착하면 같은 카드가 있는 가운데 묶음으로 눈에 띄지 않게 이동
            if (targetIndex >= moods.length * 2) {
                normalizedIndex = targetIndex - moods.length;
            } else if (targetIndex < moods.length) {
                normalizedIndex = targetIndex + moods.length;
            }

            if (normalizedIndex !== targetIndex) {
                const track = trackRef.current;

                // 경계 보정 중에는 카드 상태 전환을 끈다. 상태와 스크롤 위치를 같은
                // 프레임에서 함께 옮겨 숨겨진 복제 카드가 잠깐 보이는 현상을 막는다.
                track?.classList.add('mood-list--resetting');
                currentIndexRef.current = normalizedIndex;
                flushSync(() => {
                    setCurrentTrackIndex(normalizedIndex);
                });
                alignToIndex(normalizedIndex, 'auto');

                if (track) {
                    // 전환이 꺼진 상태를 브라우저에 먼저 확정한 다음 다음 프레임에 복원
                    void track.offsetWidth;
                    window.cancelAnimationFrame(resetFrameRef.current);
                    resetFrameRef.current = window.requestAnimationFrame(() => {
                        track.classList.remove('mood-list--resetting');
                    });
                }
            }

            isMovingRef.current = false;
            window.requestAnimationFrame(() => runQueuedMoveRef.current());
        }, finishDelay);
    }, [alignToIndex]);

    useEffect(() => {
        runQueuedMoveRef.current = runQueuedMove;
    }, [runQueuedMove]);

    const queueMove = useCallback((direction) => {
        pendingStepsRef.current += direction;
        runQueuedMoveRef.current();
    }, []);

    // 첫 화면에서 가운데 복제 묶음으로 바로 이동해 왼쪽에도 이전 카드가 보이게 함
    useLayoutEffect(() => {
        const frameId = window.requestAnimationFrame(() => {
            alignToIndex(currentIndexRef.current, 'auto');
        });

        return () => window.cancelAnimationFrame(frameId);
    }, [alignToIndex, visibleCount]);

    // 화면 크기가 바뀌면 5개/3개/1개 반응형 기준과 현재 카드 위치를 다시 맞춤
    useEffect(() => {
        const handleResize = () => {
            setVisibleCount(getVisibleMoodCount());
            window.cancelAnimationFrame(resizeFrameRef.current);
            resizeFrameRef.current = window.requestAnimationFrame(() => {
                if (!isMovingRef.current) {
                    alignToIndex(currentIndexRef.current, 'auto');
                }
            });
        };

        window.addEventListener('resize', handleResize);

        return () => {
            window.removeEventListener('resize', handleResize);
            window.cancelAnimationFrame(resizeFrameRef.current);
        };
    }, [alignToIndex]);

    useEffect(() => () => {
        window.clearTimeout(slideTimerRef.current);
        window.cancelAnimationFrame(resetFrameRef.current);
        trackRef.current?.classList.remove('mood-list--resetting');
        pendingStepsRef.current = 0;
    }, []);

    return (
        <section className="section mood-section">
            {/* mood-shell은 흰색 카드형 배경을 만드는 바깥 박스 */}
            <div className="mood-shell">
                {/* 섹션 제목 영역*/}
                <div className="mood-header">
                    <div className="mood-title-group">
                        <div className="mood-heading-title">
                            <Palette className="mood-heading-icon" size={21} strokeWidth={2.2} />
                            <h2>지금 이 순간, 어떤 서울이 끌리시나요?</h2>
                        </div>
                        <p>원하는 분위기를 선택하면 테마별 추천 코스를 볼 수 있어요.</p>
                    </div>

                    <a className="mood-more-btn" href="/courses/themes">
                        전체 보기
                        <ChevronRight size={16} strokeWidth={2.2} />
                    </a>
                </div>

                {/* 5개 본 카드와 양끝의 작고 흐린 이전/다음 카드를 보여주는 무한 슬라이드 */}
                <div className="mood-carousel">
                    <button
                        className="mood-nav mood-nav-left"
                        type="button"
                        onClick={() => queueMove(-1)}
                        aria-label="이전 테마 보기"
                        aria-controls="mood-carousel-viewport"
                    >
                        <ChevronLeft size={24} strokeWidth={2.2} aria-hidden="true" />
                    </button>

                    <div
                        className="mood-viewport"
                        id="mood-carousel-viewport"
                        ref={viewportRef}
                        role="region"
                        aria-roledescription="carousel"
                        aria-label="서울 테마 추천"
                    >
                        <div className="mood-list" ref={trackRef}>
                            {Array.from({ length: LOOP_COPY_COUNT }, (_, copyIndex) => (
                                moods.map(({ themeCode, image, title, Icon }, moodIndex) => {
                                    const trackIndex = copyIndex * moods.length + moodIndex;
                                    const relativeIndex = trackIndex - currentTrackIndex;
                                    const isActive = relativeIndex >= 0 && relativeIndex < visibleCount;
                                    const isLeftPreview = relativeIndex === -1;
                                    const isRightPreview = relativeIndex === visibleCount;
                                    const cardStateClass = isActive
                                        ? 'mood-card--active'
                                        : isLeftPreview
                                            ? 'mood-card--preview mood-card--preview-left'
                                            : isRightPreview
                                                ? 'mood-card--preview mood-card--preview-right'
                                                : 'mood-card--offstage';

                                    return (
                                        <button
                                            className={['mood-card', cardStateClass].join(' ')}
                                            type="button"
                                            key={[copyIndex, themeCode].join('-')}
                                            data-track-index={trackIndex}
                                            onClick={() => moveToThemeCourses(themeCode)}
                                            aria-label={title.join(' ') + ' 테마 추천 코스 보기'}
                                            aria-hidden={!isActive}
                                            tabIndex={isActive ? 0 : -1}
                                        >
                                            <img src={image} alt="" draggable="false" />

                                            {/* 이미지 위의 어두운 그라데이션과 아이콘/문구 */}
                                            <div className="mood-overlay">
                                                <Icon className="mood-icon" size={34} strokeWidth={1.35} />
                                                <strong>
                                                    {title.map((line) => (
                                                        <span key={line}>{line}</span>
                                                    ))}
                                                </strong>
                                            </div>
                                        </button>
                                    );
                                })
                            ))}
                        </div>
                    </div>

                    <button
                        className="mood-nav mood-nav-right"
                        type="button"
                        onClick={() => queueMove(1)}
                        aria-label="다음 테마 보기"
                        aria-controls="mood-carousel-viewport"
                    >
                        <ChevronRight size={24} strokeWidth={2.2} aria-hidden="true" />
                    </button>
                </div>
            </div>
        </section>
    );
}

export default MoodSection;
import { useEffect, useMemo, useState } from 'react';

import {
    normalizeCourseImageUrl,
    normalizeCourseImageUrls,
} from '../../utils/courseImage';

/**
 * 실제 이미지 후보를 순서대로 시도하고, 모두 없거나 실패했을 때만 예시 이미지를 씁니다.
 */
function CourseImage({
    imageUrls,
    fallbackImageUrl = null,
    alt,
    className,
    fallbackLabel = null,
    fallbackLabelClassName,
    emptyClassName,
}) {
    const candidates = useMemo(
        () => normalizeCourseImageUrls(imageUrls),
        [imageUrls],
    );
    const fallback = normalizeCourseImageUrl(fallbackImageUrl);
    const candidateKey = candidates.join('|');
    const [candidateIndex, setCandidateIndex] = useState(0);
    const [fallbackFailed, setFallbackFailed] = useState(false);

    useEffect(() => {
        setCandidateIndex(0);
        setFallbackFailed(false);
    }, [candidateKey, fallback]);

    const candidateSource = candidates[candidateIndex] || null;
    const usingFallback = !candidateSource && Boolean(fallback) && !fallbackFailed;
    const source = candidateSource || (usingFallback ? fallback : null);

    if (!source) {
        return emptyClassName
            ? <span className={emptyClassName} aria-hidden="true" />
            : null;
    }

    return (
        <>
            <img
                className={className}
                src={source}
                alt={alt}
                onError={() => {
                    if (candidateSource) {
                        setCandidateIndex((index) => index + 1);
                    } else {
                        setFallbackFailed(true);
                    }
                }}
            />
            {usingFallback && fallbackLabel && (
                <em className={fallbackLabelClassName}>{fallbackLabel}</em>
            )}
        </>
    );
}

export default CourseImage;

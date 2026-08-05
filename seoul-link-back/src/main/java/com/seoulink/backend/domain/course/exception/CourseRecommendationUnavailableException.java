
package com.seoulink.backend.domain.course.exception;

/**
 * 시간·장소 수·중복 제한을 유지하면서 서로 다른 추천 코스 3개를
 * 모두 구성할 수 없을 때 발생한다.
 *
 * <p>예외 메시지는 서버 진단용으로만 사용하고, 사용자 안내 문구는
 * 컨트롤러에서 별도로 반환한다.</p>
 */
public class CourseRecommendationUnavailableException
        extends IllegalStateException {

    public CourseRecommendationUnavailableException(
            String diagnosticMessage
    ) {
        super(diagnosticMessage);
    }
}

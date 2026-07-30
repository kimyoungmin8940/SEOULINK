package com.seoulink.backend.domain.course.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원의 테마 코스 저장 여부를 전달하는 응답 DTO이다.
 *
 * <p>saved가 true이면 courseId에는 DB에 저장된 코스 ID가 들어가고,
 * 저장되지 않은 상태이면 courseId는 null이다.</p>
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThemeCourseBookmarkResponse {

    // 회원이 해당 테마 코스를 저장했는지 여부
    private boolean saved;

    // 저장된 TRAVEL_COURSES의 COURSE_ID
    // 저장되지 않았다면 null
    private Long courseId;

    // 프론트에서 사용하는 원본 테마 코스 식별값
    // 예: sunset-1, rainy-cafe-2, walking-alley-3
    private String sourceCourseKey;
}
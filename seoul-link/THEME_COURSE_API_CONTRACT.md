# 테마 코스 저장 API 계약

프론트는 코스의 장소 목록 전체를 저장 API에 다시 보내지 않습니다.
`sourceCourseKey`만 전달하고, 백엔드가 DB에 등록된 원본 테마 코스와 장소 순서를
`TRAVEL_COURSES`, `COURSE_DETAILS`에 회원 코스로 복사합니다.

## 원본 키

프론트 키 형식:

```text
{THEME_CODE}_{FRONT_COURSE_ID}
```

예:

```text
NIGHT_DATE_1101
HANOK_PHOTO_1201
LOCAL_FOOD_1301
```

백엔드와 테마 코스 시드 데이터도 같은 키를 사용해야 합니다.

## 저장 상태 목록

```http
GET /api/courses/themes/saved?memberId=1
```

응답 예:

```json
[
  {
    "sourceCourseKey": "NIGHT_DATE_1101",
    "savedCourseId": 501,
    "saved": true
  }
]
```

## 저장

```http
POST /api/courses/themes/NIGHT_DATE_1101/save
Content-Type: application/json
```

```json
{
  "memberId": 1
}
```

백엔드는 동일한 `MEMBER_ID`, `SOURCE_COURSE_KEY`가 이미 있으면 중복 행을 만들지
않고 기존 저장 결과를 반환합니다.

## 저장 취소

```http
DELETE /api/courses/themes/NIGHT_DATE_1101/save?memberId=1
```

연결된 `COURSE_DETAILS`를 먼저 삭제한 뒤 회원의 THEME 코스 복사본을 삭제합니다.

## 마이페이지 목록

```http
GET /api/courses/recommended?memberId=1
```

`COURSE_TYPE`이 `SURVEY` 또는 `THEME`인 코스를 모두 최신순으로 반환합니다.

```http
GET /api/courses/my?memberId=1
```

THEME를 포함해 회원이 저장한 모든 코스를 최신순으로 반환합니다.

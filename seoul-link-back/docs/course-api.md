# 코스 추천·조회 API 계약

이 문서는 프론트 추천 결과 화면과 상세 화면이 사용할 코스 API 계약을 고정한다.
추천 결과(`POST /api/courses/recommend`)와 저장 상세(`GET /api/courses/{courseId}`)는
모두 `days[].places[]` 구조를 사용한다. 최적화만 확인하는 개발용 API의
`optimizedPlaces` 구조는 그대로 유지한다.

## 공통 규칙

- 날짜 형식: `YYYY-MM-DD`
- 거리 단위: km
- 시간 단위: 분
- `days`는 `dayNo` 오름차순, `places`는 `visitOrder` 오름차순이다.
- 날짜별 첫 장소를 고정한 뒤 최근접 이웃 경로에 2-opt를 적용하며,
  총 이동시간을 우선하고 동률이면 총 거리를 줄인다.
- 날짜별 첫 장소의 `distanceFromPreviousKm`와
  `travelTimeFromPreviousMinutes`는 `0`이다.
- `recommendationScore`는 추천 직후 응답에서는 채워진다. 저장 상세 조회에서는
  추천 점수 저장 또는 장소 추천 데이터 연동 전까지 `null`일 수 있다.
- 상세 조회의 장소명·주소·이미지·좌표는 PLACES 조회 연동 전까지 `null`일 수 있다.

## 1. 추천 후보 최적화

`POST /api/courses/optimize`

DB에 저장하지 않고 최근접 이웃과 2-opt로 방문 순서와 이동값을 계산한다.

### 요청

```json
{
  "placeCandidates": [
    {
      "placeId": 1,
      "placeName": "서울시청",
      "category": "TOUR",
      "recommendationScore": 100.0,
      "latitude": 37.5665,
      "longitude": 126.978,
      "visitDate": "2026-07-20"
    },
    {
      "placeId": 2,
      "placeName": "먼 관광지",
      "category": "TOUR",
      "recommendationScore": 90.0,
      "latitude": 37.5854,
      "longitude": 126.978,
      "visitDate": "2026-07-20"
    }
  ],
  "alternativeCandidates": [
    {
      "placeId": 3,
      "placeName": "덕수궁",
      "category": "TOUR",
      "recommendationScore": 85.0,
      "latitude": 37.5658,
      "longitude": 126.9751,
      "visitDate": "2026-07-20"
    }
  ]
}
```

### 성공 응답 `200 OK`

```json
{
  "optimizedPlaces": [
    {
      "placeId": 1,
      "placeName": "서울시청",
      "category": "TOUR",
      "recommendationScore": 100.0,
      "latitude": 37.5665,
      "longitude": 126.978,
      "visitDate": "2026-07-20",
      "expectedVisitMinutes": 90,
      "visitOrder": 1,
      "distanceFromPreviousKm": 0.0,
      "travelTimeFromPreviousMinutes": 0.0
    },
    {
      "placeId": 3,
      "placeName": "덕수궁",
      "category": "TOUR",
      "recommendationScore": 85.0,
      "latitude": 37.5658,
      "longitude": 126.9751,
      "visitDate": "2026-07-20",
      "expectedVisitMinutes": 90,
      "visitOrder": 2,
      "distanceFromPreviousKm": 0.2671912132991297,
      "travelTimeFromPreviousMinutes": 3.562549510655063
    }
  ],
  "totalDistanceKm": 0.2671912132991297,
  "totalTravelTimeMinutes": 3.562549510655063,
  "totalVisitTimeMinutes": 180,
  "totalCourseTimeMinutes": 183.56254951065506
}
```

## 2. 추천 생성 및 저장

`POST /api/courses/recommend`

후보 최적화와 `SURVEY` 코스 저장을 하나의 트랜잭션으로 처리한다.

### 요청 필드

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `memberId` | number | Y | 코스 소유 회원 ID. 인증 연동 후 본문에서 제거 예정 |
| `resultId` | number | N | 설문 결과 ID |
| `paymentId` | number | N | 결제 ID |
| `title` | string | Y | 코스 제목, 최대 200자 |
| `description` | string | N | 코스 설명 |
| `travelCode` | string | N | 영문 5자리 여행 유형 코드 |
| `region` | string | N | 대표 지역 |
| `publicCourse` | boolean | N | 기본값 `false` |
| `placeCandidates` | array | Y | 실제 코스에 배치할 후보 |
| `alternativeCandidates` | array | N | 먼 장소 교체용 예비 후보 |

`placeCandidates`와 `alternativeCandidates`의 장소 필드는 최적화 요청과 같다.

### 성공 응답 `201 Created`

```json
{
  "courseId": 20,
  "title": "서울 추천 코스",
  "description": "서울 도심의 대표 관광지를 걷는 코스",
  "travelCode": "ATLSR",
  "courseType": "SURVEY",
  "region": "서울 중구",
  "publicCourse": false,
  "placeCount": 2,
  "dayCount": 1,
  "totalDistanceKm": 0.267,
  "totalTravelTimeMinutes": 3.56,
  "totalVisitTimeMinutes": 180,
  "totalCourseTimeMinutes": 183.56,
  "days": [
    {
      "dayNo": 1,
      "visitDate": "2026-07-20",
      "places": [
        {
          "detailId": null,
          "placeId": 1,
          "placeName": "서울시청",
          "category": "TOUR",
          "address": null,
          "roadAddress": null,
          "imageUrl": null,
          "latitude": 37.5665,
          "longitude": 126.978,
          "recommendationScore": 100.0,
          "visitOrder": 1,
          "memo": null,
          "visitTime": null,
          "expectedVisitMinutes": 90,
          "distanceFromPreviousKm": 0.0,
          "travelTimeFromPreviousMinutes": 0.0
        }
      ]
    }
  ]
}
```

## 3. 저장 코스 상세 조회

`GET /api/courses/{courseId}`

성공 시 `200 OK`이며 추천 생성 응답과 동일한 `days[].places[]` 구조를 사용한다.
추가로 `coverImageUrl`, `viewCount`, `createdAt`, `updatedAt`을 반환한다.

```json
{
  "courseId": 20,
  "title": "서울 추천 코스",
  "description": "서울 도심의 대표 관광지를 걷는 코스",
  "coverImageUrl": null,
  "travelCode": "ATLSR",
  "courseType": "SURVEY",
  "region": "서울 중구",
  "publicCourse": false,
  "viewCount": 0,
  "placeCount": 2,
  "dayCount": 1,
  "totalDistanceKm": 0.267,
  "totalTravelTimeMinutes": 3.56,
  "totalVisitTimeMinutes": 180,
  "totalCourseTimeMinutes": 183.56,
  "createdAt": "2026-07-16T15:00:00",
  "updatedAt": "2026-07-16T15:00:00",
  "days": [
    {
      "dayNo": 1,
      "visitDate": "2026-07-20",
      "places": [
        {
          "detailId": 100,
          "placeId": 1,
          "placeName": null,
          "category": null,
          "address": null,
          "roadAddress": null,
          "imageUrl": null,
          "latitude": null,
          "longitude": null,
          "recommendationScore": null,
          "visitOrder": 1,
          "memo": null,
          "visitTime": null,
          "expectedVisitMinutes": 90,
          "distanceFromPreviousKm": 0.0,
          "travelTimeFromPreviousMinutes": 0.0
        }
      ]
    }
  ]
}
```

## 4. 직접 저장 및 목록 조회

| 메서드 | 경로 | 성공 | 용도 |
|---|---|---:|---|
| `POST` | `/api/courses` | `201` | 사용자가 확정한 최적화 결과 저장 |
| `GET` | `/api/courses/recommended?memberId={id}` | `200` | 회원의 `SURVEY` 추천 코스 카드 목록 |
| `GET` | `/api/members/me/courses?memberId={id}` | `200` | 회원의 전체 코스 카드 목록 |

목록 항목은 `courseId`, `title`, `description`, `coverImageUrl`, `regions`,
`tags`, `placeCount`, `dayCount`, 네 가지 합계값, `liked`를 반환한다.

## 오류 응답

```json
{
  "code": "INVALID_REQUEST",
  "message": "방문 날짜는 필수입니다."
}
```

| HTTP | 코드 | 발생 조건 | 프론트 처리 |
|---:|---|---|---|
| `400` | `INVALID_REQUEST` | 필수값 누락, 잘못된 좌표·순서·ID, 중복 장소 | 입력 확인 메시지 표시 |
| `404` | `COURSE_NOT_FOUND` | 존재하지 않는 `courseId` 조회 | 결과 없음 화면 또는 목록으로 이동 |
| `500` | `COURSE_PROCESSING_FAILED` | 최적화 내부 상태 오류 또는 저장 처리 실패 | 재시도 안내, 내부 상세 원인은 노출하지 않음 |

## 담당자 간 전달 필드

### 1번 담당자에게 받을 값

| 필드 | 필수 시점 | 비고 |
|---|---|---|
| `resultId` | 추천 저장 전 | 설문 결과 DB ID |
| `travelCode` | 추천 요청 전 | 영문 5자리 최종 여행 유형 코드 |
| 여행 시작일·일수 | 후보 날짜 배정 전 | 최종적으로 각 후보의 `visitDate`로 변환 |
| `title`, `description`, `region` | 추천 저장 전 | 담당 경계에 따라 생성 주체 확정 필요 |

### 2번 담당자에게 받을 값

| 필드 | 필수 시점 | 비고 |
|---|---|---|
| `placeId` | 최적화 전 | SEOULINK `PLACES` 내부 ID |
| `placeName`, `category` | 최적화·결과 표시 전 | 카테고리는 체류시간·대체 조건에 사용 |
| `recommendationScore` | 최적화 전 | 높은 점수 우선 및 결과 화면 표시 |
| `latitude`, `longitude` | 최적화 전 | 거리 행렬 계산 필수 |
| `placeCandidates` | 최적화 전 | 실제 코스 1차 후보 |
| `alternativeCandidates` | 먼 장소 교체 전 | 같은 날짜·카테고리 예비 후보 |
| `address`, `roadAddress`, `imageUrl` | 상세 화면 연동 전 | `placeId` 기반 상세 조회로 보강 |

현재 코스 모듈이 계산·반환하는 값은 `courseId`, `dayNo`, `visitOrder`,
`expectedVisitMinutes`, 장소별 거리·이동시간, 전체 거리·시간 합계이다.

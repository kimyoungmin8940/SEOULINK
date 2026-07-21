# 코스 추천·조회 API 계약

이 문서는 프론트 추천 결과 화면과 상세 화면이 사용할 코스 API 계약을 고정한다.
추천 결과(`POST /api/courses/recommend`)는 `courseOptions[].days[].places[]`,
저장 상세(`GET /api/courses/{courseId}`)는 `days[].places[]` 구조를 사용한다. 최적화만 확인하는 개발용 API의
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
  "resultId": 101,
  "travelCode": "ATLSR",
  "dailyStartTime": "10:00",
  "placeCandidates": [
    {
      "placeId": 1,
      "placeName": "서울시청",
      "category": "TOUR",
      "recommendationScore": 100.0,
      "latitude": 37.5665,
      "longitude": 126.978,
      "visitDate": "2026-07-20",
      "themePalaceCultureYn": "Y",
      "themeNatureHangangYn": "N",
      "themeDateYn": "N",
      "themeFoodTourYn": "N",
      "themeCafeTourYn": "N",
      "themeShoppingHotplaceYn": "N",
      "themeNightViewYn": "N",
      "themeHotelStayYn": "N",
      "alternativeCandidates": []
    },
    {
      "placeId": 2,
      "placeName": "먼 관광지",
      "category": "TOUR",
      "recommendationScore": 90.0,
      "latitude": 37.5854,
      "longitude": 126.978,
      "visitDate": "2026-07-20",
      "themePalaceCultureYn": "Y",
      "themeNatureHangangYn": "N",
      "themeDateYn": "N",
      "themeFoodTourYn": "N",
      "themeCafeTourYn": "N",
      "themeShoppingHotplaceYn": "N",
      "themeNightViewYn": "N",
      "themeHotelStayYn": "N",
      "alternativeCandidates": [
        {
          "placeId": 3,
          "placeName": "덕수궁",
          "category": "TOUR",
          "recommendationScore": 85.0,
          "latitude": 37.5658,
          "longitude": 126.9751,
          "themePalaceCultureYn": "Y",
          "themeNatureHangangYn": "N",
          "themeDateYn": "N",
          "themeFoodTourYn": "N",
          "themeCafeTourYn": "N",
          "themeShoppingHotplaceYn": "N",
          "themeNightViewYn": "N",
          "themeHotelStayYn": "N"
        }
      ]
    }
  ]
}
```

- 대체 후보는 교체 대상 장소의 `alternativeCandidates` 안에 넣는다.
- 대체 후보의 `visitDate`는 생략하며, 교체 시 원본 장소의 날짜를 자동 상속한다.
- 한 장소가 여러 테마에 해당하면 여러 `theme*Yn` 필드가 동시에 `Y`일 수 있다.
- 최상위 `alternativeCandidates`는 이전 호출부 호환용이며 신규 요청에서는 사용하지 않는다.

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

## 2. 추천 코스 3개 생성

`POST /api/courses/recommend`

날짜별 후보 풀에서 `targetPlaceCount`와 `categoryTargets`를 만족하는 조합을 만들고,
취향 우선·이동 최소·균형 추천 세 가지 코스를 반환한다. 이 단계에서는 DB에 저장하지 않는다.
사용자가 세 코스 중 하나만 선택하면 `POST /api/courses`, 두 개 이상 선택하면 `POST /api/courses/batch`로 저장한다.

### 요청 필드

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `resultId` | number | Y | 설문 결과 ID |
| `travelCode` | string | N | 5자리 여행 유형 코드. 전달되면 응답에 그대로 포함 |
| `dailyStartTime` | string | Y | 매일 일정 시작 시각, `HH:mm` |
| `dailyPlans` | array | Y | 날짜별 후보 풀과 선발 목표 |
| `dailyPlans[].visitDate` | string | Y | 방문 날짜 |
| `dailyPlans[].targetPlaceCount` | number | Y | 해당 날짜에 최종 선발할 장소 수 |
| `dailyPlans[].categoryTargets` | object | Y | TOUR·RESTAURANT·CAFE·HOTEL별 최종 개수 |
| `dailyPlans[].placeCandidates` | array | Y | 최종 선발 전 후보 풀 |
| `placeCandidates[].alternativeCandidates` | array | N | 해당 장소가 먼 구간일 때만 사용하는 전용 대체 후보 |

`categoryTargets` 값의 합계는 `targetPlaceCount`와 같아야 하며, 후보 풀에는 각
카테고리 목표를 충족할 수 있는 충분한 장소가 있어야 한다.
전체 2일 요청 예시는 `docs/course-recommend-request-example.json`에서 바로 사용할 수 있다.

### 성공 응답 `200 OK`

```json
{
  "resultId": 101,
  "travelCode": "ATLSR",
  "dailyStartTime": "10:00",
  "optionCount": 3,
  "courseOptions": [
    {
      "optionNo": 1,
      "optionType": "PREFERENCE",
      "optionName": "취향 집중 코스",
      "placeCount": 8,
      "dayCount": 2,
      "totalDistanceKm": 6.214,
      "totalTravelTimeMinutes": 82.85,
      "totalVisitTimeMinutes": 600,
      "totalCourseTimeMinutes": 682.85,
      "days": [
        {
          "dayNo": 1,
          "visitDate": "2026-07-20",
          "dailyDistanceKm": 2.731,
          "dailyTravelTimeMinutes": 36.41,
          "dailyVisitTimeMinutes": 300,
          "dailyCourseTimeMinutes": 336.41,
          "places": []
        }
      ]
    },
    {
      "optionNo": 2,
      "optionType": "MIN_DISTANCE",
      "optionName": "이동 최소 코스",
      "placeCount": 8,
      "dayCount": 2,
      "days": []
    },
    {
      "optionNo": 3,
      "optionType": "BALANCED",
      "optionName": "균형 추천 코스",
      "placeCount": 8,
      "dayCount": 2,
      "days": []
    }
  ]
}
```

- `PREFERENCE`: 카테고리 목표 안에서 추천 점수 합계가 높은 조합을 우선한다.
- `MIN_DISTANCE`: 직선거리 기반 예상 이동시간과 거리가 짧은 조합을 우선한다.
- `BALANCED`: 추천 점수 50%, 이동시간 30%, 거리 20%를 정규화해 균형을 맞춘다.
- 가능한 조합이 3개 이상이면 서로 다른 장소 조합을 반환한다. 후보 풀이 부족하면
  일부 옵션의 장소 구성이 겹칠 수 있다.

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
| `POST` | `/api/courses` | `201` | 사용자가 확정한 코스 한 개 저장 |
| `POST` | `/api/courses/batch` | `201` | 선택한 코스 1~3개를 단일 트랜잭션으로 일괄 저장 |
| `GET` | `/api/courses/recommended?memberId={id}` | `200` | 회원의 `SURVEY` 추천 코스 카드 목록 |
| `GET` | `/api/members/me/courses?memberId={id}` | `200` | 회원의 전체 코스 카드 목록 |

목록 항목은 `courseId`, `title`, `description`, `coverImageUrl`, `regions`,
`tags`, `placeCount`, `dayCount`, 네 가지 합계값, `liked`를 반환한다.


### 복수 코스 저장 요청

`POST /api/courses/batch`는 추천 옵션 중 사용자가 선택한 코스를 최대 3개까지 저장한다.
모든 코스는 같은 `memberId`를 사용해야 하며, 하나라도 검증 또는 저장에 실패하면 전체가 롤백된다.

```json
{
  "courses": [
    {
      "memberId": 1,
      "resultId": 101,
      "title": "취향 집중 코스",
      "courseType": "SURVEY",
      "places": [
        {
          "placeId": 10,
          "visitDate": "2026-07-20",
          "visitOrder": 1,
          "visitTime": "10:00",
          "expectedVisitMinutes": 90,
          "distanceFromPreviousKm": 0.0,
          "travelTimeFromPreviousMinutes": 0.0
        }
      ]
    },
    {
      "memberId": 1,
      "resultId": 101,
      "title": "이동 최소 코스",
      "courseType": "SURVEY",
      "places": [
        {
          "placeId": 11,
          "visitDate": "2026-07-20",
          "visitOrder": 1,
          "visitTime": "10:00",
          "expectedVisitMinutes": 90,
          "distanceFromPreviousKm": 0.0,
          "travelTimeFromPreviousMinutes": 0.0
        }
      ]
    }
  ]
}
```

성공 응답은 저장된 코스별 ID와 합계를 반환한다.

```json
{
  "savedCount": 2,
  "savedCourses": [
    {
      "courseId": 20,
      "title": "취향 집중 코스",
      "placeCount": 8,
      "dayCount": 2
    },
    {
      "courseId": 21,
      "title": "이동 최소 코스",
      "placeCount": 8,
      "dayCount": 2
    }
  ]
}
```

### 동일 장소 쌍 거리 캐시

- 거리와 이동시간은 방향을 구분해 `A→B`, `B→A`를 별도 저장한다.
- 장소 ID가 같아도 좌표가 변경되면 새 키로 계산한다.
- OpenRouteService 결과와 Haversine 대체 결과 모두 캐시한다.
- 기본 최대 크기는 20,000쌍, 기본 TTL은 1,440분(24시간)이다.
- 환경변수 `COURSE_DISTANCE_CACHE_MAX_ENTRIES`, `COURSE_DISTANCE_CACHE_TTL_MINUTES`로 조정할 수 있다.

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

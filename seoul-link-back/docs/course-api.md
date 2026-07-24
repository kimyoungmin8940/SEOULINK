# 코스 추천·조회 API 계약

이 문서는 프론트 추천 결과 화면과 상세 화면이 사용할 코스 API 계약을 고정한다.
추천 결과(`POST /api/courses/recommend`)는 `courseOptions[].days[].places[]`,
저장 상세(`GET /api/courses/{courseId}`)는 `days[].places[]` 구조를 사용한다. 최적화만 확인하는 개발용 API의
`optimizedPlaces` 구조는 그대로 유지한다.

## 공통 규칙

- 날짜 형식: `YYYY-MM-DD`
- 거리 단위: km
- 시간 단위: 분
- 이동수단 값: `WALKING`, `PUBLIC_TRANSIT`, `DRIVING`
- 대중교통 구간 종류: `SUBWAY`, `BUS`, `BUS_SUBWAY`
- `days`는 `dayNo` 오름차순, `places`는 `visitOrder` 오름차순이다.
- 코스 초안의 기본 시작 시각은 일정 유형에 따라 P형 `11:00`, R형 `13:00`으로 정한다.
- 장소 선발과 기본 최적화가 끝나면 최소 동선보다 `15분` 또는 `20%`를 초과하지 않는
  순서 안에서 관광지 시작, 첫 음식점의 점심시간 배치, 음식점·카페 연속 방지,
  오후 카페 배치를 우선한다.
- 도보 추천은 추정 경로를 사용하지 않고 모든 인접 구간의 실제 도보시간을 `20분 이하`로
  강제한다. 먼저 총 도보시간이 `(장소 수 - 1) × 15분` 이하인 경로를 찾고, 없으면
  `(장소 수 - 1) × 18분` 이하까지 완화한다. 두 조건을 모두 만족하지 못하면 장소 수를
  줄여 다시 탐색하며, 구간당 `20분` 제한은 어떤 경우에도 완화하지 않는다.
- 기본 경로는 날짜별 첫 장소를 기준으로 최근접 이웃과 2-opt를 적용하며,
  총 이동시간을 우선하고 동률이면 총 거리를 줄인다.
- 날짜별 첫 장소의 `distanceFromPreviousKm`와
  `travelTimeFromPreviousMinutes`는 `0`이다.
- `recommendationScore`는 추천 직후 응답에서는 채워진다. 저장 상세 조회에서는
  추천 점수 저장 또는 장소 추천 데이터 연동 전까지 `null`일 수 있다.
- 장소 원점수는 취향 코드·평점·리뷰·동행 유형 가중치로 순위를 정하고,
  화면 표시 점수만 순서를 유지한 채 `70~95` 범위로 정규화한다.
- 재추천에서는 직전 결과의 장소를 `-20`, 보지 않은 후보를 `+5`로 조정한 뒤
  표시 점수를 다시 `70~95`로 정규화한다.
- 2일 이상 일정은 일반 장소 최적화가 끝난 뒤 마지막 여행일을 제외한 DAY 끝에
  같은 HOTEL을 붙인다. 숙소 구간은 추천 중 추정값으로 계산하고 현재 DAY 경로
  조회 시 실제값으로 보정한다.
- 저장 상세 조회의 장소명·카테고리·주소·이미지·좌표·테마는
  `COURSE_DETAILS.PLACE_ID`로 `PLACES`를 조회해 채운다.
- 도보·자동차는 OpenRouteService의 `foot-walking`·`driving-car` 프로필을 사용한다.
  API 키는 백엔드의 `OPENROUTESERVICE_API_KEY` 환경변수에서 읽는다.
  API 키가 없거나 호출에 실패한 구간은 추정값으로 표시되지만, 도보 추천 선발에서는
  추정 구간을 제외한다. 따라서 실제 경로만으로 제한을 만족하지 못하면 장소 수를 줄이고,
  최소 장소 수도 구성할 수 없으면 도보 추천을 실패시킨다.
- 대중교통은 ODsay `searchPubTransPathT`의 `totalDistance`와 `totalTime`을 사용한다.
  선택된 최단시간 경로의 `pathType`은 `transitPathType`으로 변환한다
  (`1→SUBWAY`, `2→BUS`, `3→BUS_SUBWAY`).
  ODsay Server 키는 `ODSAY_API_KEY` 환경변수에서 읽으며, 등록된 공인 IP에서
  백엔드가 호출해야 한다.
- ODsay 키가 없거나 호출·경로 검색에 실패한 구간만 별도 임시 계산을 사용한다.
  최종 방문 경로에 이런 구간이 실제 포함된 경우에만 해당 옵션과 최상위 응답의
  `estimatedTravelTimes`가 `true`이다.
- ODsay의 `-98`(출발지·도착지 700m 이내) 구간은 도보 이동 추정값으로 처리한다.
  이처럼 실제 경로가 없는 추정 구간의 `transitPathType`은 `null`이다.
- 도보 후보 풀은 구간당 20분 상한을 실제 경로로 강제하기 위해 ORS 실제 행렬을
  사용하며, 이미 조회한 장소 쌍은 캐시한다. 자동차·대중교통 후보 풀의 1차 선별은
  외부 API를 호출하지 않는 추정행렬을 사용하고, 추천 화면에 표시 중인 DAY의 최종
  인접 구간만 `POST /api/courses/route-details`로 실제 조회한다.

## 1. 추천 후보 최적화

`POST /api/courses/optimize`

DB에 저장하지 않고 최근접 이웃과 2-opt로 방문 순서와 이동값을 계산한다.

### 요청

```json
{
  "resultId": 101,
  "travelCode": "ATLSR",
  "transportMode": "WALKING",
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
  "transportMode": "WALKING",
  "estimatedTravelTimes": false,
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
      "travelTimeFromPreviousMinutes": 0.0,
      "transitPathType": null
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
      "travelTimeFromPreviousMinutes": 3.562549510655063,
      "transitPathType": null
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

날짜별 후보 풀의 `categoryTargets` 비율을 `targetPlaceCount`에 맞춰 축소한 조합을 만들고,
취향 우선·이동 최소·균형 추천 세 가지 코스를 반환한다. 이 단계에서는 DB에 저장하지 않는다.
사용자가 세 코스 중 하나만 선택하면 `POST /api/courses`, 두 개 이상 선택하면 `POST /api/courses/batch`로 저장한다.

### 요청 필드

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `surveyId` | number | N | 원본 설문 ID. 1번 담당자가 전달하는 추적용 메타데이터 |
| `resultId` | number | Y | 설문 결과 ID |
| `travelCode` | string | N | 5자리 여행 유형 코드. 전달되면 응답에 그대로 포함 |
| `companionType` | string | N | `SOLO`, `COUPLE`, `FRIENDS`, `FAMILY`. 장소 후보 동행 가중치 기준 |
| `transportMode` | string | Y | 여행 전체 이동수단. `WALKING`, `PUBLIC_TRANSIT`, `DRIVING` 중 하나 |
| `startDate` | string | N | 전체 여행 시작일 메타데이터, `YYYY-MM-DD` |
| `endDate` | string | N | 전체 여행 종료일 메타데이터, `YYYY-MM-DD` |
| `travelDays` | number | N | 전체 여행 일수 메타데이터 |
| `dailyStartTime` | string | Y | 매일 일정 시작 시각, `HH:mm` |
| `excludedRecommendationKeys` | string[] | N | 이전에 본 코스 조합 제외 키 |
| `previouslyRecommendedPlaceIds` | number[] | N | 재추천 시 이전 장소 감점에 사용할 ID |
| `hotelCandidates` | array | N | 2일 이상 일정에서 마지막 날 전까지 같은 숙소를 붙일 HOTEL 후보 풀 |
| `dailyPlans` | array | Y | 날짜별 후보 풀과 선발 목표 |
| `dailyPlans[].visitDate` | string | Y | 방문 날짜 |
| `dailyPlans[].targetPlaceCount` | number | Y | 해당 날짜에 최종 선발할 장소 수 |
| `dailyPlans[].categoryTargets` | object | Y | TOUR·RESTAURANT·CAFE·HOTEL별 후보 풀 개수·비율 |
| `dailyPlans[].placeCandidates` | array | Y | 최종 선발 전 후보 풀 |
| `placeCandidates[].alternativeCandidates` | array | N | 해당 장소가 먼 구간일 때만 사용하는 전용 대체 후보 |

`categoryTargets` 값의 합계는 `targetPlaceCount` 이상이어야 하며, 후보 풀에는 각
카테고리 목표를 충족할 수 있는 충분한 장소가 있어야 한다. 합계가 더 크면 후보 비율을
`targetPlaceCount`에 맞춰 최대 나머지 방식으로 축소한다. 예: `7/4/4` 후보에서 6곳을
뽑을 때 최종 목표는 `TOUR 3 / RESTAURANT 2 / CAFE 1`이다.
전체 2일 요청 예시는 `docs/course-recommend-request-example.json`에서 바로 사용할 수 있다.

### 성공 응답 `200 OK`

```json
{
  "resultId": 101,
  "travelCode": "ATLSR",
  "transportMode": "PUBLIC_TRANSIT",
  "estimatedTravelTimes": true,
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
      "estimatedTravelTimes": true,
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
- `MIN_DISTANCE`: 선택한 이동수단의 예상 이동시간과 거리가 짧은 조합을 우선한다.
- `BALANCED`: 추천 점수 50%, 이동시간 30%, 거리 20%를 정규화해 균형을 맞춘다.
- 가능한 조합이 3개 이상이면 서로 다른 장소 조합을 반환한다. 후보 풀이 부족하면
  일부 옵션의 장소 구성이 겹칠 수 있다.

## 3. 저장 코스 상세 조회

`GET /api/courses/{courseId}`

성공 시 `200 OK`이며 추천 생성 응답과 동일한 `days[].places[]` 구조를 사용한다.
추가로 `coverImageUrl`, `viewCount`, `createdAt`, `updatedAt`을 반환한다.
대중교통 코스는 저장 요청에 `transitPathType`을 함께 보내며, 상세 조회에서도 동일한
값을 반환하므로 새로고침 후에도 구간별 지하철·버스·혼합 표기가 유지된다.
코스 전체 `transportMode`는 `TRAVEL_COURSES.RESULT_ID`로 `SURVEY_RESULT`와
`TRAVEL_SURVEY.TRANSPORT_TYPE`을 조회한 뒤 API enum으로 변환한다.

```json
{
  "courseId": 20,
  "title": "서울 추천 코스",
  "description": "서울 도심의 대표 관광지를 걷는 코스",
  "coverImageUrl": "https://example.com/place/1.jpg",
  "travelCode": "ATLSR",
  "transportMode": "PUBLIC_TRANSIT",
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
          "placeName": "서울시청",
          "category": "TOUR",
          "address": "서울특별시 중구 세종대로 110",
          "roadAddress": null,
          "imageUrl": "https://example.com/place/1.jpg",
          "latitude": 37.5665,
          "longitude": 126.978,
          "recommendationScore": null,
          "visitOrder": 1,
          "memo": null,
          "visitTime": null,
          "expectedVisitMinutes": 90,
          "distanceFromPreviousKm": 0.0,
          "travelTimeFromPreviousMinutes": 0.0,
          "transitPathType": null
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
| `GET` | `/api/courses/my?memberId={id}` | `200` | 회원의 전체 코스 카드 목록 |

기존 `/api/members/me/courses?memberId={id}` 경로도 같은 목록을 반환하는 호환 API로 유지한다.

목록 항목은 `courseId`, `title`, `description`, `coverImageUrl`, `transportMode`, `regions`,
`tags`, `placeCount`, `dayCount`, 네 가지 합계값, `liked`를 반환한다.


### 복수 코스 저장 요청

`POST /api/courses/batch`는 추천 옵션 중 사용자가 선택한 코스를 최대 3개까지 저장한다.
모든 코스는 같은 `memberId`와 `transportMode`를 사용해야 하며, 하나라도 검증 또는 저장에 실패하면 전체가 롤백된다.

```json
{
  "courses": [
    {
      "memberId": 1,
      "resultId": 101,
      "transportMode": "PUBLIC_TRANSIT",
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
      "transportMode": "PUBLIC_TRANSIT",
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
      "transportMode": "PUBLIC_TRANSIT",
      "placeCount": 8,
      "dayCount": 2
    },
    {
      "courseId": 21,
      "title": "이동 최소 코스",
      "transportMode": "PUBLIC_TRANSIT",
      "placeCount": 8,
      "dayCount": 2
    }
  ]
}
```

### 동일 장소 쌍 거리 캐시

- 거리와 이동시간은 방향을 구분해 `A→B`, `B→A`를 별도 저장한다.
- 같은 장소 쌍도 이동수단을 포함해 `WALKING:A:B`, `PUBLIC_TRANSIT:A:B`,
  `DRIVING:A:B`로 분리한다.
- 장소 ID가 같아도 좌표가 변경되면 새 키로 계산한다.
- OpenRouteService·ODsay에서 성공한 실제 결과만 캐시한다. 실패 시 대체한 추정값은
  외부 API가 복구된 뒤 다시 확인할 수 있도록 장기 캐시에 저장하지 않는다.
- ODsay는 행렬 API가 아니므로 캐시에 없는 방향별 장소 쌍마다 한 번씩 호출한다.
  추천 후보 조합에서는 호출하지 않고 현재 표시 중인 DAY의 인접 구간만 조회한다.
- OpenRouteService Matrix는 카드의 한 DAY를 한 요청으로 조회하며, 기본 서버 예산은
  하루 450회이다. `OPENROUTESERVICE_DAILY_CALL_BUDGET`으로 조정할 수 있다.
- ODsay 기본 서버 예산은 하루 900회이며 `ODSAY_DAILY_CALL_BUDGET`으로 조정할 수 있다.
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
| `400` | `INVALID_REQUEST` | 필수값 누락, 허용되지 않은 이동수단, 잘못된 좌표·순서·ID, 중복 장소 | 입력 확인 메시지 표시 |
| `404` | `COURSE_NOT_FOUND` | 존재하지 않는 `courseId` 조회 | 결과 없음 화면 또는 목록으로 이동 |
| `500` | `COURSE_PROCESSING_FAILED` | 최적화 내부 상태 오류 또는 저장 처리 실패 | 재시도 안내, 내부 상세 원인은 노출하지 않음 |

## 담당자 간 전달 필드

### 1번 담당자에게 받을 값

| 필드 | 필수 시점 | 비고 |
|---|---|---|
| `resultId` | 추천 저장 전 | 설문 결과 DB ID |
| `travelCode` | 추천 요청 전 | 영문 5자리 최종 여행 유형 코드 |
| `transportMode` | 추천 요청 전 | `TRAVEL_SURVEY.TRANSPORT_TYPE`의 PUBLIC/WALKING/CAR를 `PUBLIC_TRANSIT`/`WALKING`/`DRIVING`으로 변환, null 불가 |
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

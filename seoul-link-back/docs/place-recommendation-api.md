# 장소 추천 후보 API (2번 담당 -> 1번 담당)

## 역할 경계

- 2번 담당: 장소 자동 태깅, 여행 코드별 점수 계산, 카테고리별 후보와 대체 후보 반환
- 1번 담당: 여행 일수와 P/R에 따른 목표 장소 수 결정, 날짜별 후보 분배, 중복 제거
- 3번 담당: 날짜별 최종 장소 선택, 코스 3개 생성, 거리·시간·순서 최적화

2번 응답에는 `visitDate`, `targetPlaceCount`, `categoryTargets`를 넣지 않는다.

## 추천 후보 조회

```http
GET /api/places/recommend
    ?travelCode=ATBSP
    &region=성동구
    &limitPerCategory=5
    &alternativeLimit=3
```

### 파라미터

| 이름 | 필수 | 설명                                              |
|---|---:|-------------------------------------------------|
| `travelCode` | Y | `[AH][TM][LB][SD][PR]` 규칙의 5자리 코드               |
| `region` | N | `성동구`, `종로구` 등. 서울 전체이면 생략                      |
| `limitPerCategory` | N | TOUR·RESTAURANT·CAFE·HOTEL별 후보 수, 최대 50         |
| `alternativeLimit` | N | 대표 후보 하나당 대체 후보 수, 기본 3                         |
| `limit` | N | 기존 호환용 전체 후보 수. `limitPerCategory`가 있으면 사용하지 않음 |

2일 일정에 날짜별 후보 7~10개가 필요하면 `limitPerCategory=4~5`부터 시작한다.
실제 반환 수는 해당 지역·카테고리에서 취향 태그가 일치하는 장소 수에 따라 적을 수 있다.

## 응답

```json
{
  "travelCode": "ATBSP",
  "recommendedPlaces": [
    {
      "placeId": 10,
      "placeName": "경복궁",
      "category": "TOUR",
      "recommendationScore": 92.0,
      "latitude": 37.5796,
      "longitude": 126.9770,
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
          "placeId": 20,
          "placeName": "창덕궁",
          "category": "TOUR",
          "recommendationScore": 88.0,
          "latitude": 37.5794,
          "longitude": 126.9910,
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

대체 후보는 원본과 `category`가 같고 전체 응답에서 중복되지 않는다.

## 1번 담당 처리 규칙

같은 Spring Boot 프로젝트 안에서는 HTTP로 자기 서버를 다시 호출하지 않고
`PlaceRecommendationService`를 주입해 직접 호출한다.

```java
private final PlaceRecommendationService placeRecommendationService;

PlaceRecommendationListResponse candidates = placeRecommendationService.recommend(
        travelCode,
        survey.getRegion(),
        null,
        5,
        3
);
```

1번 브랜치의 기존 `com.seoulink.backend.entity.Place`와 자체
`recommendPlaceEntities()`는 네임스페이스가 다른 이전 구현이다. 통합할 때는
`com.seoulink.backend.domain.place.*` 구조로 통일하고 기존 추천 계산을 중복 실행하지 않는다.

1. 설문 저장 후 `resultId`, `travelCode`, 여행 시작일·종료일을 확보한다.
2. 위 API를 호출해 충분한 `recommendedPlaces`를 받는다.
3. P형은 하루 6~7곳, R형은 하루 4~5곳 등 팀 합의값으로 `targetPlaceCount`를 정한다.
4. `categoryTargets`를 정하고 날짜마다 목표 수보다 넉넉한 7~10개 후보를 배분한다.
5. 기본 후보 `placeId`가 날짜 간 중복되지 않게 한다.
6. 추천 점수·좌표·8개 테마·대체 후보는 수정하지 않고 그대로 3번 요청에 포함한다.

날씨 기반 추천은 1차 범위에서 제외한다. `weatherStatus`, `temperature`,
`rainProbability`는 요청에서 생략해도 3번 서비스가 `null`로 처리한다.

## 장소 저장과 자동 태깅

```http
POST /api/places
```

장소가 `PlaceService.createPlace()`를 통과하면 다음 순서로 처리된다.

```text
API_PROVIDER + API_PLACE_ID 중복 조회
-> 장소 기본 정보 갱신
-> 자동 태깅
-> 요청에 명시된 Y/N 태그 보정
-> PLACES 저장
```

SQL로 직접 INSERT하면 Java 자동 태깅은 실행되지 않는다. 지도 API 수집 코드는
Repository에 직접 저장하지 말고 `PlaceService.createPlace()`를 호출해야 한다.

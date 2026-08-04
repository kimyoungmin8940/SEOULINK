# SeoulLink 데이터베이스 통합본

`seoulink-database-fixed(8).zip`을 기준으로 유지하면서, 새 관광지·식당·카페·호텔 장소 데이터만 추가한 버전입니다. 기존 장소 행은 덮어쓰지 않고 그대로 보존했습니다.

## 처음부터 다시 설치

DBeaver에서 압축을 완전히 푼 뒤 아래 순서로 각각 **Alt+X** 실행합니다.

1. `reset/01_drop_tables.sql`
2. `install_all.sql`
3. `verify_all.sql`

## 기존 DB 데이터를 유지하고 변경만 적용

아래 세 파일을 순서대로 **Alt+X** 실행합니다.

1. `upgrade_existing.sql`
2. `apply_place_data.sql`
3. `verify_all.sql`

## 이번에 추가된 변경

- `TRAVEL_COURSES.SOURCE_COURSE_KEY VARCHAR2(50)` 추가
- `COURSE_TYPE`에 `THEME` 허용
- 회원별 동일 기본 테마 코스 중복 저장 방지
  - `THEME 코스에만 적용되는 백엔드 서비스 중복 검사`
  - SURVEY/CUSTOM/CHATBOT 코스는 인덱스 대상에서 제외되어 추천 이력 저장에 영향 없음
- 기존 DB용 `migrations/010_add_theme_course_source_key.sql` 추가
- 검증용 `verify/09_verify_theme_course.sql` 추가

## 보존한 기존 구조

- `TRAVEL_COURSES.IS_SAVED`
- 현재 `COURSE_DETAILS` 실제 경로 및 예상 경로 컬럼
- 현재 설문·소셜 로그인·챗봇·후기·결제 구조
- 기존 장소와 데모 데이터

## 이번 장소 데이터 통합

- 관광지: 230개
- 식당: 180개
- 카페: 150개
- 호텔: 140개
- 전체 장소: 700개
- 동일한 `(API_PROVIDER, API_PLACE_ID)` 장소는 `seoulink-database-fixed(8).zip` 값을 유지
- 신규 키를 가진 장소만 뒤에 추가
- 기존 DB에는 `apply_place_data.sql`을 실행하면 기준본 값으로 기존 장소를 동기화하고 신규 장소를 추가

## DBeaver 실행 주의사항

이 파일들은 DBeaver의 **Alt+X 스크립트 실행** 기준입니다. SQL*Plus 전용 단독 `/` 구분자는 사용하지 않습니다.

## 2026-08-04 대표 이미지 보완 통합

- 기준본: `seoulink-database-fixed(10).zip`
- 반영 파일: `TOUR_관광지 (3).sql`, `RESTAURANT_식당 (3).sql`, `CAFE_카페 (3).sql`, `HOTEL_호텔 (3).sql`
- 장소 추가·삭제 없음: 전체 688개 유지
- 대표 이미지 정보 보완: 총 73개
  - 관광지 20개 / 식당 23개 / 카페 28개 / 호텔 2개
  - 기존 이미지가 없던 장소 51개에 이미지 추가
  - 기존 이미지가 있던 장소 22개는 전달받은 대표 이미지 정보로 교체
- 장소명, 좌표, 추천 태그, 테마, 체류시간 등 이미지 외 데이터와 DB 구조는 기준본 값을 그대로 유지

## 2026-08-04 추가 장소·이미지 재통합

- 기준본: `seoulink-database-fixed(13).zip`
- 반영 파일: `01_tour.sql`, `02_restaurant.sql`, `03_cafe.sql`, `04_hotel.sql`
- 기준본의 DB 구조와 기존 장소의 이미지 외 값을 그대로 유지
- 신규 장소 12개 추가
  - 관광지 1개 / 식당 10개 / 카페 0개 / 호텔 1개
- 기존 장소 76개의 이미지 관련 정보만 갱신
  - 관광지 21개 / 식당 23개 / 카페 30개 / 호텔 2개
- 최종 장소 수: 700개
  - 관광지 230 / 식당 180 / 카페 150 / 호텔 140
- 삭제된 장소 없음
- 전체 `(API_PROVIDER, API_PLACE_ID)` 및 동일 카테고리 장소명 중복 없음


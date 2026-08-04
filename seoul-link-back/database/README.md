# SeoulLink 데이터베이스

2026-08-04 기준의 Oracle XE용 통합 스키마와 시드 데이터입니다. DBeaver의 **Alt+X(스크립트 실행)** 기준으로 작성되어 있으며, SQL*Plus 전용 단독 `/` 구분자는 사용하지 않습니다.

## 처음부터 다시 설치

압축 또는 저장소를 완전히 받은 뒤 `seoul-link-back/database` 폴더에서 다음 파일을 각각 Alt+X로 실행합니다.

1. `reset/01_drop_tables.sql`
2. `install_all.sql`
3. `verify_all.sql`

`reset/01_drop_tables.sql`은 기존 18개 SeoulLink 테이블과 데이터를 삭제하므로 필요한 데이터가 있다면 먼저 백업하세요.

기존 단일 파일 경로와의 호환이 필요하면 `seoulink.sql`을 Alt+X로 실행할 수 있습니다. 이 파일은 위 세 단계를 순서대로 포함합니다.

## 기존 DB 데이터를 유지하고 변경만 적용

다음 파일을 순서대로 각각 Alt+X로 실행합니다.

1. `upgrade_existing.sql`
2. `apply_place_data.sql`
3. `verify_all.sql`

- `upgrade_existing.sql`: 설문·소셜 로그인·챗봇·후기·결제·테마 코스 구조 반영
- `apply_place_data.sql`: 기존 장소 동기화와 신규 장소·대표 이미지 반영
- `verify_all.sql`: 테이블, 외래키, 중복 장소와 최신 컬럼·제약조건 검증

## 폴더 구성

```text
database/
├─ reset/                 # 개발용 전체 테이블 삭제
├─ schema/                # 18개 테이블, 인덱스, 주석
├─ seed/                  # 테스트 회원, 여행 유형, 설문, 장소, 데모 후기
│  └─ places/             # 관광지·식당·카페·호텔 데이터
├─ migrations/            # 기존 DB에 적용할 순차 마이그레이션
├─ verify/                # 항목별 검증 쿼리
├─ install_all.sql        # 신규 설치
├─ upgrade_existing.sql   # 기존 구조 업그레이드
├─ apply_place_data.sql   # 최신 장소 데이터 적용
├─ verify_all.sql         # 전체 검증
└─ seoulink.sql           # 전체 재설치 호환 진입 파일
```

## 현재 구조

| 도메인 | 테이블 |
| --- | --- |
| 회원 | `MEMBER` |
| 장소 | `PLACES` |
| 취향 검사 | `TRAVEL_TYPE_MASTER`, `SURVEY_QUESTION`, `SURVEY_OPTION`, `TRAVEL_SURVEY`, `SURVEY_ANSWER`, `TRAVEL_TYPE_PLACE`, `SURVEY_RESULT` |
| 결제 | `PAYMENT` |
| 코스 | `TRAVEL_COURSES`, `COURSE_DETAILS` |
| 챗봇 | `CHATBOT_HISTORY` |
| 후기 | `REVIEW`, `REVIEW_IMAGE`, `REVIEW_TAG`, `REVIEW_LIKE`, `REVIEW_COMMENT` |

총 18개 테이블입니다.

### 주요 최신 항목

- `TRAVEL_COURSES.IS_SAVED` 유지
- `TRAVEL_COURSES.SOURCE_COURSE_KEY VARCHAR2(50)` 반영
- `COURSE_TYPE`에 `THEME` 허용
- `COURSE_DETAILS`의 실제 경로 유형과 예상값 컬럼 반영
- 설문 여행 조건, 회원 소셜 로그인, 챗봇 대화방, 후기 동행 유형, 결제 무결성 구조 반영
- `UK_MEMBER_SOURCE_COURSE` 제약조건 및 `UX_MEMBER_THEME_SOURCE_COURSE` 인덱스 미사용
- 테마 코스 중복 저장은 백엔드 서비스에서 검사

## 장소 데이터

| 카테고리 | 건수 |
| --- | ---: |
| 관광지 | 229 |
| 식당 | 170 |
| 카페 | 150 |
| 호텔 | 139 |
| 합계 | 688 |

- `(API_PROVIDER, API_PLACE_ID)` 기준 중복 없음
- 2026-08-03 신규 장소 110개 통합
- 2026-08-04 장소 수 변경 없이 73개 장소의 대표 이미지 정보 보완
- 이미지 외 장소명, 좌표, 추천 태그, 테마와 체류시간 데이터 유지

## 검증 기준

`verify_all.sql` 실행 후 다음을 확인합니다.

- 대상 테이블 18개 조회
- 장소 중복 조회 결과 0행
- 외래키 상태 정상
- `TRAVEL_SURVEY.PEOPLE_COUNT` 제거 확인
- `SOURCE_COURSE_KEY` 존재 및 `COURSE_TYPE`의 `THEME` 허용 확인
- `UK_MEMBER_SOURCE_COURSE`, `UX_MEMBER_THEME_SOURCE_COURSE` 조회 결과 0행

세부 통합 이력은 `CHANGED_FILES.txt`, `PLACE_DATA_ADDED_20260803.txt`, `IMAGE_DATA_UPDATED_20260804.txt`, `VALIDATION.txt`를 참고하세요.

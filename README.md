# SEOULINK

서울 여행자의 취향과 여행 조건을 바탕으로 맞춤 장소와 여행 코스를 추천하고, 지도 기반 코스 편집·후기·결제·AI 여행 챗봇을 제공하는 웹 서비스입니다.

## 주요 기능

- 10문항 취향 검사와 5자리 여행 유형 분석
- 취향·여행 기간·이동수단을 반영한 추천 코스 3개 생성
- 도보·자동차·대중교통의 실제 경로, 이동 시간 및 거리 조회
- 지도에서 직접 장소를 선택하는 커스텀 코스 만들기
- 추천 코스 저장, 내 코스 관리, 장소 후기·댓글·좋아요
- Google·Kakao·Naver 소셜 로그인
- Toss Payments 이용권 결제와 OpenAI 기반 여행 챗봇

## 기술 스택

| 구분 | 기술 |
| --- | --- |
| Frontend | React 19, Vite 8, React Router, Axios |
| Backend | Java 17, Spring Boot 4.1, Spring Data JPA, Spring Security, OAuth2 |
| Database | Oracle XE |
| External API | Kakao Maps/Local, OpenRouteService, ODsay, Toss Payments, OpenAI |

## 프로젝트 구조

```text
SEOULINK/
├─ seoul-link/                 # React + Vite 프론트엔드
├─ seoul-link-back/            # Spring Boot 백엔드
│  └─ database/                # 최신 Oracle 스키마·시드·마이그레이션·검증 SQL
└─ README.md
```

## 실행 전 준비

- Git
- JDK 17
- Node.js `20.19 이상` 또는 `22.12 이상`
- Oracle Database XE
- DBeaver

현재 통합본을 직접 받을 때는 다음 브랜치를 사용합니다.

```powershell
git clone https://github.com/kimyoungmin8940/SEOULINK.git
cd SEOULINK
git switch final-integration-20260731
```

## 1. Oracle 계정과 DB 준비

백엔드의 기본 연결값은 다음과 같습니다.

| 항목 | 기본값 |
| --- | --- |
| Host | `localhost` |
| Port | `1521` |
| SID | `xe` |
| Username | `seoulink` |
| Password | `12345` |

`seoulink` 사용자가 없다면 SYSTEM 계정으로 아래 SQL을 한 번 실행합니다.

```sql
CREATE USER seoulink IDENTIFIED BY 12345;
GRANT CREATE SESSION, RESOURCE TO seoulink;
ALTER USER seoulink QUOTA UNLIMITED ON USERS;
```

그다음 DBeaver에서 `seoulink` 계정으로 접속합니다. DB 파일은 반드시 압축이 완전히 풀린 상태여야 하며, 개별 구문 실행이 아닌 **Alt+X(스크립트 실행)** 를 사용합니다.

### 처음 설치하거나 DB를 완전히 재설치할 때

`seoul-link-back/database` 폴더에서 다음 파일을 순서대로 각각 Alt+X로 실행합니다.

1. `reset/01_drop_tables.sql`
2. `install_all.sql`
3. `verify_all.sql`

> `reset/01_drop_tables.sql`은 기존 SeoulLink 테이블과 데이터를 삭제합니다. 필요한 데이터가 있다면 먼저 백업하세요.

기존 경로와의 호환을 위해 `seoul-link-back/database/seoulink.sql`도 위 세 단계를 한 번에 수행하지만, 오류 위치를 쉽게 확인하려면 세 파일을 각각 실행하는 방식을 권장합니다.

### 기존 DB 데이터를 유지하면서 최신 구조와 장소만 반영할 때

다음 파일을 순서대로 각각 Alt+X로 실행합니다.

1. `upgrade_existing.sql`
2. `apply_place_data.sql`
3. `verify_all.sql`

## 2. 백엔드 환경 변수 설정

Spring Boot는 `.env` 파일을 자동으로 읽지 않습니다. PowerShell에서 백엔드를 실행할 **같은 창**에 환경 변수를 설정하거나, IntelliJ Run Configuration의 Environment variables에 등록합니다.

```powershell
# Oracle: 아래 값은 application.properties의 기본값과 같아서 다른 환경일 때만 변경
$env:DB_URL = "jdbc:oracle:thin:@localhost:1521:xe"
$env:DB_USERNAME = "seoulink"
$env:DB_PASSWORD = "12345"

# 현재 oauth 프로필에서 사용하는 소셜 로그인 키
$env:GOOGLE_CLIENT_ID = "your-google-client-id"
$env:GOOGLE_CLIENT_SECRET = "your-google-client-secret"
$env:KAKAO_CLIENT_ID = "your-kakao-client-id"
$env:KAKAO_CLIENT_SECRET = "your-kakao-client-secret"
$env:NAVER_CLIENT_ID = "your-naver-client-id"
$env:NAVER_CLIENT_SECRET = "your-naver-client-secret"

# 기능별 외부 API 키
$env:OPENAI_API_KEY = "your-openai-api-key"
$env:TOSS_SECRET_KEY = "your-toss-secret-key"
$env:KAKAO_REST_API_KEY = "your-kakao-rest-api-key"
$env:OPENROUTESERVICE_API_KEY = "your-openrouteservice-api-key"
$env:ODSAY_API_KEY = "your-odsay-server-api-key"
```

외부 API 키를 저장소에 직접 작성하거나 커밋하지 마세요. 키가 없으면 해당 기능이 제한되며, OpenRouteService·ODsay 경로 조회에 실패한 구간은 화면에 예상값으로 표시될 수 있습니다.

ODsay Server API 키는 등록된 공인 IP에서만 호출되도록 설정될 수 있습니다. 다른 컴퓨터나 네트워크에서 실행한다면 ODsay 콘솔의 허용 IP도 확인해야 합니다.

소셜 로그인 콘솔에는 다음 로컬 Redirect URI를 등록합니다.

```text
http://localhost:8080/login/oauth2/code/google
http://localhost:8080/login/oauth2/code/kakao
http://localhost:8080/login/oauth2/code/naver
```

## 3. 백엔드 실행

저장소 루트에서 새 PowerShell 창을 열고 실행합니다. 환경 변수를 PowerShell에서 설정했다면 같은 창을 사용하세요.

```powershell
cd seoul-link-back
.\gradlew.bat bootRun
```

정상 실행 주소는 `http://localhost:8080`입니다.

## 4. 프론트엔드 환경 변수와 실행

`seoul-link/.env.local` 파일을 만들고 필요한 공개 클라이언트 키를 설정합니다.

```dotenv
VITE_KAKAO_MAP_KEY=your-kakao-javascript-key
VITE_TOSS_CLIENT_KEY=your-toss-client-key

# 기본 프록시를 사용할 때는 생략 가능
VITE_API_BASE_URL=/api
VITE_BACKEND_ORIGIN=http://localhost:8080
```

새 PowerShell 창에서 다음을 실행합니다.

```powershell
cd seoul-link
npm ci
npm run dev
```

브라우저에서 `http://localhost:5173`으로 접속합니다. 현재 백엔드 CORS와 OAuth 복귀 주소도 5173 포트를 기준으로 설정되어 있으므로 프론트 포트를 임의로 바꾸지 않는 것이 안전합니다.

## 권장 실행 순서

1. Oracle XE 실행
2. DBeaver에서 DB 설치 또는 업데이트 SQL 실행
3. 백엔드 실행 (`localhost:8080`)
4. 프론트엔드 실행 (`localhost:5173`)

## 빌드 및 테스트

```powershell
# Backend
cd seoul-link-back
.\gradlew.bat test

# Frontend
cd ..\seoul-link
npm run build
```

## 현재 DB 구조

현재 기준은 `2026-08-04` 통합본이며 총 18개 테이블을 사용합니다.

| 도메인 | 테이블 | 용도 |
| --- | --- | --- |
| 회원 | `MEMBER` | 일반·소셜 회원과 계정 상태 |
| 장소 | `PLACES` | 장소 기본 정보, 좌표, 이미지, 추천 태그와 지도 테마 |
| 취향 검사 | `TRAVEL_TYPE_MASTER`, `SURVEY_QUESTION`, `SURVEY_OPTION`, `TRAVEL_SURVEY`, `SURVEY_ANSWER`, `TRAVEL_TYPE_PLACE`, `SURVEY_RESULT` | 설문, 여행 유형, 추천 장소 매핑과 결과 |
| 결제 | `PAYMENT` | 주문, 결제 승인 상태와 이용권 만료 정보 |
| 코스 | `TRAVEL_COURSES`, `COURSE_DETAILS` | 추천·테마·커스텀·챗봇 코스와 날짜별 방문 순서·실제 경로 |
| 챗봇 | `CHATBOT_HISTORY` | 회원별 대화방과 질문·답변·코스 요약 |
| 후기 | `REVIEW`, `REVIEW_IMAGE`, `REVIEW_TAG`, `REVIEW_LIKE`, `REVIEW_COMMENT` | 후기 본문, 다중 이미지, 태그, 좋아요와 댓글 |

핵심 관계는 `MEMBER → TRAVEL_SURVEY → SURVEY_RESULT → TRAVEL_COURSES → COURSE_DETAILS → PLACES` 흐름입니다. 후기와 결제·챗봇 이력도 회원, 장소 또는 코스와 외래키로 연결됩니다.

### 장소 데이터 현황

| 카테고리 | 건수 |
| --- | ---: |
| 관광지 (`TOUR`) | 229 |
| 식당 (`RESTAURANT`) | 170 |
| 카페 (`CAFE`) | 150 |
| 호텔 (`HOTEL`) | 139 |
| 합계 | 688 |

현재 구조에는 다음 항목이 반영되어 있습니다.

- `TRAVEL_COURSES.IS_SAVED`: 추천 이력과 사용자가 저장한 내 코스 구분
- `TRAVEL_COURSES.SOURCE_COURSE_KEY`: 기본 테마 코스의 원본 식별값
- `COURSE_TYPE`: `CUSTOM`, `SURVEY`, `CHATBOT`, `THEME` 허용
- `COURSE_DETAILS.TRANSIT_PATH_TYPE`, `ROUTE_ESTIMATED`: 실제 경로 유형과 예상값 여부
- `TRAVEL_SURVEY.COMPANION_TYPE`, `TRANSPORT_TYPE`: 동행 유형과 이동수단
- `MEMBER.SOCIAL_PROVIDER`, `SOCIAL_ID`, `LOGIN_TYPE`: 소셜 로그인 식별 정보
- `CHATBOT_HISTORY.CONVERSATION_ID`: 연속된 챗봇 대화방 식별값
- 장소 688개와 2026-08-04 대표 이미지 보완 데이터

`UK_MEMBER_SOURCE_COURSE` 제약조건과 `UX_MEMBER_THEME_SOURCE_COURSE` 인덱스는 현재 구조에서 사용하지 않습니다. 테마 코스 중복 저장은 백엔드 서비스에서 검사합니다.

## 자주 발생하는 문제

- **DBeaver에서 ORA-00900 또는 PL/SQL 구문 오류**: 파일 하나를 열어 Ctrl+Enter로 나눠 실행하지 말고, 압축을 완전히 푼 뒤 Alt+X로 실행합니다.
- **DB 연결 실패**: Oracle XE 실행 여부, 1521 포트, SID `xe`, 계정 `seoulink`를 확인합니다.
- **백엔드 시작 시 OAuth 환경 변수 오류**: `GOOGLE_*`, `KAKAO_*`, `NAVER_*` 환경 변수가 백엔드를 실행한 프로세스에 전달됐는지 확인합니다.
- **지도 미표시**: `seoul-link/.env.local`의 `VITE_KAKAO_MAP_KEY`와 Kakao JavaScript 키의 허용 도메인 `http://localhost:5173`을 확인합니다.
- **실제 이동 경로가 예상값으로 표시됨**: `OPENROUTESERVICE_API_KEY`, `ODSAY_API_KEY`, ODsay 허용 IP와 일일 호출 한도를 확인합니다.
- **포트 충돌**: 백엔드는 8080, 프론트는 5173 포트를 사용합니다.

DB 세부 설치·마이그레이션 내역은 [`seoul-link-back/database/README.md`](seoul-link-back/database/README.md)를 참고하세요.

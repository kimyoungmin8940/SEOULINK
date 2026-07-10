<<<<<<< HEAD
# React + Vite

This template provides a minimal setup to get React working in Vite with HMR and some ESLint rules.

Currently, two official plugins are available:

- [@vitejs/plugin-react](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react) uses [Oxc](https://oxc.rs)
- [@vitejs/plugin-react-swc](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react-swc) uses [SWC](https://swc.rs/)

## React Compiler

The React Compiler is not enabled on this template because of its impact on dev & build performances. To add it, see [this documentation](https://react.dev/learn/react-compiler/installation).

## Expanding the ESLint configuration

If you are developing a production application, we recommend using TypeScript with type-aware lint rules enabled. Check out the [TS template](https://github.com/vitejs/vite/tree/main/packages/create-vite/template-react-ts) for information on how to integrate TypeScript and [`typescript-eslint`](https://typescript-eslint.io) in your project.
=======
# SEOULINK

사용자의 취향과 상황에 맞는 서울 여행 코스를 추천하는 여행 코스 추천 웹 서비스입니다.

메인페이지에서는 취향 검사, 추천 코스, 지도 코스 만들기, 방문 후기, 마이페이지, AI 여행 챗봇, 결제 기능 등 주요 서비스로 이동할 수 있도록 구성했습니다.

## 프로젝트 소개

SEOULINK는 서울 여행을 더 쉽고 감성적으로 즐길 수 있도록 돕는 웹 서비스입니다.

사용자는 자신의 취향에 맞는 여행 코스를 확인할 수 있고, 지도 기반으로 직접 코스를 만들거나 다른 사용자의 후기를 참고할 수 있습니다.
현재는 메인페이지 UI를 중심으로 구현되어 있으며, 이후 백엔드 데이터와 각 기능 페이지를 연결할 수 있도록 컴포넌트 구조를 분리해두었습니다.

## 주요 기능

### 메인페이지

* 서울 여행 서비스의 첫 화면 구성
* 취향 검사 시작 버튼
* 추천 코스 보기 버튼
* 카테고리 메뉴 제공
* 추천 코스 미리보기
* 무드별 서울 여행 카드
* 실제 여행자 후기 미리보기
* 하단 CTA 배너
* Footer 영역 구성

### 카테고리 메뉴

* 지도
* 궁궐 · 문화
* 자연 · 한강
* 데이트
* 맛집 탐방
* 카페 투어
* 쇼핑 · 핫플
* 야경
* 숙소

### 추천 코스 영역

현재는 화면 확인을 위한 임시 데이터를 사용하고 있습니다.
추후 백엔드 API와 연결하면 실제 추천 코스 데이터를 받아와 표시할 수 있습니다.

### 방문 후기 영역

현재는 임시 후기 데이터를 사용하고 있습니다.
추후 후기 게시판 API와 연결하면 실제 사용자가 작성한 후기를 메인페이지에 보여줄 수 있습니다.

### 사이드 메뉴

오른쪽 메뉴 버튼을 클릭하면 사이드 메뉴가 열립니다.

* 추천 코스
* 지도 코스 만들기
* 방문 후기
* 마이페이지
* AI 여행 챗봇
* 이용권 / 결제

현재는 임시 링크로 구성되어 있으며, 추후 라우터 적용 후 각 페이지로 연결할 예정입니다.

## 기술 스택

* React
* Vite
* JavaScript
* CSS
* lucide-react
* Pretendard Font

## 프로젝트 구조

```bash
seoul-link
├─ public
│  ├─ favicon.svg
│  └─ icons.svg
├─ src
│  ├─ assets
│  │  ├─ fonts
│  │  └─ images
│  ├─ components
│  │  ├─ Header.jsx
│  │  ├─ HeroSection.jsx
│  │  ├─ CategoryMenu.jsx
│  │  ├─ RecommendSection.jsx
│  │  ├─ CourseCard.jsx
│  │  ├─ MoodSection.jsx
│  │  ├─ ReviewSection.jsx
│  │  ├─ CTASection.jsx
│  │  └─ Footer.jsx
│  ├─ pages
│  │  └─ Home.jsx
│  ├─ styles
│  │  ├─ base.css
│  │  ├─ variables.css
│  │  ├─ layout.css
│  │  ├─ header.css
│  │  ├─ hero.css
│  │  ├─ category.css
│  │  ├─ course.css
│  │  ├─ mood.css
│  │  ├─ review.css
│  │  ├─ cta.css
│  │  ├─ footer.css
│  │  └─ index.css
│  ├─ App.jsx
│  └─ main.jsx
├─ package.json
└─ vite.config.js
```

## 실행 방법

프로젝트를 실행하기 전에 `package.json`이 있는 폴더에서 명령어를 실행해야 합니다.

```bash
npm install
```

```bash
npm run dev
```

실행 후 터미널에 표시되는 로컬 주소로 접속하면 화면을 확인할 수 있습니다.

예시:

```bash
http://localhost:5173
```

## 빌드 방법

```bash
npm run build
```

## 현재 구현 상태

* 메인페이지 UI 구현 완료
* 헤더 및 사이드 메뉴 구현 완료
* Hero 영역 구현 완료
* 카테고리 메뉴 구현 완료
* 추천 코스 카드 UI 구현 완료
* 무드별 카드 UI 구현 완료
* 방문 후기 미리보기 UI 구현 완료
* CTA 배너 구현 완료
* Footer 구현 완료
* 컴포넌트별 CSS 분리 완료
* 주요 코드 주석 추가 완료

## 추후 구현 예정

* 로그인 / 회원가입 페이지 연결
* React Router 적용
* 추천 코스 목록 페이지 연결
* 지도 코스 만들기 기능 연결
* 방문 후기 게시판 연결
* 마이페이지 연결
* AI 여행 챗봇 페이지 연결
* 이용권 / 결제 페이지 연결
* 백엔드 API 연동
* 실제 추천 코스 데이터 연동
* 실제 후기 데이터 연동

## 담당 구현

메인페이지 및 공통 화면 구조를 담당했습니다.

* 전체 메인페이지 레이아웃 구성
* 공통 Header / Footer 구성
* 주요 기능으로 이동할 수 있는 메뉴 구성
* 추천 코스, 무드 카드, 후기 미리보기 UI 구성
* 추후 데이터 연동이 가능하도록 컴포넌트 단위로 분리
>>>>>>> origin/goeun

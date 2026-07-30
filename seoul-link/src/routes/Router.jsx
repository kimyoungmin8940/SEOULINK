// Router.jsx
// 지금 프로젝트는 프론트 구조를 먼저 잡는 단계라 외부 라우터 라이브러리 없이
// 현재 주소(pathname)에 맞는 페이지 컴포넌트를 보여주는 가벼운 라우터를 사용합니다.
// 나중에 react-router-dom을 설치하면 이 파일만 Routes/Route 구조로 바꾸면 됩니다.

import { useEffect } from 'react';
import Home from '../pages/home/Home';

import LoginPage from "../pages/LoginPage";
import SignupPage from "../pages/SignupPage";
import OAuthSuccessPage from "../pages/OAuthSuccessPage";
import FindPasswordPage from "../pages/auth/FindPasswordPage";

import TravelInfoPage from '../pages/survey/TravelInfoPage';
import SurveyPage from '../pages/survey/SurveyPage';
import SurveyResultPage from '../pages/survey/SurveyResultPage';

import CourseRecommendPage from '../pages/course/CourseRecommendPage';
import CourseListPage from '../pages/course/CourseListPage';
import CourseDetailPage from '../pages/course/CourseDetailPage';
import ThemeCourseAllPage from '../pages/course/ThemeCourseAllPage';
import PopularThemeCoursePage from '../pages/course/PopularThemeCoursePage';
import ThemeCourseListPage from '../pages/course/ThemeCourseListPage';

import MapCourseBuilderPage from '../pages/map/MapCourseBuilderPage';

import ReviewListPage from '../pages/review/ReviewListPage';
import ReviewDetailPage from '../pages/review/ReviewDetailPage';
import ReviewWritePage from '../pages/review/ReviewWritePage';
import ReviewEditPage from '../pages/review/ReviewEditPage';

import MyPage from '../pages/mypage/MyPage';
import MyTravelTypePage from '../pages/mypage/MyTravelTypePage';
import MyCoursesPage from '../pages/mypage/MyCoursesPage';
import MyCustomCoursesPage from '../pages/mypage/MyCustomCoursesPage';
import MyFavoritesPage from '../pages/mypage/MyFavoritesPage';
import MyReviewsPage from '../pages/mypage/MyReviewsPage';
import PaymentHistoryPage from '../pages/mypage/PaymentHistoryPage';

import ChatbotPage from '../pages/chatbot/ChatbotPage';

import PaymentPage from '../pages/payment/PaymentPage';
import PaymentSuccessPage from '../pages/payment/PaymentSuccessPage';
import PaymentFailPage from '../pages/payment/PaymentFailPage';

import NotFoundPage from '../pages/NotFoundPage';
import ServiceInfoPage from '../pages/service/ServiceInfoPage';
import { isLoggedIn } from '../utils/authGuard';


function isProtectedPath(pathname) {
    const isPublicReviewDetail =
        pathname.startsWith('/reviews/') &&
        pathname !== '/reviews/write' &&
        !pathname.endsWith('/edit');

    const isPublicCourseDetail =
        pathname.startsWith('/courses/') &&
        pathname !== '/courses/list' &&
        !pathname.startsWith('/courses/recommendations/') &&
        !pathname.startsWith('/courses/themes/');

    // 로그인 없이 접근 가능한 페이지
    if (
        pathname === '/' ||
        pathname === '/login' ||
        pathname === '/oauth-success' ||
        pathname === '/signup' ||
        pathname === '/find-password' ||
        pathname === '/travel-info' ||
        pathname === '/survey' ||
        pathname === '/survey/result' ||
        // 회원 기능 연동 전에도 추천 생성 화면은 확인할 수 있게 둡니다.
        pathname === '/courses' ||
        pathname === '/reviews' ||
        pathname === '/courses/themes' ||
        pathname === '/terms' ||
        pathname === '/privacy' ||
        pathname === '/support' ||
        pathname.startsWith('/courses/themes/') ||
        isPublicReviewDetail ||
        isPublicCourseDetail
    ) {
        return false;
    }

    // 추천 코스 생성/조회, 후기 작성/수정, 지도, 마이페이지, 챗봇, 결제는 로그인 필요
    // 취향 검사와 검사 결과 확인은 비로그인 사용자도 접근할 수 있습니다.
    return (
        pathname === '/courses/list' ||
        pathname === '/courses/recommendations' ||
        pathname.startsWith('/courses/recommendations/') ||
        pathname === '/map-course' ||
        pathname.startsWith('/mypage') ||
        pathname === '/chatbot' ||
        pathname.startsWith('/payment') ||
        pathname === '/reviews/write' ||
        (pathname.startsWith('/reviews/') && pathname.endsWith('/edit'))
    );
}

function LoginRedirect() {
    useEffect(() => {
        const returnUrl =
            window.location.pathname +
            window.location.search +
            window.location.hash;

        sessionStorage.setItem("loginReturnUrl", returnUrl);
        window.location.replace("/login");
    }, []);

    return (
        <main style={{ minHeight: "100vh", display: "grid", placeItems: "center", color: "#123160" }}>
            로그인 화면으로 이동하고 있습니다.
        </main>
    );
}

function Router() {
    const { pathname } = window.location;

    // 주소를 직접 입력해도 로그인 필요한 페이지는 막습니다.
    // 취향 검사/결과, 테마 코스, 후기 목록/상세는 로그인 없이 볼 수 있습니다.
    if (isProtectedPath(pathname) && !isLoggedIn()) {
        return <LoginRedirect />;
    }

    // 정적 경로는 객체에서 바로 찾습니다.
    const routes = {
        '/': <Home />,

        '/login': <LoginPage />,
        '/oauth-success': <OAuthSuccessPage />,
        '/signup': <SignupPage />,
        '/find-password': <FindPasswordPage />,

        '/travel-info': <TravelInfoPage />,
        '/survey': <SurveyPage />,
        '/survey/result': <SurveyResultPage />,

        '/courses': <CourseRecommendPage />,
        '/courses/list': <CourseListPage />,
        '/courses/recommendations': <CourseListPage />,
        '/courses/themes': <ThemeCourseAllPage />,
        '/courses/themes/popular': <PopularThemeCoursePage />,

        '/map-course': <MapCourseBuilderPage />,

        '/reviews': <ReviewListPage />,
        '/reviews/write': <ReviewWritePage />,

        '/mypage': <MyPage />,
        '/mypage/travel-type': <MyTravelTypePage />,
        '/mypage/courses': <MyCoursesPage />,
        '/mypage/custom-courses': <MyCustomCoursesPage />,
        '/mypage/favorites': <MyFavoritesPage />,
        '/mypage/reviews': <MyReviewsPage />,
        '/mypage/payments': <PaymentHistoryPage />,

        '/chatbot': <ChatbotPage />,

        '/payment': <PaymentPage />,
        '/payment/success': <PaymentSuccessPage />,
        '/payment/fail': <PaymentFailPage />,
        '/terms': <ServiceInfoPage type="terms" />,
        '/privacy': <ServiceInfoPage type="privacy" />,
        '/support': <ServiceInfoPage type="support" />,
    };

    if (routes[pathname]) {
        return routes[pathname];
    }

    // /courses/recommendations/1 같은 추천받은 코스 상세 페이지
    if (pathname.startsWith('/courses/recommendations/')) {
        return <CourseDetailPage />;
    }

    // /courses/themes/night-date/1101 같은 테마 코스 상세 페이지
    if (/^\/courses\/themes\/[^/]+\/[1-9]\d*\/?$/.test(pathname)) {
        return <CourseDetailPage />;
    }

    // /courses/themes/night-date 같은 테마별 추천 코스 목록 페이지
    if (/^\/courses\/themes\/[^/]+\/?$/.test(pathname)) {
        return <ThemeCourseListPage />;
    }

    // /mypage/courses/1 같은 내 코스 상세 페이지는 로그인 보호 경로로 처리합니다.
    if (pathname.startsWith('/mypage/courses/')) {
        return <CourseDetailPage />;
    }

    // /courses/1 같은 일반 코스 상세 페이지 경로 처리
    if (pathname.startsWith('/courses/')) {
        return <CourseDetailPage />;
    }

    // /reviews/1/edit 또는 /reviews/1 같은 후기 상세/수정 경로 처리
    if (pathname.startsWith('/reviews/') && pathname.endsWith('/edit')) {
        return <ReviewEditPage />;
    }

    if (pathname.startsWith('/reviews/')) {
        return <ReviewDetailPage />;
    }

    return <NotFoundPage />;
}

export default Router;

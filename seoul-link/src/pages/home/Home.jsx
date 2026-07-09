// Home 페이지는 메인페이지 전체 섹션을 순서대로 조립하는 역할만 담당
// 실제 디자인과 내용은 각 컴포넌트(Header, HeroSection 등) 안에 분리해둠
import Header from '../../components/common/Header';
import HeroSection from '../../components/home/HeroSection';
import CategoryMenu from '../../components/home/CategoryMenu';
import RecommendSection from '../../components/home/RecommendSection';
import MoodSection from '../../components/home/MoodSection';
import ReviewSection from '../../components/home/ReviewSection';
import CTASection from '../../components/home/CTASection';
import Footer from '../../components/common/Footer';

function Home() {
    return (
        <div className="home">
            {/* 상단 로고, 로그인/회원가입 버튼, 오른쪽 사이드 메뉴 */}
            <Header />

            {/* 메인 비주얼 영역: 배경 이미지, 대표 문구, 주요 버튼 */}
            <HeroSection />

            {/* main 안에는 본문에 해당하는 섹션들을 위에서 아래 순서대로 배치*/}
            <main>
                {/* 지도/궁궐/맛집/카페 등 빠른 카테고리 이동 메뉴 */}
                <CategoryMenu />

                {/* 추천 여행 코스 카드 목록 */}
                <RecommendSection />

                {/* 노을/비 오는 날/혼자 걷기 등 무드별 추천 영역 */}
                <MoodSection />

                {/* 사용자 후기 미리보기 영역 */}
                <ReviewSection />

                {/* 페이지 하단 취향 검사 유도 배너 */}
                <CTASection />
            </main>

            {/* 사이트 이용약관, 개인정보처리방침, SNS 링크가 들어가는 하단 영역 */}
            <Footer />
        </div>
    );
}

export default Home;

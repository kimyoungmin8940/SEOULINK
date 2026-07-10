// Footer 컴포넌트는 사이트 하단 영역
// 로고, 약관 링크, 개인정보처리방침, 고객센터, SNS 아이콘을 배치
import logoSymbol from '../../assets/images/logo-symbol.png';
import logoText from '../../assets/images/logo-text.png';

// 인스타그램 아이콘입니다. 외부 이미지가 아니라 SVG를 직접 그려 사용
function InstagramIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <rect x="4.2" y="4.2" width="15.6" height="15.6" rx="4.4" />
            <circle cx="12" cy="12" r="3.7" />
            <circle cx="16.7" cy="7.3" r="0.8" fill="currentColor" stroke="none" />
        </svg>
    );
}

// X/Twitter 아이콘
function XIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
            <path d="M19.7 7.4v.5c0 5.1-3.9 11-11 11-2.2 0-4.2-.6-5.9-1.8h.9c1.8 0 3.5-.6 4.8-1.7-1.7 0-3.1-1.1-3.6-2.7.2 0 .5.1.7.1.4 0 .7 0 1-.1-1.7-.4-3.1-1.9-3.1-3.7v-.1c.5.3 1.1.5 1.7.5-1-.7-1.7-1.9-1.7-3.2 0-.7.2-1.4.5-1.9 1.9 2.3 4.7 3.9 7.8 4-.1-.3-.1-.6-.1-.9 0-2.1 1.7-3.8 3.8-3.8 1.1 0 2.1.5 2.8 1.2.9-.2 1.7-.5 2.4-.9-.3.9-.9 1.6-1.7 2.1.8-.1 1.5-.3 2.2-.6-.4.7-1 1.4-1.7 1.9Z" />
        </svg>
    );
}

// 유튜브 아이콘
function YoutubeIcon() {
    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <rect x="3.7" y="6.2" width="16.6" height="11.6" rx="3.2" />
            <path d="M10.6 9.3L14.9 12L10.6 14.7Z" fill="currentColor" stroke="none" />
        </svg>
    );
}

function Footer() {
    return (
        <footer className="footer">
            {/* 하단 로고. 클릭하면 메인페이지로 이동 */}
            <a href="/" className="footer-logo" aria-label="메인페이지로 이동">
                <img className="footer-logo-symbol" src={logoSymbol} alt="SEOULINK 로고" />
                <img className="footer-logo-text" src={logoText} alt="SEOULINK" />
            </a>

            {/* 서비스 안내 링크 */}
            <div className="footer-links" aria-label="서비스 안내 문구">
                <span>이용약관</span>
                <span>개인정보처리방침</span>
                <span>고객센터</span>
            </div>

            {/* SNS 아이콘은 현재 연결하지 않도록 링크가 아닌 span으로 배치 */}
            <div className="sns" aria-label="SNS 아이콘">
                <span className="sns-icon" aria-label="Instagram">
                    <InstagramIcon />
                </span>
                <span className="sns-icon" aria-label="Twitter">
                    <XIcon />
                </span>
                <span className="sns-icon" aria-label="YouTube">
                    <YoutubeIcon />
                </span>
            </div>
        </footer>
    );
}

export default Footer;

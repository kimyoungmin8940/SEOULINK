import logoSymbol from '../../assets/images/logo-symbol.png';
import logoText from '../../assets/images/logo-text.png';

function InstagramIcon() {
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><rect x="4.2" y="4.2" width="15.6" height="15.6" rx="4.4" /><circle cx="12" cy="12" r="3.7" /><circle cx="16.7" cy="7.3" r="0.8" fill="currentColor" stroke="none" /></svg>;
}

function XIcon() {
  return <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M19.7 7.4v.5c0 5.1-3.9 11-11 11-2.2 0-4.2-.6-5.9-1.8h.9c1.8 0 3.5-.6 4.8-1.7-1.7 0-3.1-1.1-3.6-2.7.2 0 .5.1.7.1.4 0 .7 0 1-.1-1.7-.4-3.1-1.9-3.1-3.7v-.1c.5.3 1.1.5 1.7.5-1-.7-1.7-1.9-1.7-3.2 0-.7.2-1.4.5-1.9 1.9 2.3 4.7 3.9 7.8 4-.1-.3-.1-.6-.1-.9 0-2.1 1.7-3.8 3.8-3.8 1.1 0 2.1.5 2.8 1.2.9-.2 1.7-.5 2.4-.9-.3.9-.9 1.6-1.7 2.1.8-.1 1.5-.3 2.2-.6-.4.7-1 1.4-1.7 1.9Z" /></svg>;
}

function YoutubeIcon() {
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><rect x="3.7" y="6.2" width="16.6" height="11.6" rx="3.2" /><path d="M10.6 9.3L14.9 12L10.6 14.7Z" fill="currentColor" stroke="none" /></svg>;
}

function Footer() {
  return <footer className="footer">
    <a href="/" className="footer-logo" aria-label="SEOULINK 메인으로 이동"><img className="footer-logo-symbol" src={logoSymbol} alt="SEOULINK 로고" data-photo-filter="off" /><img className="footer-logo-text" src={logoText} alt="SEOULINK" data-photo-filter="off" /></a>
    <nav className="footer-links" aria-label="서비스 안내 메뉴"><a href="/terms">이용약관</a><a href="/privacy">개인정보처리방침</a><a href="/support">고객센터</a></nav>
    <div className="sns" aria-label="SNS"><span className="sns-icon" aria-label="Instagram"><InstagramIcon /></span><span className="sns-icon" aria-label="Twitter"><XIcon /></span><span className="sns-icon" aria-label="YouTube"><YoutubeIcon /></span></div>
  </footer>;
}

export default Footer;

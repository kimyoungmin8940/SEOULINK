// PagePlaceholder
// 아직 실제 화면을 만들기 전인 페이지들이 공통으로 사용하는 임시 화면입니다.
// 라우팅 연결이 제대로 되었는지 확인하는 용도로 쓰고,
// 담당자가 실제 페이지를 만들면 이 컴포넌트 대신 전용 컴포넌트로 교체하면 됩니다.

import Header from './Header';
import Footer from './Footer';

function PagePlaceholder({ title, description, links = [] }) {
    return (
        <div className="page">
            <Header />

            <main className="page-shell">
                <section className="page-card">
                    <p className="page-eyebrow">SEOULINK</p>
                    <h1>{title}</h1>
                    <p>{description}</p>

                    {links.length > 0 && (
                        <div className="page-actions">
                            {links.map((link) => (
                                <a className="page-link-btn" href={link.href} key={link.href}>
                                    {link.label}
                                </a>
                            ))}
                        </div>
                    )}
                </section>
            </main>

            <Footer />
        </div>
    );
}

export default PagePlaceholder;

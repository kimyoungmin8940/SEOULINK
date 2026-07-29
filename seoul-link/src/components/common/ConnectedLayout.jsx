import Header from './Header';
import Footer from './Footer';

export function ConnectedLayout({ title, description, children, actions }) {
    return (
        <div className="connected-page">
            <Header variant="default" />
            <main className="connected-shell">
                <header className="connected-heading">
                    <p>SEOULINK</p>
                    <h1>{title}</h1>
                    {description && <span>{description}</span>}
                    {actions && <div className="connected-actions">{actions}</div>}
                </header>
                {children}
            </main>
            <Footer />
        </div>
    );
}

export function AsyncState({ loading, error, empty, children }) {
    if (loading) return <div className="connected-state">데이터를 불러오고 있습니다.</div>;
    if (error) return <div className="connected-state error">{error}</div>;
    if (empty) return <div className="connected-state">표시할 데이터가 없습니다.</div>;
    return children;
}

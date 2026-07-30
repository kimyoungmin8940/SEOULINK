import { Bookmark, BriefcaseBusiness, CreditCard, MessageCircle, Pencil, Plus, RefreshCw, Route, Sparkles, UserRound } from 'lucide-react';
import { authStore } from '../../store/authStore';
import '../../styles/mypage-shared-sidebar.css';

const items = [
    ['내 여행 정보', '/mypage', BriefcaseBusiness],
    ['취향 검사 결과', '/mypage/travel-type', Sparkles],
    ['저장한 추천 코스', '/mypage/courses', Bookmark],
    ['직접 만든 코스', '/mypage/custom-courses', Route],
    ['내가 쓴 후기와 댓글', '/mypage/reviews', MessageCircle],
    ['결제 내역', '/mypage/payments', CreditCard],
];

export default function MypageSidebar({ activePath, bottomAction = 'map-course' }) {
    const member = authStore.getMember() || {};
    const isRetestAction = bottomAction === 'retest';

    return <aside className="mypage-v3-sidebar">
        <section className="mypage-v3-profile"><div className="mypage-v3-avatar"><UserRound size={54} strokeWidth={1.5} /></div><strong>{member.nickname || member.name || '여행자'}님</strong><span>{member.email || 'user@seoulink.com'}</span><a className="mypage-profile-edit" href="/mypage/profile-edit"><Pencil size={16} />회원 정보 수정</a></section>
        <nav className="mypage-v3-menu" aria-label="마이페이지 메뉴">{items.map(([label, path, Icon]) => <a className={path === activePath ? 'active' : ''} href={path} key={path}><Icon size={20} strokeWidth={1.8} /><span>{label}</span></a>)}</nav>
        <a className="mypage-retest" href={isRetestAction ? '/survey' : '/map-course?category=palace-culture'}>
            {isRetestAction ? <RefreshCw size={17} /> : <Plus size={18} />}
            {isRetestAction ? '취향 검사 다시하기' : '지도 코스 만들기'}
        </a>
    </aside>;
}

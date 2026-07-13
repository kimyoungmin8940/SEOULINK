import { useState } from 'react';
import { login } from '../api/authApi';
import { authStore } from '../store/authStore';
import '../styles/member-pages.css';

export default function LoginPage() {
  const [loginId, setLoginId] = useState(''); const [password, setPassword] = useState(''); const [error, setError] = useState(''); const [loading, setLoading] = useState(false);
  const submit = async (event) => { event.preventDefault(); setLoading(true); setError(''); try { const member = await login({ loginId, password }); authStore.setMember(member); window.location.assign('/'); } catch (e) { setError(e.message || '아이디 또는 비밀번호를 확인해 주세요.'); } finally { setLoading(false); } };
  return <main className="member-page"><section className="member-card"><p className="member-kicker">로그인</p><h1>서울의 다양한 여행을<br />시작해 보세요</h1><form onSubmit={submit}><label>아이디 또는 이메일<input value={loginId} onChange={(e) => setLoginId(e.target.value)} placeholder="아이디 또는 이메일" required /></label><label>비밀번호<input type="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="비밀번호" required /></label>{error && <p className="member-error">{error}</p>}<button disabled={loading}>{loading ? '로그인 중...' : '로그인'}</button></form><div className="member-divider">또는</div><div className="member-social"><button type="button">카카오로 로그인</button><button type="button">네이버로 로그인</button></div><p className="member-link">계정이 없으신가요? <a href="/signup">회원가입</a></p></section></main>;
}

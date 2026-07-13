import { useState } from 'react';
import { CheckCircle2, LockKeyhole, Mail, Sparkles, UserRound } from 'lucide-react';
import { signup } from '../../api/authApi';
import { authStore } from '../../store/authStore';
import heroSeoul from '../../assets/images/hero-seoul-main.png';

const initialForm = { loginId: '', password: '', passwordConfirm: '', name: '', nickname: '', email: '', phone: '' };

function SignupPage() {
  const [form, setForm] = useState(initialForm);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState('');

  const update = (event) => setForm((prev) => ({ ...prev, [event.target.name]: event.target.value }));

  const submit = async (event) => {
    event.preventDefault();
    if (form.password !== form.passwordConfirm) return setMessage('비밀번호 확인이 일치하지 않습니다.');
    try {
      setLoading(true); setMessage('');
      const member = await signup(form);
      authStore.setMember(member);
      window.location.assign('/chatbot');
    } catch (error) {
      setMessage(error.message || '회원가입에 실패했습니다.');
    } finally { setLoading(false); }
  };

  return (
    <main className="demo-signup-page" style={{ '--signup-bg': `url(${heroSeoul})` }}>
      <section className="signup-copy">
        <a href="/" className="signup-brand"><span>SL</span> SEOULLINK</a>
        <div><small><Sparkles size={15} /> PROJECT DEMO ACCOUNT</small><h1>서울 여행을 위한<br />임시 체험 계정을 만들어보세요</h1><p>회원가입이 완료되면 7일 AI 챗봇 체험권이 자동으로 지급되어 결제 전에도 구현된 기능을 확인할 수 있습니다.</p>
          <ul><li><CheckCircle2 /> AI 서울 여행 코스 추천</li><li><CheckCircle2 /> 대화 및 추천 코스 자동 저장</li><li><CheckCircle2 /> 7일 개발용 체험 이용권</li></ul>
        </div>
      </section>
      <section className="signup-card"><header><h2>임시 회원가입</h2><p>테스트에 사용할 계정 정보를 입력하세요.</p></header>
        <form onSubmit={submit}>
          <label>아이디<div><UserRound /><input name="loginId" value={form.loginId} onChange={update} placeholder="영문·숫자 4자 이상" minLength="4" required /></div></label>
          <div className="signup-two"><label>이름<input name="name" value={form.name} onChange={update} required /></label><label>닉네임<input name="nickname" value={form.nickname} onChange={update} /></label></div>
          <label>이메일<div><Mail /><input name="email" type="email" value={form.email} onChange={update} placeholder="demo@seoulink.com" required /></div></label>
          <div className="signup-two"><label>비밀번호<div><LockKeyhole /><input name="password" type="password" value={form.password} onChange={update} placeholder="영문+숫자 8자 이상" minLength="8" required /></div></label><label>비밀번호 확인<input name="passwordConfirm" type="password" value={form.passwordConfirm} onChange={update} required /></label></div>
          {message && <p className="signup-error">{message}</p>}
          <button type="submit" disabled={loading}>{loading ? '계정 생성 중...' : '체험 계정 만들기'}</button>
          <p className="signup-login-link">이미 계정이 있나요? <a href="/login">로그인</a></p>
        </form>
      </section>
    </main>
  );
}

export default SignupPage;

import { useState } from 'react';
import { ArrowRight, ChevronDown, Headphones, LockKeyhole, Mail, ShieldCheck, Sparkles } from 'lucide-react';
import Header from '../../components/common/Header';
import Footer from '../../components/common/Footer';

const policies = {
  terms: [
    ['제1조 (목적)', '이 약관은 SEOULINK가 제공하는 서울 여행 코스 추천, 지도 코스 만들기, 여행 후기, AI 여행 플래너 및 이용권 관련 서비스의 이용 조건과 회원의 권리·의무를 정합니다.'],
    ['제2조 (서비스 이용)', '회원은 정확한 정보를 바탕으로 서비스를 이용해야 하며, 다른 사람의 계정을 사용하거나 서비스 운영을 방해하는 행위를 해서는 안 됩니다. 서비스는 개선과 운영상 필요에 따라 일부 변경되거나 일시 중단될 수 있습니다.'],
    ['제3조 (여행 정보와 추천)', 'SEOULINK의 코스, 장소, 이동 시간 및 AI 응답은 여행 계획을 돕기 위한 참고 정보입니다. 실제 운영 시간, 휴무일, 혼잡도, 요금, 교통 상황은 방문 전 해당 장소와 관계 기관의 최신 안내를 확인해 주세요.'],
    ['제4조 (회원 콘텐츠)', '후기, 댓글, 코스 메모 등 회원이 등록한 콘텐츠의 책임은 작성자에게 있습니다. 타인의 권리를 침해하거나 광고·혐오·불법 정보를 포함한 콘텐츠는 사전 안내 없이 제한 또는 삭제될 수 있습니다.'],
    ['제5조 (AI 이용권 및 결제)', 'AI 이용권의 가격, 사용 기간, 제공 기능은 결제 화면에 표시된 내용을 따릅니다. 결제 완료 후 이용 내역은 마이페이지에서 확인할 수 있으며, 환불은 관련 법령 및 결제 수단의 기준에 따라 처리됩니다.'],
    ['제6조 (약관의 변경)', '서비스 운영 또는 관련 법령의 변경이 있는 경우 약관을 개정할 수 있습니다. 중요한 변경 사항은 시행 전에 서비스 화면을 통해 안내합니다.'],
  ],
  privacy: [
    ['1. 수집하는 개인정보', '회원가입과 서비스 이용을 위해 이메일, 닉네임, 로그인 식별 정보, 서비스 이용 기록을 수집할 수 있습니다. AI 이용권 결제 시 결제 처리는 결제대행사를 통해 이루어지며, SEOULINK는 결제 확인에 필요한 최소 정보만 처리합니다.'],
    ['2. 이용 목적', '회원 식별, 맞춤 여행 코스 제공, 저장 코스와 후기 관리, AI 이용권 제공 및 결제 확인, 문의 응대, 서비스 품질 개선을 위해 정보를 이용합니다.'],
    ['3. 보유 및 이용 기간', '개인정보는 회원 탈퇴 또는 처리 목적 달성 시 지체 없이 삭제합니다. 단, 관계 법령에 보관 의무가 있는 정보는 해당 기간 동안 보관할 수 있습니다.'],
    ['4. 제3자 제공 및 처리 위탁', 'SEOULINK는 원칙적으로 이용자의 동의 없이 개인정보를 제3자에게 제공하지 않습니다. 결제, 로그인 연동, 서비스 운영에 필요한 경우에는 관련 법령과 계약에 따라 안전하게 처리합니다.'],
    ['5. 이용자의 권리', '이용자는 언제든지 자신의 개인정보를 조회·수정하거나 삭제, 처리 정지를 요청할 수 있습니다. 계정 또는 서비스 이용과 관련된 요청은 고객센터를 통해 접수할 수 있습니다.'],
    ['6. 안전성 확보 조치', '개인정보 보호를 위해 접근 권한 관리, 전송 구간 보호, 접속 기록 관리 등 합리적인 보호 조치를 적용합니다.'],
  ],
};

const faqs = [
  ['AI 이용권은 어디에서 확인하나요?', '로그인 후 마이페이지의 결제 내역에서 현재 이용권과 결제 상태를 확인할 수 있습니다.'],
  ['추천 코스는 저장할 수 있나요?', '추천 결과에서 저장한 코스는 마이페이지의 저장한 추천 코스에서 다시 확인할 수 있습니다.'],
  ['장소 정보나 운영 시간이 실제와 달라요.', '장소 정보는 변동될 수 있습니다. 방문 전 공식 채널에서 운영 시간과 휴무일을 한 번 더 확인해 주세요.'],
  ['후기 또는 댓글을 수정·삭제하고 싶어요.', '마이페이지의 내가 쓴 후기와 댓글에서 작성한 내용을 확인하고 관리할 수 있습니다.'],
];

function SupportPage() {
  const [openFaq, setOpenFaq] = useState(0);
  return <><section className="service-info-hero support-hero"><span className="service-info-eyebrow"><Headphones size={16} /> SEOULINK HELP</span><h1>여행이 더 편안해지도록<br />도와드릴게요.</h1><p>서비스 이용 중 궁금한 점이 있다면 자주 묻는 질문부터 확인해 보세요.</p></section><main className="service-info-main support-main"><section className="support-faq"><div className="service-section-heading"><span>FAQ</span><h2>자주 묻는 질문</h2><p>가장 많이 찾는 도움말을 모았어요.</p></div><div className="faq-list">{faqs.map(([question, answer], index) => <article className={openFaq === index ? 'open' : ''} key={question}><button type="button" onClick={() => setOpenFaq(openFaq === index ? -1 : index)}><span>Q</span>{question}<ChevronDown /></button>{openFaq === index && <p>{answer}</p>}</article>)}</div></section><section className="support-contact"><Mail size={25} /><div><strong>추가 도움이 필요하신가요?</strong><p>프로젝트 시연 환경에서는 서비스 내 메뉴와 FAQ를 통해 안내를 제공합니다.</p></div><a href="/">메인으로 돌아가기 <ArrowRight size={15} /></a></section></main></>;
}

function PolicyPage({ type }) {
  const isTerms = type === 'terms';
  const Icon = isTerms ? ShieldCheck : LockKeyhole;
  return <><section className="service-info-hero"><span className="service-info-eyebrow"><Icon size={16} /> SEOULINK POLICY</span><h1>{isTerms ? 'SEOULINK 이용약관' : '개인정보처리방침'}</h1><p>{isTerms ? '서울 여행을 함께 계획하는 모든 사용자를 위한 약속입니다.' : '여행을 위한 정보가 안전하게 다뤄지도록 노력합니다.'}</p><small>시행일: 2026년 7월 29일</small></section><main className="service-info-main policy-main"><aside className="policy-side"><strong>{isTerms ? '이용약관' : '개인정보처리방침'}</strong>{policies[type].map(([title], index) => <a href={`#policy-${index}`} key={title}>{title}</a>)}</aside><article className="policy-document"><div className="policy-notice"><Sparkles size={18} /><span>SEOULINK의 현재 서비스 범위를 기준으로 작성된 안내입니다. 정식 서비스 운영 전 법률 검토 및 정보 처리 현황에 따라 보완될 수 있습니다.</span></div>{policies[type].map(([title, content], index) => <section id={`policy-${index}`} key={title}><h2>{title}</h2><p>{content}</p></section>)}</article></main></>;
}

function ServiceInfoPage({ type }) { return <div className="service-info-page"><Header />{type === 'support' ? <SupportPage /> : <PolicyPage type={type} />}<Footer /></div>; }
export default ServiceInfoPage;

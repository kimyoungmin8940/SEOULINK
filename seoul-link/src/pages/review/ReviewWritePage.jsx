import { useEffect, useState } from 'react';
import { ImagePlus, Star, X } from 'lucide-react';
import Header from '../../components/common/Header';
import Footer from '../../components/common/Footer';
import { createReview } from '../../api/reviewApi';
import { getMyCourses } from '../../api/courseApi';
import { authStore } from '../../store/authStore';
import '../../styles/review-pages.css';

const tagOptions = ['혼자 여행', '데이트', '가족 여행', '맛집', '야경', '카페 투어', '사진 명소', '비 오는 날'];
const scoreOptions = Array.from({ length: 9 }, (_, index) => (index + 2) / 2);

function LegacyRatingStars({ value, onChange }) {
    const fillAmount = (star) => value >= star ? 'full' : value >= star - 0.5 ? 'half' : 'empty';
    return <div className="rating-control" role="radiogroup" aria-label="평점">
        <div className="rating-preview" aria-label={`현재 평점 ${value.toFixed(1)}점`}>{[1, 2, 3, 4, 5].map((star) => <span className={`rating-star ${fillAmount(star)}`} key={star}><Star className="rating-star-outline" /><Star className="rating-star-fill" fill="currentColor" /></span>)}<strong>{value.toFixed(1)}</strong><small>/ 5.0</small></div>
        <div className="rating-score-grid">{scoreOptions.map((score) => <button type="button" key={score} className={value === score ? 'selected' : ''} onClick={() => onChange(score)} aria-pressed={value === score}>{score.toFixed(1)}</button>)}</div>
    </div>;
}

function RatingStars({ value, onChange }) {
    const numericValue = Number(value);
    const rating = Number.isFinite(numericValue) ? Math.min(5, Math.max(0, numericValue)) : 0;
    const commitRating = () => onChange(Number.isFinite(numericValue) ? rating.toFixed(1) : '');

    return <div className="rating-control" aria-label="평점">
        <div className="rating-preview" aria-label={`현재 평점 ${rating.toFixed(1)}점`}>
            {[1, 2, 3, 4, 5].map((star) => {
                const fill = Math.max(0, Math.min(100, (rating - (star - 1)) * 100));
                return <span className="rating-star" key={star} aria-hidden="true">
                    <Star className="rating-star-outline" />
                    <Star className="rating-star-fill" fill="currentColor" style={{ clipPath: `inset(0 ${100 - fill}% 0 0)` }} />
                </span>;
            })}
            <strong>{rating.toFixed(1)}</strong><small>/ 5.0</small>
        </div>
        <label className="rating-number-input">
            <span>평점 입력</span>
            <input type="number" min="0" max="5" step="0.1" inputMode="decimal" value={value} onChange={(event) => onChange(event.target.value)} onBlur={commitRating} placeholder="예: 4.4" aria-label="0.0부터 5.0까지 평점 입력" />
            <em>0.0 ~ 5.0점 · 소수점 첫째 자리</em>
        </label>
    </div>;
}

function ReviewWritePage() {
    const member = authStore.getMember();
    const [courses, setCourses] = useState([]);
    const [form, setForm] = useState({ courseId: '', reviewTitle: '', reviewContent: '', rating: 5, visitDate: '', companion: '친구와 함께', imageUrls: [], tags: [] });
    const [imageUrl, setImageUrl] = useState('');
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState('');
    const set = (key, value) => setForm((prev) => ({ ...prev, [key]: value }));
    useEffect(() => { if (member?.memberId) getMyCourses(member.memberId).then(setCourses).catch(() => setError('내 코스를 불러오지 못했습니다.')); }, []);
    const addImage = () => { if (imageUrl.trim() && form.imageUrls.length < 8) { set('imageUrls', [...form.imageUrls, imageUrl.trim()]); setImageUrl(''); } };
    const toggleTag = (tag) => set('tags', form.tags.includes(tag) ? form.tags.filter((item) => item !== tag) : form.tags.length < 8 ? [...form.tags, tag] : form.tags);
    const submit = async (event) => {
        event.preventDefault();
        if (!member?.memberId) { window.location.href = '/login'; return; }
        if (!form.courseId) { setError('후기를 남길 여행 코스를 선택해 주세요.'); return; }
        if (!Number.isFinite(Number(form.rating)) || Number(form.rating) < 0 || Number(form.rating) > 5) { setError('평점은 0.0점부터 5.0점 사이로 입력해 주세요.'); return; }
        setSubmitting(true); setError('');
        try { await createReview({ ...form, courseId: Number(form.courseId), rating: Number(Number(form.rating).toFixed(1)), memberId: member.memberId }); window.location.href = '/reviews'; }
        catch (err) { setError(err.message || '후기 등록에 실패했습니다.'); setSubmitting(false); }
    };
    return <><Header /><main className="review-write-shell"><div className="breadcrumbs">홈　›　여행 후기　›　후기 작성</div><form onSubmit={submit} className="review-composer"><aside className="composer-side"><section><h3>작성자 정보</h3><strong>{member?.nickname || member?.name || '여행자'}</strong><p>여행 코스의 생생한 경험을 나눠주세요.</p></section><section><h3>방문 정보</h3><label>방문일<input type="date" value={form.visitDate} onChange={(event) => set('visitDate', event.target.value)} /></label><label>동행<select value={form.companion} onChange={(event) => set('companion', event.target.value)}>{['혼자', '친구와 함께', '연인과 함께', '가족과 함께', '아이와 함께'].map((item) => <option key={item}>{item}</option>)}</select></label></section><section><h3>평점 <small>0.5점 단위</small></h3><RatingStars value={form.rating} onChange={(score) => set('rating', score)} /></section><section><h3>태그 <small>최대 8개</small></h3><div className="tag-choice">{tagOptions.map((tag) => <button type="button" className={form.tags.includes(tag) ? 'selected' : ''} onClick={() => toggleTag(tag)} key={tag}>#{tag}</button>)}</div></section></aside><section className="composer-main"><h1>여행 코스 후기를 작성해 주세요</h1><p>내가 완성한 서울 여행 코스를 다른 여행자에게 소개해보세요.</p><label>여행 코스<select className="course-select" required value={form.courseId} onChange={(event) => set('courseId', event.target.value)}><option value="">후기를 남길 코스를 선택하세요</option>{courses.map((course) => <option value={course.courseId} key={course.courseId}>{course.title} · {course.placeCount}개 장소</option>)}</select></label>{courses.length === 0 && <p className="selected-place">등록한 코스가 없습니다. 지도 코스 만들기에서 먼저 코스를 만들어 주세요.</p>}<label>제목<input required maxLength="200" value={form.reviewTitle} onChange={(event) => set('reviewTitle', event.target.value)} placeholder="여행의 기억을 제목으로 남겨보세요" /></label><label>내용<textarea required maxLength="4000" value={form.reviewContent} onChange={(event) => set('reviewContent', event.target.value)} placeholder="코스의 동선, 좋았던 장소, 여행 팁을 자유롭게 작성해 주세요." /><small>{form.reviewContent.length} / 4,000</small></label></section><aside className="composer-images"><h3>사진 스토리보드 <small>최대 8장</small></h3><div className="image-url-add"><input value={imageUrl} onChange={(event) => setImageUrl(event.target.value)} placeholder="사진 URL 붙여넣기" /><button type="button" onClick={addImage}>추가</button></div><div className="story-grid">{form.imageUrls.map((url, index) => <figure key={url}><img src={url} alt={`첨부 사진 ${index + 1}`} /><button type="button" onClick={() => set('imageUrls', form.imageUrls.filter((_, position) => position !== index))}><X size={16} /></button></figure>)}{form.imageUrls.length < 8 && <button type="button" className="image-placeholder" onClick={() => document.querySelector('.image-url-add input')?.focus()}><ImagePlus /><span>사진 추가</span></button>}</div></aside><footer className="composer-footer">{error && <p>{error}</p>}<a href="/reviews">취소</a><button disabled={submitting}>{submitting ? '등록 중...' : '후기 등록하기'}</button></footer></form></main><Footer /></>;
}
export default ReviewWritePage;

import { useEffect, useRef, useState } from 'react';
import { ImagePlus, X } from 'lucide-react';
import Header from '../../components/common/Header';
import Footer from '../../components/common/Footer';
import { getReviewDetail, updateReview, uploadReviewImages } from '../../api/reviewApi';
import { authStore } from '../../store/authStore';
import '../../styles/review-pages.css';
import '../../styles/review-primary-color.css';

// 기존 '부모님/아이와 함께' 데이터도 수정 화면에서는 '가족'으로 정규화한다.
// 저장 시에는 혼자·연인·친구·가족 중 하나만 백엔드로 전달한다.

const normalizeCompanion = (companion) => {
  if (['부모님과 함께', '아이와 함께', '가족과 함께'].includes(companion)) return '가족';
  if (companion === '친구와 함께') return '친구';
  if (companion === '연인과 함께') return '연인';
  return ['혼자', '연인', '친구', '가족'].includes(companion) ? companion : '혼자';
};

const tagOptions = ['혼자 여행', '데이트', '가족 여행', '맛집', '야경', '카페 투어', '사진 명소', '비 오는 날'];
const companions = ['혼자', '연인', '친구', '가족'];

function ReviewEditPage() {
  const reviewId = window.location.pathname.split('/').filter(Boolean).at(-2);
  const member = authStore.getMember();
  const imageInputRef = useRef(null);
  const [review, setReview] = useState(null);
  const [form, setForm] = useState(null);
  const [error, setError] = useState('');
  const [uploading, setUploading] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!member?.memberId) return;
    getReviewDetail(reviewId, member.memberId).then((data) => {
      if (data.memberId !== member.memberId) {
        setError('작성자만 후기를 수정할 수 있습니다.');
        return;
      }
      setReview(data);
      setForm({ reviewTitle: data.reviewTitle || '', reviewContent: data.reviewContent || '', rating: data.rating ?? 5, visitDate: data.visitDate || '', companion: normalizeCompanion(data.companion), imageUrls: data.imageUrls || (data.imageUrl ? [data.imageUrl] : []), tags: data.tags || [] });
    }).catch((loadError) => setError(loadError.message || '후기를 불러오지 못했습니다.'));
  }, [reviewId, member?.memberId]);

  const set = (key, value) => setForm((current) => ({ ...current, [key]: value }));
  const toggleTag = (tag) => set('tags', form.tags.includes(tag) ? form.tags.filter((item) => item !== tag) : form.tags.length < 8 ? [...form.tags, tag] : form.tags);
  const handleImageFiles = async (event) => {
    const files = Array.from(event.target.files || []).slice(0, 8 - form.imageUrls.length);
    if (!files.length) return;
    setUploading(true); setError('');
    try { const uploaded = await uploadReviewImages(files); set('imageUrls', [...form.imageUrls, ...uploaded.imageUrls]); }
    catch (uploadError) { setError(uploadError.message || '사진 업로드에 실패했습니다.'); }
    finally { setUploading(false); event.target.value = ''; }
  };
  const submit = async (event) => {
    event.preventDefault();
    const rating = Number(form.rating);
    if (!form.reviewTitle.trim() || !form.reviewContent.trim()) return setError('제목과 내용을 입력해 주세요.');
    if (!Number.isFinite(rating) || rating < 0 || rating > 5) return setError('평점은 0.0에서 5.0 사이로 입력해 주세요.');
    setSubmitting(true); setError('');
    try {
      await updateReview(reviewId, { ...form, memberId: member.memberId, reviewTitle: form.reviewTitle.trim(), reviewContent: form.reviewContent.trim(), rating: Number(rating.toFixed(1)) });
      window.location.href = `/reviews/${reviewId}`;
    } catch (submitError) { setError(submitError.message || '후기 수정에 실패했습니다.'); setSubmitting(false); }
  };

  if (error && !form) return <><Header /><p className="review-message">{error}</p><Footer /></>;
  if (!form) return <><Header /><p className="review-message">후기를 불러오는 중입니다.</p><Footer /></>;
  return <><Header /><main className="review-write-shell"><div className="breadcrumbs">홈 · 여행 후기 · 후기 수정</div><form onSubmit={submit} className="review-composer">
    <aside className="composer-side"><section><h3>수정하는 후기</h3><strong>{review?.placeName || '서울 여행지'}</strong></section><section><h3>방문 정보</h3><label>방문일<input type="date" value={form.visitDate} onChange={(event) => set('visitDate', event.target.value)} /></label><label>동행<select value={form.companion} onChange={(event) => set('companion', event.target.value)}>{companions.map((item) => <option key={item}>{item}</option>)}</select></label></section><section><h3>평점</h3><label className="rating-number-input"><span>평점 입력</span><input type="number" min="0" max="5" step="0.1" inputMode="decimal" value={form.rating} onChange={(event) => set('rating', event.target.value)} /></label></section><section><h3>태그 <small>최대 8개</small></h3><div className="tag-choice">{tagOptions.map((tag) => <button type="button" className={form.tags.includes(tag) ? 'selected' : ''} onClick={() => toggleTag(tag)} key={tag}>#{tag}</button>)}</div></section></aside>
    <section className="composer-main"><h1>여행 후기를 수정해 주세요</h1><p>코스와 장소 정보는 유지되며, 후기 내용만 변경할 수 있습니다.</p><label>제목<input required maxLength="200" value={form.reviewTitle} onChange={(event) => set('reviewTitle', event.target.value)} /></label><label>내용<textarea required maxLength="4000" value={form.reviewContent} onChange={(event) => set('reviewContent', event.target.value)} /><small>{form.reviewContent.length} / 4,000</small></label></section>
    <aside className="composer-images"><h3>사진 스토리보드 <small>최대 8장</small></h3><input ref={imageInputRef} className="image-file-input" type="file" accept="image/jpeg,image/png,image/webp,image/gif" multiple onChange={handleImageFiles} /><button type="button" onClick={() => imageInputRef.current?.click()} disabled={uploading || form.imageUrls.length >= 8}>{uploading ? '업로드 중...' : '파일에서 사진 찾기'}</button><div className="story-grid">{form.imageUrls.map((url, index) => <figure key={`${url}-${index}`}><img src={url} alt={`첨부 사진 ${index + 1}`} /><button type="button" aria-label="사진 삭제" onClick={() => set('imageUrls', form.imageUrls.filter((_, position) => position !== index))}><X size={16} /></button></figure>)}{form.imageUrls.length < 8 && <button type="button" className="image-placeholder" onClick={() => imageInputRef.current?.click()}><ImagePlus /><span>사진 추가</span></button>}</div></aside>
    <footer className="composer-footer">{error && <p>{error}</p>}<a href={`/reviews/${reviewId}`}>취소</a><button disabled={submitting || uploading}>{submitting ? '수정 중...' : '후기 수정하기'}</button></footer>
  </form></main><Footer /></>;
}
export default ReviewEditPage;

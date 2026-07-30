import { useEffect, useRef, useState } from 'react';
import { ImagePlus, Star, X } from 'lucide-react';
import Header from '../../components/common/Header';
import Footer from '../../components/common/Footer';
import { createReview, uploadReviewImages } from '../../api/reviewApi';
import { getMyCourses } from '../../api/courseApi';
import { authStore } from '../../store/authStore';
import '../../styles/review-pages.css';
import '../../styles/review-primary-color.css';

// 작성 화면은 이미지 업로드를 먼저 완료하고, 반환된 URL 배열만 후기 저장 요청에 담는다.
// 이렇게 하면 파일 자체를 후기 생성 API로 중복 전송하지 않는다.

// 작성자가 선택할 수 있는 후기 태그 목록이다. 선택된 값만 form.tags에 저장된다.
const tagOptions = [
  '혼자 여행',
  '데이트',
  '가족 여행',
  '맛집',
  '야경',
  '카페 투어',
  '사진 명소',
  '비 오는 날'
];

// 별 아이콘을 겹쳐 표시해 소수점 평점도 시각적으로 표현한다.
function RatingStars({ value, onChange }) {
  // 입력값이 비어 있거나 범위를 벗어나도 화면에는 0.0~5.0으로 안전하게 표시한다.
  const rating = Math.min(5, Math.max(0, Number(value) || 0));

  return (
    <div className="rating-control" aria-label="평점">
      <div className="rating-preview">
        {[1, 2, 3, 4, 5].map((star) => (
          <span className="rating-star" key={star}>
            <Star className="rating-star-outline" />
            {/* 해당 별에 채워질 비율을 잘라 소수점 평점을 표현한다. */}
            <Star
              className="rating-star-fill"
              fill="currentColor"
              style={{
                clipPath: `inset(0 ${100 - Math.max(0, Math.min(100, (rating - (star - 1)) * 100))}% 0 0)`
              }}
            />
          </span>
        ))}
        <strong>{rating.toFixed(1)}</strong>
        <small>/ 5.0</small>
      </div>

      {/* 실제 평점 값은 숫자 입력창에서 수정하며, 별 표시는 그 값을 즉시 반영한다. */}
      <label className="rating-number-input">
        <span>평점 입력</span>
        <input
          type="number"
          min="0"
          max="5"
          step="0.1"
          inputMode="decimal"
          value={value}
          onChange={(event) => onChange(event.target.value)}
          placeholder="예: 4.5"
        />
      </label>
    </div>
  );
}

// 저장한 추천 코스와 직접 만든 코스를 select의 그룹으로 나눠 렌더링한다.
function CourseOptions({ courses }) {
  const savedRecommendations = courses.filter((course) => course.courseType !== 'CUSTOM');
  const customCourses = courses.filter((course) => course.courseType === 'CUSTOM');

  const option = (course) => (
    <option value={course.courseId} key={course.courseId}>
      {course.title} · 장소 {course.placeCount || 0}곳
    </option>
  );

  return (
    <>
      {savedRecommendations.length > 0 && (
        <optgroup label="저장한 추천 코스">
          {savedRecommendations.map(option)}
        </optgroup>
      )}
      {customCourses.length > 0 && (
        <optgroup label="직접 만든 코스">
          {customCourses.map(option)}
        </optgroup>
      )}
    </>
  );
}

// 선택한 코스와 방문 경험을 후기 데이터로 조립해 등록하는 작성 화면이다.
function ReviewWritePage() {
  // 로그인 정보는 작성자 표시, 내 코스 조회, 후기 등록 요청에 공통으로 사용한다.
  const member = authStore.getMember();
  const [courses, setCourses] = useState([]);

  // API에 전송할 후기 입력값을 한 객체로 관리한다.
  const [form, setForm] = useState({
    courseId: '',
    reviewTitle: '',
    reviewContent: '',
    rating: 5,
    visitDate: '',
    companion: '친구',
    imageUrls: [],
    tags: []
  });

  // 업로드와 등록 중에는 중복 요청을 막기 위해 버튼 상태를 분리해 관리한다.
  const [uploading, setUploading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  // 숨겨진 파일 input을 사진 추가 버튼에서 열기 위한 참조값이다.
  const imageInputRef = useRef(null);

  // 특정 필드만 바꿀 때 기존 후기 입력값이 사라지지 않도록 병합한다.
  const set = (key, value) => {
    setForm((previous) => ({
      ...previous,
      [key]: value
    }));
  };

  // 로그인한 회원이 가진 코스만 불러와 후기 작성 대상 선택 목록에 제공한다.
  useEffect(() => {
    if (!member?.memberId) return;

    getMyCourses(member.memberId)
      .then((items) => setCourses(Array.isArray(items) ? items : []))
      .catch(() => setError('내 코스를 불러오지 못했습니다.'));
  }, [member?.memberId]);

  // 선택한 이미지 파일을 최대 8장까지 업로드하고, 서버가 반환한 URL을 스토리보드에 추가한다.
  const handleImageFiles = async (event) => {
    const files = Array.from(event.target.files || []).slice(0, 8 - form.imageUrls.length);
    if (files.length === 0) return;

    setUploading(true);
    setError('');

    try {
      const uploaded = await uploadReviewImages(files);
      set('imageUrls', [...form.imageUrls, ...uploaded.imageUrls]);
    } catch (uploadError) {
      setError(uploadError.message || '사진 업로드에 실패했습니다.');
    } finally {
      setUploading(false);
      // 같은 파일을 다시 고를 때도 change 이벤트가 발생하도록 선택값을 비운다.
      event.target.value = '';
    }
  };

  // 이미 선택한 태그는 해제하고, 새 태그는 최대 8개까지만 추가한다.
  const toggleTag = (tag) => {
    const nextTags = form.tags.includes(tag)
      ? form.tags.filter((item) => item !== tag)
      : form.tags.length < 8
        ? [...form.tags, tag]
        : form.tags;

    set('tags', nextTags);
  };

  // 입력값을 검증한 뒤 후기 생성 API를 호출하고, 성공하면 후기 목록으로 이동한다.
  const submit = async (event) => {
    event.preventDefault();

    if (!member?.memberId) {
      window.location.href = '/login';
      return;
    }

    if (!form.courseId) {
      setError('후기를 남길 여행 코스를 선택해 주세요.');
      return;
    }

    if (!Number.isFinite(Number(form.rating)) || Number(form.rating) < 0 || Number(form.rating) > 5) {
      setError('평점은 0.0부터 5.0 사이로 입력해 주세요.');
      return;
    }

    setSubmitting(true);
    setError('');

    try {
      await createReview({
        ...form,
        courseId: Number(form.courseId),
        // 서버에 보내기 전 소수 첫째 자리까지만 숫자로 정규화한다.
        rating: Number(Number(form.rating).toFixed(1)),
        memberId: member.memberId
      });
      window.location.href = '/reviews';
    } catch (submitError) {
      setError(submitError.message || '후기 등록에 실패했습니다.');
      setSubmitting(false);
    }
  };

  return (
    <>
      <Header />

      <main className="review-write-shell">
        {/* 현재 위치를 보여 주는 경로 표시 영역 */}
        <div className="breadcrumbs">홈 · 여행 후기 · 후기 작성</div>

        <form onSubmit={submit} className="review-composer">
          {/* 작성자·방문 정보·평점·태그를 입력하는 왼쪽 보조 영역 */}
          <aside className="composer-side">
            <section>
              <h3>작성자 정보</h3>
              <p />
              <p />
              <strong>{member?.nickname || member?.name || '여행자'}</strong>
            </section>

            <section>
              <h3>방문 정보</h3>
              <label>
                방문일
                <input
                  type="date"
                  value={form.visitDate}
                  onChange={(event) => set('visitDate', event.target.value)}
                />
              </label>
              <label>
                동행
                <select
                  value={form.companion}
                  onChange={(event) => set('companion', event.target.value)}
                >
                  {['혼자', '연인', '친구', '가족'].map((item) => (
                    <option key={item}>{item}</option>
                  ))}
                </select>
              </label>
            </section>

            <section>
              <h3>평점</h3>
              <RatingStars value={form.rating} onChange={(score) => set('rating', score)} />
            </section>

            <section>
              <h3>
                태그 <small>최대 8개</small>
              </h3>
              <div className="tag-choice">
                {tagOptions.map((tag) => (
                  <button
                    type="button"
                    className={form.tags.includes(tag) ? 'selected' : ''}
                    onClick={() => toggleTag(tag)}
                    key={tag}
                  >
                    #{tag}
                  </button>
                ))}
              </div>
            </section>
          </aside>

          {/* 코스 선택과 후기 본문을 입력하는 중앙의 핵심 작성 영역 */}
          <section className="composer-main">
            <h1>여행 코스 후기를 작성해 주세요</h1>
            <p />

            <label>
              여행 코스
              <select
                className="course-select"
                required
                value={form.courseId}
                onChange={(event) => set('courseId', event.target.value)}
              >
                <option value="">후기를 남길 코스를 선택하세요</option>
                <CourseOptions courses={courses} />
              </select>
            </label>

            {/* 코스가 하나도 없을 때 사용자에게 다음 행동을 안내한다. */}
            {courses.length === 0 && (
              <p>등록한 코스가 없습니다. 지도 코스 만들기에서 먼저 코스를 만들어 주세요.</p>
            )}

            <label>
              제목
              <input
                required
                maxLength="200"
                value={form.reviewTitle}
                onChange={(event) => set('reviewTitle', event.target.value)}
                placeholder="여행의 제목을 지어주세요."
              />
            </label>

            <p />

            <label>
              내용
              <textarea
                required
                maxLength="4000"
                value={form.reviewContent}
                onChange={(event) => set('reviewContent', event.target.value)}
                placeholder="코스의 동선, 좋았던 장소, 여행 팁 등 후기를 자유롭게 작성해 주세요."
              />
              <small>{form.reviewContent.length} / 4,000</small>
            </label>
          </section>

          {/* 최대 8장의 사진을 올리고, 올린 사진을 미리 보는 스토리보드 영역 */}
          <aside className="composer-images">
            <h3>
              사진 스토리보드 <small>최대 8장</small>
            </h3>

            <input
              ref={imageInputRef}
              className="image-file-input"
              type="file"
              accept="image/jpeg,image/png,image/webp,image/gif"
              multiple
              onChange={handleImageFiles}
            />

            <button
              type="button"
              onClick={() => imageInputRef.current?.click()}
              disabled={uploading || form.imageUrls.length >= 8}
            >
              {uploading ? '업로드 중…' : '파일에서 사진 찾기'}
            </button>

            <div className="story-grid">
              {form.imageUrls.map((url, index) => (
                <figure key={url}>
                  <img src={url} alt={`첨부 사진 ${index + 1}`} />
                  {/* 이 버튼은 해당 위치의 사진 URL만 목록에서 제거한다. */}
                  <button
                    type="button"
                    onClick={() => set('imageUrls', form.imageUrls.filter((_, position) => position !== index))}
                  >
                    <X size={16} />
                  </button>
                </figure>
              ))}

              {form.imageUrls.length < 8 && (
                <button
                  type="button"
                  className="image-placeholder"
                  onClick={() => imageInputRef.current?.click()}
                >
                  <ImagePlus />
                  <span>사진 추가</span>
                </button>
              )}
            </div>
          </aside>

          {/* 오류 메시지와 등록/취소 동작을 제공하는 하단 고정 영역 */}
          <footer className="composer-footer">
            {error && <p>{error}</p>}
            <a href="/reviews">취소</a>
            <button disabled={submitting || uploading}>
              {submitting ? '등록 중…' : '후기 등록하기'}
            </button>
          </footer>
        </form>
      </main>

      <Footer />
    </>
  );
}

export default ReviewWritePage;

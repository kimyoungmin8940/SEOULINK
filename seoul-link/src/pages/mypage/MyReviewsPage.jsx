import { useEffect, useState } from 'react';
import { ConnectedLayout, AsyncState } from '../../components/common/ConnectedLayout';
import { getMyReviews } from '../../api/mypageApi';
import { authStore } from '../../store/authStore';

function MyReviewsPage() {
    const member=authStore.getMember(); const [items,setItems]=useState([]); const [loading,setLoading]=useState(true); const [error,setError]=useState('');
    useEffect(()=>{getMyReviews(member.memberId).then(setItems).catch((e)=>setError(e.message)).finally(()=>setLoading(false));},[member.memberId]);
    return <ConnectedLayout title="내 후기" description="내가 작성한 방문 후기를 확인하고 수정합니다." actions={<a href="/reviews/write">후기 작성</a>}>
        <AsyncState loading={loading} error={error} empty={!items.length}><div className="connected-list">{items.map((review)=><a className="connected-card" href={`/reviews/${review.reviewId}`} key={review.reviewId}><h2>{review.reviewTitle}</h2><p>{review.reviewContent}</p><div className="connected-meta"><span>평점 {review.rating}</span><span>좋아요 {review.likeCount || 0}</span></div></a>)}</div></AsyncState>
    </ConnectedLayout>;
}
export default MyReviewsPage;

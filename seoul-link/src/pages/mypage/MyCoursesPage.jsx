import { useEffect, useState } from 'react';
import { ConnectedLayout, AsyncState } from '../../components/common/ConnectedLayout';
import { getMyCourses } from '../../api/mypageApi';
import { authStore } from '../../store/authStore';

function MyCoursesPage() {
    const member = authStore.getMember(); const [items,setItems]=useState([]); const [loading,setLoading]=useState(true); const [error,setError]=useState('');
    useEffect(()=>{getMyCourses(member.memberId).then(setItems).catch((e)=>setError(e.message)).finally(()=>setLoading(false));},[member.memberId]);
    return <ConnectedLayout title="내 코스" description="자동 추천받거나 직접 만든 코스를 확인합니다." actions={<a href="/map-course">새 코스 만들기</a>}>
        <AsyncState loading={loading} error={error} empty={!items.length}><div className="connected-grid">{items.map((course)=><a className="connected-card" href={`/courses/${course.courseId}`} key={course.courseId}><h2>{course.title}</h2><p>{course.description || course.region}</p><div className="connected-meta"><span>{course.courseType}</span><span>{course.region}</span></div><small>상세보기</small></a>)}</div></AsyncState>
    </ConnectedLayout>;
}
export default MyCoursesPage;

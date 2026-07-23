import { useEffect, useState } from 'react';
import { ConnectedLayout, AsyncState } from '../../components/common/ConnectedLayout';
import { getMyTravelType } from '../../api/mypageApi';
import { authStore } from '../../store/authStore';

function MyTravelTypePage() {
    const member = authStore.getMember();
    const [type, setType] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    useEffect(() => { getMyTravelType(member.memberId).then(setType).catch((e) => setError(e.message)).finally(() => setLoading(false)); }, [member.memberId]);
    return <ConnectedLayout title="내 여행 유형" description="가장 최근 취향 검사 결과입니다." actions={<a href="/survey">다시 검사하기</a>}>
        <AsyncState loading={loading} error={error} empty={!type}>
            {type && <section className="connected-panel"><span className="connected-code">{type.travelCode}</span><h2>{type.typeTitle}</h2><p>{type.typeDescription}</p>{type.imageUrl && <img src={type.imageUrl} alt="" style={{ width: '100%', maxHeight: 360, objectFit: 'cover', borderRadius: 14, marginTop: 18 }} />}</section>}
        </AsyncState>
    </ConnectedLayout>;
}
export default MyTravelTypePage;

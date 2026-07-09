import PagePlaceholder from '../../components/common/PagePlaceholder';

function ChatbotPage() {
    return (
        <PagePlaceholder
            title="AI 여행 챗봇"
            description="결제한 사용자에게 컨셉형 서울 코스를 추천하는 챗봇 화면입니다."
            links={[{ href: '/payment', label: '이용권 결제' }]}
        />
    );
}

export default ChatbotPage;

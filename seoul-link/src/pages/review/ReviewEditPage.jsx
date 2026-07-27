import PagePlaceholder from '../../components/common/PagePlaceholder';
// 편집 폼이 구현되기 전까지도 사용자가 이동할 경로를 잃지 않도록 안내 화면을 제공한다.
function ReviewEditPage() {
  // 리뷰 수정 기능이 연결되기 전까지 목록 화면으로 이동할 수 있는 안내를 제공한다.
  return <PagePlaceholder title="후기 수정" description="내가 작성한 후기를 수정하는 화면입니다." links={[{
    href: '/reviews',
    label: '후기 목록으로'
  }]} />;
}
export default ReviewEditPage;

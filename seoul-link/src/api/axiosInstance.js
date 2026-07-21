/**
 * TODO: axios 공통 인스턴스가 필요해질 때 구현하는 파일입니다.
 *
 * 현재 API 요청은 `apiClient.js`의 fetch 기반 클라이언트를 사용하므로
 * 두 방식을 동시에 사용하지 않기 위해 이 파일에는 아직 실행 코드를 넣지 않습니다.
 * axios로 전환할 때는 아래 항목을 이곳에 한 번만 설정합니다.
 *
 * - `VITE_API_BASE_URL`을 사용하는 baseURL과 공통 timeout
 * - 요청 인터셉터에서 accessToken을 읽어 Authorization 헤더 추가
 * - 응답 인터셉터에서 401 처리, 토큰 재발급 또는 로그아웃 처리
 * - 서버 오류 응답을 화면에서 사용하기 쉬운 공통 에러 형태로 변환
 *
 * 주의: 카카오 REST 키, OpenRouteService 키, 소셜 로그인 Secret처럼
 * 노출되면 안 되는 값은 프론트 인스턴스에 넣지 않고 백엔드에서만 사용합니다.
 */

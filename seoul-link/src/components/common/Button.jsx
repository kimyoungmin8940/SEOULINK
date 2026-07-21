/**
 * TODO: 여러 화면에서 반복되는 버튼을 하나의 디자인 규칙으로 묶을 공통 컴포넌트입니다.
 *
 * 구현 시 권장 props:
 * - children: 버튼 문구
 * - variant: primary | secondary | danger | text
 * - size: small | medium | large
 * - type, disabled, onClick, className
 * - loading, leftIcon, rightIcon
 *
 * loading 중에는 중복 제출을 막고 스크린 리더가 상태를 알 수 있도록
 * disabled와 aria-busy를 함께 적용합니다. 페이지 이동은 Button 내부에서
 * 직접 처리하지 말고, 사용하는 화면에서 링크 또는 onClick으로 결정합니다.
 */

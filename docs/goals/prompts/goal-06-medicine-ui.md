# Goal 6 실행 프롬프트: React 약 관리 UI·UX

이 작업을 Goal로 생성하고 완료할 때까지 진행한다. [공통 고도화 원칙](common-principles.md)을 모두 적용한다.

## 목표

약 등록·수정·삭제 기능과 오류·로딩·인증 만료 처리를 완성하고 핵심 사용자 흐름 테스트를 추가한다.

## 브랜치

web 최신 `enhancement`에서 `feature/medicine-ui`를 만든다. backend 변경이 실제로 필요하면 같은 이름의 backend 브랜치를 만들되 UI 편의를 위한 불필요한 API 변경은 하지 않는다.

## 작업

- 약 수정 UI를 기존 backend 수정 API와 연결한다.
- DAILY, WEEKLY, INTERVAL 입력과 기존 값 편집을 지원한다.
- API 요청 중 중복 제출을 막는다.
- `alert` 중심 오류 처리를 화면 메시지 또는 toast로 개선한다.
- 삭제·수정 후 functional state update 또는 재조회 전략을 일관되게 적용한다.
- 빈 상태, 로딩, 네트워크 오류, 인증 만료 UI를 정리한다.
- 접근성과 모바일 레이아웃을 확인한다.
- Vitest와 React Testing Library로 주요 화면 테스트를 작성한다.
- Playwright는 로그인부터 약 등록·수정·삭제까지의 유지 가능한 E2E 가치가 있을 때만 추가한다.
- 테스트 데이터는 `yakmogo_enhancement`에서만 사용하고 종료 후 정리한다.

## 완료 조건

- web lint/build/test 성공
- 약 등록·수정·삭제 사용자 흐름 성공
- 중복 제출 방지 확인
- 인증 만료, API 오류, 빈 상태 표시 확인
- 모바일 크기의 주요 화면 검증
- 8081 배포와 실제 브라우저 검증 성공
- UI troubleshooting과 Goal 문서 갱신

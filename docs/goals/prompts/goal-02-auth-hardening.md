# Goal 2 실행 프롬프트: 클라이언트 독립적인 인증·인가

[공통 고도화 원칙](common-principles.md)을 모두 적용한다.

## 목표

아무 문자열이나 허용하던 `x-magic-token` 취약점을 제거하고, 만료 시간과 사용자 접근 범위가 있는 인증 체계를 구현한다.

## 브랜치

backend와 web의 최신 `enhancement`에서 `feature/auth-hardening`을 만든다. `main`은 변경하지 않는다.

## 작업

- Telegram 로그인 proof를 서버가 서명하고 검증한다.
- 로그인 proof와 access token의 목적을 구분한다.
- 토큰에 만료 시간과 허용 userId 범위를 적용한다.
- 관리자와 Telegram 사용자의 권한을 분리한다.
- 다른 사용자의 조회·수정·삭제·복용 완료를 차단한다.
- React의 chatId 직접 로그인과 임의 토큰 흐름을 제거한다.
- 401 인증 실패와 403 권한 부족을 구분한다.
- token과 비밀번호 원문을 로그에 남기지 않는다.
- 인증된 주체는 `principalType`, `allowedUserIds`, `admin`만 제공하는 최소 표현으로 둔다.
- 범용 권한 프레임워크, Android 전용 주체, Device·Pairing API를 만들지 않는다.
- 성공, 실패, 만료, 변조, 권한 범위 테스트를 작성한다.

## 완료 조건

- 임의 token 401
- 만료·변조 token 401
- 로그인 proof를 access token으로 사용할 수 없음
- 정상 token은 허용 userId에만 접근 가능
- 범위 밖 userId의 조회·변경 403 및 상태 불변
- backend test/bootJar 성공
- web lint/build 성공
- 8081 통합 검증 성공
- 민감값 비노출 및 문서 갱신

## 상태

완료. 실제 기록은 [Goal 2 문서](../goal-02-auth-hardening.md)를 참고한다.

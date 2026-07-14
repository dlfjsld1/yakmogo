# Goal 5 실행 프롬프트: 백엔드 API 테스트·검증·상태 전이

이 작업을 Goal로 생성하고 완료할 때까지 진행한다. [공통 고도화 원칙](common-principles.md)을 모두 적용한다.

## 목표

사용자, 보호자, 약, 복용 기록 API의 서비스·Controller 테스트와 입력 검증을 구축하고 복용 상태 변경 규칙을 단일 진입점으로 통합한다.

## 브랜치

backend 최신 `enhancement`에서 `feature/backend-test-suite`를 만든다.

## 작업

- 사용자 등록·조회·삭제 성공·실패 테스트를 작성한다.
- 보호자 추가·삭제와 다른 사용자 보호자 삭제 방지를 테스트한다.
- 약 등록·수정·중단을 테스트한다.
- DAILY, WEEKLY, INTERVAL 계산을 테스트한다.
- 복용 완료 중복 처리와 잘못된 상태 전이를 검증한다.
- DTO에 Bean Validation을 적용한다.
- 오류 응답을 일관된 JSON 형식으로 정리한다.
- 잘못된 입력 400, 미존재 404, 인증 실패 401, 권한 부족 403을 구분한다.
- H2와 MariaDB 차이로 테스트가 잘못 통과하지 않는지 확인한다.
- 기존 API 클라이언트 호환성을 확인한다.

## 공통 상태 전이

```text
REST controller ─────┐
                     ├─> IntakeCommandService.complete()
Telegram callback ──┘
```

조회와 상태 변경 명령을 구분하고 상태를 바꾸는 규칙만 단일 진입점으로 모은다. 새 클래스가 현재 코드 규모에 과하면 기존 `IntakeLogService`의 단일 메서드로 수렴해도 된다.

## 완료 조건

- 핵심 API의 성공·입력 오류·미존재·권한 실패 테스트 보유
- 복용 완료 중복과 잘못된 상태 전이 차단
- REST와 Telegram이 같은 상태 변경 규칙 사용
- 다른 사용자 데이터 상태 불변 확인
- backend 전체 테스트와 CI 성공
- 8081에서 비파괴 통합 테스트 성공
- API·상태 전이 troubleshooting 및 Goal 문서 갱신

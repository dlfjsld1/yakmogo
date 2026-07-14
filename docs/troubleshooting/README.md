# 약모고 트러블슈팅 기록

이 디렉터리는 최종 코드만 보고는 알기 어려운 실패 과정과 판단 변경을 기록한다. 단순 오류 목록이 아니라 같은 문제가 다시 발생했을 때 원인에 도달하는 방법을 설명하는 학습 자료다.

## 문서 원칙

- Git 커밋, 테스트 결과, CI 실행, 배포 로그처럼 확인 가능한 근거만 사실로 기록한다.
- 당시 의도를 확인할 수 없으면 `당시 의도 확인 불가`, 가능성만 말할 때는 `원인 추정`이라고 표시한다.
- 토큰, 비밀번호, Chat ID와 같은 민감값은 항상 `<redacted>`로 표기한다.
- 최종 해결책뿐 아니라 실제로 실패한 접근과 코드 작성 전에 검토 후 제외한 대안을 구분한다.
- Goal 범위 밖에서 발견한 문제는 임의로 수정하지 않고 후속 Goal로 넘긴다.

## 주제별 문서

- [인증과 인가](authentication.md): 임의 토큰 허용, chatId 직접 로그인, HMAC 토큰 구현 과정
- [배포와 런타임](deployment-and-runtime.md): 운영·고도화 환경 분리, 준비 상태 확인, 알림 격리, 비밀정보 분리
- [CI/CD](ci-cd.md): 기본 CI 도입과 오래된 `origin/main` 기준선 문제
- [DB 마이그레이션](database-migration.md): H2와 MariaDB 차이, 권한 출력, Flyway baseline의 한계
- [API 검증과 복용 상태 전이](api-validation-and-state-transition.md): Bean Validation, orphan removal, 일정 계산, 동시 완료 잠금
- [UI 테스트와 8081 배포](ui-testing-and-deployment.md): Vitest 격리, Windows 실행·인코딩, 통합 JAR, 브라우저 삭제 확인
- [알림 전달과 재시도](notification-reliability.md): 전달 상태, backoff, 중복 방지, 날짜 경계, 실제 메시지 없는 검증

## Goal별 문서

- [Goal 1: 품질 기반과 고도화 환경 격리](../goals/goal-01-quality-hardening.md)
- [Goal 2: 인증·인가 강화](../goals/goal-02-auth-hardening.md)
- [Goal 3: 런타임 설정과 비밀정보 분리](../goals/goal-03-runtime-config.md)
- [Goal 4: Flyway 기반 DB 마이그레이션](../goals/goal-04-database-migration.md) — 완료
- [Goal 5: 백엔드 API 테스트·검증·상태 전이](../goals/goal-05-backend-test-suite.md) — 완료
- [Goal 6: React 약 관리 UI·UX](../goals/goal-06-medicine-ui.md) — 완료
- [Goal 7: 알림 신뢰성 고도화](../goals/goal-07-notification-reliability.md) — 병합 승인 대기

## 빠르게 찾는 법

1. HTTP `401`·`403`, Telegram 링크 문제는 `authentication.md`를 확인한다.
2. 서비스는 active인데 접속이 안 되거나 8080·8081이 혼동되면 `deployment-and-runtime.md`를 확인한다.
3. 브랜치에 CI가 실행되지 않거나 enhancement 기준선이 이상하면 `ci-cd.md`를 확인한다.
4. API 400·404 구분, validation, 복용 완료 중복, 보호자 삭제는 `api-validation-and-state-transition.md`를 확인한다.
5. React 테스트 격리, Windows npm·sudo 인코딩, 8081 통합 UI 배포는 `ui-testing-and-deployment.md`를 확인한다.
6. Telegram 재시도, 알림 중복, 날짜 경계, 실제 메시지 없는 검증은 `notification-reliability.md`를 확인한다.

## 새 사례 추가 기준

다음 질문에 답할 근거가 있을 때 사례를 추가한다.

1. 무엇을 하려 했는가?
2. 어떤 증상이 실제로 관찰됐는가?
3. 무엇을 확인해 원인을 확정했는가?
4. 어떤 수정과 검증으로 해결했는가?
5. 자동 테스트나 절차로 어떻게 재발을 막았는가?

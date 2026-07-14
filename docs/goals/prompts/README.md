# 약모고 1차 고도화 Goal 실행 프롬프트

이 디렉터리는 대화에서 합의한 Goal 1~10의 실행 기준을 보존한다. 최초 초안뿐 아니라 이후 검토에서 확정한 브랜치 예외, 최소 인증 모델, Flyway 사전 비교, 공통 상태 전이, Android 2차 분리 원칙을 모두 반영했다.

Goal을 실행할 때는 먼저 [공통 고도화 원칙](common-principles.md)을 전부 읽고 해당 Goal 파일을 사용한다. 완료된 Goal의 실제 구현·실패·검증 기록은 상위 `docs/goals`와 `docs/troubleshooting` 문서를 함께 확인한다.

## 진행 현황

| Goal | 실행 프롬프트 | 상태 | 실행 기록 |
|---:|---|---|---|
| 1 | [품질 개선 통합 및 기본 CI](goal-01-quality-hardening.md) | 완료 | [기록](../goal-01-quality-hardening.md) |
| 2 | [클라이언트 독립적인 인증·인가](goal-02-auth-hardening.md) | 완료 | [기록](../goal-02-auth-hardening.md) |
| 3 | [런타임 설정과 비밀정보 분리](goal-03-runtime-config.md) | 완료 | [기록](../goal-03-runtime-config.md) |
| 4 | [Flyway 기반 DB 마이그레이션](goal-04-database-migration.md) | 완료 | [완료 보고](../goal-04-database-migration.md) |
| 5 | [백엔드 API 테스트·검증·상태 전이](goal-05-backend-test-suite.md) | 진행 중 | [구현 기록](../goal-05-backend-test-suite.md) |
| 6 | [React 약 관리 UI·UX](goal-06-medicine-ui.md) | 대기 | 실행 전 |
| 7 | [스케줄러·Telegram 알림 신뢰성](goal-07-notification-reliability.md) | 대기 | 실행 전 |
| 8 | [CI/CD와 8081 자동 배포·롤백](goal-08-ci-cd.md) | 대기 | 실행 전 |
| 9 | [관측성·백업·복원](goal-09-operations.md) | 대기 | 실행 전 |
| 10 | [전체 검증과 main 병합 준비](goal-10-release-readiness.md) | 대기 | 실행 전 |

권장 순서는 `1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9 → 10`이다.

## 사용 방법

1. 현재 저장소와 Raspberry Pi 상태를 다시 읽어 기준선을 확인한다.
2. [공통 고도화 원칙](common-principles.md)을 작업 상위 지침으로 적용한다.
3. 해당 Goal 파일의 목표·범위·완료 조건으로 Goal을 생성한다.
4. 구현, 테스트, 8081 검증, 문서 갱신까지 완료한다.
5. 검증된 feature만 `enhancement`에 통합한다.
6. Goal 10의 사용자 승인 전까지 `main`과 운영 8080을 변경하지 않는다.

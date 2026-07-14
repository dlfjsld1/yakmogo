# Goal 7 실행 프롬프트: 스케줄러·Telegram 알림 신뢰성

이 작업을 Goal로 생성하고 완료할 때까지 진행한다. [공통 고도화 원칙](common-principles.md)을 모두 적용한다.

## 목표

복용 기록 생성과 Telegram 알림이 중복되거나 누락되지 않도록 스케줄러, 복용 상태 전이, 알림 정책의 경계를 정리한다.

## 브랜치

backend 최신 `enhancement`에서 `feature/notification-reliability`를 만든다.

## 작업

- 중복된 일일 복용 기록 생성 로직을 하나로 통합한다.
- 스케줄 계산을 외부 API와 분리된 순수 함수로 만들고 단위 테스트를 작성한다.
- DAILY, WEEKLY, INTERVAL과 월·연도·요일·자정 경계를 테스트한다.
- 동일 복용 기록과 동일 알림의 중복 생성을 방지한다.
- Telegram API 실패 시 재시도 횟수, backoff, 제한, 상태 기록 정책을 정한다.
- 외부 Telegram API는 테스트에서 mock 처리한다.
- 고도화 환경에서는 실제 Telegram 메시지를 발송하지 않는다.
- 실제 봇 검증이 꼭 필요하면 별도 테스트 bot token과 chat ID 사용 전 사용자 승인을 요청한다.

## 경계

```text
REST controller ─────┐
                     ├─> IntakeCommandService.complete()
Telegram callback ──┘

Scheduler → 알림 정책 → Telegram 전달
```

- Telegram callback이 복용 상태 규칙을 중복 구현하지 않게 한다.
- 알림 시점과 대상 결정은 Telegram SDK 호출에서 분리한다.
- 현재는 Telegram 전달만 구현하고 FCM 구현체나 범용 메시징 프레임워크를 만들지 않는다.

## 완료 조건

- 스케줄 계산과 날짜 경계 테스트 성공
- 복용 기록과 알림 중복 방지 테스트 성공
- REST와 Telegram의 상태 변경 경로 통합
- Telegram 실패가 DB 트랜잭션을 비정상 상태로 만들지 않음
- 고도화 환경 실제 메시지 0건
- backend 전체 테스트와 CI 성공
- 8081 bot·scheduler 안전 설정 유지
- 알림 troubleshooting과 Goal 문서 갱신

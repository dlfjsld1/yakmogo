# Goal 7: 알림 신뢰성 고도화

## 한 문장 요약

**구현·로컬 검증·고도화 DB V2 적용·8081 인수 검증 완료, `enhancement` 병합 승인 대기.** 일일 복약 기록 생성을 한 서비스로 통합하고, Telegram 알림을 복약 기록·보호자·알림 단계별 전달 상태로 저장해 실패 시 1분·5분 간격으로 최대 3회까지만 재시도하도록 만들었다.

## 작업 배경

기존 코드는 일일 복약 기록을 `MedicineGroupService`와 `SchedulerService` 두 곳에서 만들었다. 두 구현이 조금씩 달라지면 일정 정책을 수정할 때 한쪽만 바뀔 수 있었고, 애플리케이션 수준의 `exists` 조회만으로는 동시 실행 중 중복 생성을 완전히 막을 수 없었다.

Telegram 알림은 `SchedulerService`가 `notifiedCount`를 먼저 판단하고 `TelegramService`를 호출하는 구조였다. `TelegramService`가 HTTP 예외를 내부에서 삼켰기 때문에 실제 전송이 실패해도 스케줄러는 성공으로 오인해 `notifiedCount`를 증가시켰다. 실패한 알림을 언제 다시 보낼지, 몇 번 시도했는지, 최종 성공했는지를 DB에서 설명할 수도 없었다.

## 변경 전 문제

- 일일 복약 기록 생성 코드가 두 서비스에 중복되어 있었다.
- `(medicine_group_id, intake_date)`를 DB가 유일하게 보장하지 않았다.
- Telegram HTTP 실패와 성공을 호출자가 구분할 수 없었다.
- 실패한 알림의 재시도 시각·횟수·최종 상태가 저장되지 않았다.
- 분 경계에서 이전 알림 재시도와 다음 단계 알림이 한 번에 발송될 가능성이 있었다.
- `RestTemplate`을 서비스 내부에서 직접 생성해 네트워크 없는 단위 테스트가 어려웠다.
- 시간 계산이 `LocalDate.now()`에 직접 묶여 자정·월말·윤년 검증이 불편했다.

## 이번 Goal 범위

- 일일 복약 기록 생성 경로를 `DailyIntakeLogService.generateFor(date)`로 통합
- 일정 정책의 DAILY·WEEKLY·INTERVAL 및 날짜 경계 테스트 확장
- 복약 기록 DB 유일 제약 추가
- 알림 전달 상태와 재시도 이력을 저장하는 `notification_delivery` 추가
- Telegram 성공·실패 결과를 호출자에게 반환
- 실패 시 제한된 backoff 재시도와 최종 소진 상태 구현
- 같은 알림 단계와 분 경계의 중복 발송 방지
- `Clock`과 주입 가능한 `RestTemplate`로 네트워크 없는 결정적 테스트 구성
- 고도화 DB `yakmogo_enhancement`에 Flyway V2 적용
- Telegram bot·scheduler가 꺼진 8081에서 실제 메시지 없이 인수 검증

## 범위에서 제외한 내용

- Android, Device, Pairing, FCM, Android 동기화 API와 테이블
- Telegram 외 채널을 위한 범용 알림 프레임워크
- 운영 DB `yakmogo`의 Flyway 도입 또는 V2 적용
- 운영 8080 배포·재시작
- `main` 또는 `enhancement` 병합
- Goal 8의 자동 배포·자동 롤백
- Telegram API가 원격 수락한 직후 로컬 트랜잭션이 실패하는 극단적 구간을 해결하는 외부 idempotency 체계

## 도입한 기술과 개념

### 전달 상태를 별도 행으로 저장하는 이유

`notifiedCount` 하나만으로는 어느 보호자에게 어떤 단계의 알림을 몇 번 시도했는지 알 수 없다. 새 `notification_delivery` 행은 다음 조합을 하나의 전달 대상으로 본다.

```text
intake_log_id + guardian_id + alert_key
```

`alert_key`는 `ON_TIME`, `OVERDUE_30_MINUTES`, `OVERDUE_1_HOUR`, `OVERDUE_2_HOURS`처럼 내용이 바뀌는 단계를 나타낸다. DB 유일 제약이 같은 대상을 두 번 만드는 것을 막는다.

상태 흐름은 다음과 같다.

```text
PENDING
  ├─ 성공 ───────────────> SENT
  └─ 실패 1회 ─1분 대기─> RETRY_WAIT
                         ├─ 성공 ─────> SENT
                         └─ 실패 2회 ─5분 대기─> RETRY_WAIT
                                               ├─ 성공 ─> SENT
                                               └─ 실패 3회 ─> EXHAUSTED
```

`attempt_count`는 실제 HTTP 시도 횟수다. 성공하면 `sent_at`, 실패하면 예외 클래스 이름만 `last_error`에 저장한다. URL, token, 응답 본문처럼 민감할 수 있는 예외 메시지는 저장하지 않는다.

### 순수 알림 정책

`MedicationAlertPolicy`는 DB 저장이나 Telegram SDK를 모른다. 복약 기록과 현재 시각을 받아 현재 단계만 계산한다.

| 경과 시간 | 단계 | key |
|---|---|---|
| 0~29분 | 정시 알림 | `ON_TIME` |
| 30~59분 | 30분 경과 | `OVERDUE_30_MINUTES` |
| 60~119분 | 1시간 경과 | `OVERDUE_1_HOUR` |
| 120~360분 | 시간 단위 반복 | `OVERDUE_2_HOURS`~`OVERDUE_6_HOURS` |
| 음수, 다른 날짜, 360분 초과 | 발송 없음 | 없음 |

스케줄러가 정확히 경계 분에 실행되지 않아도 해당 구간 안에서 아직 보내지 않은 단계가 있으면 한 번 보낸다. 이미 `SENT`인 단계는 DB 기록 때문에 다시 보내지 않는다.

### 애플리케이션 시간 주입

`TimeConfig`가 시스템 시간대의 `Clock`을 제공한다. 운영 동작은 기존 시스템 시각과 같지만 테스트는 고정된 시각을 주입할 수 있다. 따라서 23:59·자정, 월말, 연말, 윤년 날짜를 기다리지 않고 검증할 수 있다.

### Telegram은 전달 어댑터

Telegram 메시지 형식과 HTTP 호출은 `TelegramService`에 남겼다. 스케줄러는 `TelegramDeliveryResult`의 성공 여부만 보고 전달 상태를 바꾼다. 이 경계 덕분에 향후 다른 채널이 필요해도 복약 상태 전이나 일정 정책을 Telegram callback 전용 코드로 복제할 필요가 없다. 이번 Goal에서는 사용하지 않는 채널 인터페이스나 FCM 구현은 만들지 않았다.

## 선택한 방식과 대안

### 선택: 작은 전달 엔티티와 고정 재시도 정책

현재 단일 라즈베리파이 규모에서는 메시지 브로커, 분산 작업 큐, 범용 workflow 엔진보다 DB 행과 1분 스케줄러가 단순하고 관찰 가능하다. 재시도 횟수와 간격도 설정 프레임워크로 일반화하지 않고 요구된 `1분 → 5분 → 종료`, 최대 3회로 코드에 명확히 고정했다.

### 선택하지 않음: `notifiedCount`만 계속 사용

단일 정수는 보호자별 성공 여부와 알림 단계별 재시도를 표현할 수 없다. 기존 컬럼은 V1 및 기존 데이터 호환을 위해 제거하지 않았지만 Goal 7 알림 판단에는 사용하지 않는다.

### 선택하지 않음: 즉시 반복 재시도

한 scheduler tick 안에서 연속 호출하면 Telegram 장애 시 외부 API를 더 압박하고 지연이 길어진다. 실패 상태를 커밋하고 다음 tick에서 due time이 지난 경우에만 재시도한다.

### 선택하지 않음: 미래 Android용 테이블과 채널

현재 사용하지 않는 API·테이블·어댑터를 미리 만들면 실제 요구 없이 스키마와 인증 경계가 굳어진다. 공통 복약 상태 전이는 Goal 5의 `IntakeCommandService.complete()`를 그대로 유지하고, 이번에는 실제 Telegram 전달 신뢰성만 개선했다.

## 실제 동작 흐름

### 일일 복약 기록 생성

```text
SchedulerService.generateDailyLogs()
  -> DailyIntakeLogService.generateFor(today)
     -> 활성 MedicineGroup 조회
     -> MedicineSchedulePolicy.shouldTakeOn(group, date)
     -> 기존 기록 존재 확인
     -> IntakeLog 저장
     -> DB unique(medicine_group_id, intake_date)가 최종 중복 방어
```

### 알림 발송과 재시도

```text
SchedulerService.checkMissedDose()
  -> due RETRY_WAIT 전달을 먼저 조회
  -> 이전 단계 재시도
  -> 같은 복약·보호자는 이번 tick의 새 단계 발송에서 제외
  -> 현재 PENDING 복약 기록의 MedicationAlert 계산
  -> 전달 행 조회 또는 생성
  -> TelegramService.sendMedicationAlert()
  -> 성공: SENT / 실패: RETRY_WAIT 또는 EXHAUSTED
```

이전 단계 재시도가 30분·1시간 같은 새 단계 경계와 겹치면 이전 단계만 처리한다. 다음 scheduler tick에서 새 단계를 평가하므로 한 번의 실행에서 두 메시지가 연속 발송되지 않는다.

### 복약 완료 상태 전이

Goal 5에서 만든 공통 경계를 바꾸지 않았다.

```text
REST controller ─────┐
                     ├─> IntakeCommandService.complete()
Telegram callback ──┘
```

따라서 알림 발송 정책 변경이 완료 상태 전이를 Telegram callback 전용 로직으로 되돌리지 않는다.

## 변경된 파일과 역할

| 파일 | 역할 |
|---|---|
| `db/migration/V2__notification_delivery_reliability.sql` | 복약 유일 제약과 전달 테이블 생성 |
| `NotificationDelivery`, `NotificationDeliveryStatus` | 전달 상태·시도 횟수·backoff 상태 전이 |
| `NotificationDeliveryRepository` | 대상별 전달 조회와 due retry 조회 |
| `MedicationAlert`, `MedicationAlertPolicy` | 현재 알림 단계의 순수 계산 |
| `DailyIntakeLogService` | 일일 복약 기록 생성 단일 경계 |
| `SchedulerService` | 일일 생성 위임, due retry 우선 처리, 전달 결과 기록 |
| `TelegramService`, `TelegramDeliveryResult` | 주입 가능한 HTTP 호출과 성공·실패 반환 |
| `TimeConfig`, `HttpClientConfig` | `Clock`, `RestTemplate` Bean 제공 |
| `IntakeLog` | DB와 일치하는 복약 유일 제약 선언 |
| 일정·전달·스케줄러·Telegram 테스트 | 날짜 경계, 중복, backoff, 네트워크 격리 검증 |
| Flyway 테스트 | V2와 MariaDB 고유 제약·FK·전달 유일키 검증 |

## 데이터 및 설정 변경

### Flyway V2

`yakmogo_enhancement`에 다음이 적용됐다.

- `intake_log.uk_intake_log_medicine_date`
- `notification_delivery` 테이블
- `uk_notification_delivery_target`
- `intake_log`, `guardian` 외래키와 `ON DELETE CASCADE`

적용 전 운영·고도화 DB 모두 복약 중복 그룹이 0건임을 SELECT로 확인했다. 운영 DB에는 V2, Flyway history, `notification_delivery`를 적용하지 않았다.

새 환경변수는 없다. 8081의 기존 설정을 유지했다.

```text
SPRING_PROFILES_ACTIVE=enhancement
server.port=8081
database=yakmogo_enhancement
telegram.bot.enabled=false
scheduling.enabled=false
```

## 시행착오와 문제 해결 과정

세부 과정은 [알림 전달·재시도 트러블슈팅](../troubleshooting/notification-reliability.md)에 기록했다.

1. 새 타입을 만들기 전 테스트를 먼저 추가해 컴파일 실패를 확인했다. 요구한 경계가 아직 존재하지 않는 정상적인 RED 단계였다.
2. 첫 전체 통합 테스트에서 같은 약·같은 날짜로 `PENDING`과 `MISSED`를 만드는 fixture가 새 유일 제약에 걸렸다. 실제 도메인 규칙에 맞게 `MISSED` 기록을 전날로 옮겼다.
3. Windows PowerShell의 script execution policy로 `npm`이 막혀 `npm.cmd`를 사용했다.
4. sandbox가 Vite의 상위 경로 탐색을 막아 권한이 허용된 동일 빌드를 다시 실행했다.
5. 첫 DB 점검 스크립트는 DB URL과 사용자명도 root 환경 파일에 있다고 가정해 `root@localhost`로 접속하려 했다. Goal 3의 실제 경계에 맞춰 공개 설정은 애플리케이션 YAML, 비밀번호만 환경 파일에서 읽도록 수정했다. DB 변경 전 실패였다.
6. PowerShell과 원격 shell을 한 명령에서 함께 인용하며 종료 코드를 보존하려던 시도가 따옴표 오류로 실행되지 않았다. 단순한 원격 실행·정리 순서로 바꿨다.
7. 8081 재기동 중 연결 거부가 약 33초 이어졌지만 60초 polling 안에 정상 준비됐다. systemd active만으로 성공 처리하지 않고 HTTP 200까지 기다린 이유를 확인했다.

## 테스트와 보호 시나리오

로컬 `clean test bootJar` 결과:

- 테스트 34개
- 성공 33개
- 실패 0개
- 건너뜀 1개: `MARIADB_TEST_URL`이 있을 때만 실행하는 MariaDB 전용 테스트
- 실행 JAR 생성 성공

핵심 자동 테스트:

- DAILY·WEEKLY·INTERVAL 일정과 월말·연말·윤년·시작일 이전
- 같은 약·같은 날짜 생성 방지
- 정시·30분·1시간·2~6시간·다른 날짜·6시간 초과 알림 정책
- 실패 1회 후 1분, 실패 2회 후 5분, 3회 후 `EXHAUSTED`
- 성공 후 같은 단계 재발송 없음
- 이전 단계 retry와 새 단계 경계가 겹칠 때 한 메시지만 처리
- mock `RestTemplate`을 사용한 Telegram 성공·실패, 실제 네트워크 0회
- 빈 스키마 Flyway V1→V2, 반복 migrate 0건
- 실제 MariaDB에서 FK·복약 unique·전달 unique 검증(CI 환경)
- 기존 REST와 Telegram callback의 공통 `IntakeCommandService` 회귀 테스트

컴파일 경고 두 개는 기존 Telegram API deprecation과 Mockito 제네릭 unchecked 경고다. 테스트 실패는 아니며 이번 Goal 범위 밖 API 교체는 하지 않았다.

feature CI 결과:

- 구현 커밋: `c4496c2`
- Backend CI: [run 29314585209](https://github.com/dlfjsld1/yakmogo/actions/runs/29314585209) 성공
- Ubuntu Java 21 전체 backend 검증 성공
- MariaDB container 초기화와 실제 migration 테스트 성공
- `Confirm MariaDB migration test ran` 단계 성공

## 배포 및 검증

2026-07-14에 Goal 6 웹 `dist`를 Goal 7 backend JAR의 정적 리소스에 포함해 고도화 8081에만 수동 배포했다.

| 항목 | 결과 |
|---|---|
| 후보 JAR SHA-256 | `e39406a64cd96b46851319d58eddaf34bf23be8c6c5d2e67ebc926755fa3df32` |
| 백업 | `/home/pi/myprojects/yakmogo-enhancement/backup-goal7-20260714-162049` |
| DB dump | `yakmogo_enhancement-before-v2.sql`, 6,982 bytes, mode 600 |
| Flyway | version 2 |
| 새 전달 행 | 0 |
| 복약 unique | 존재 |
| 8081 | active, HTTP 200 |
| 8081 무인증 API | HTTP 401 |
| Goal 6 웹 bundle | 유지 확인 |
| Telegram 전달 로그 | 0 |
| scheduler 실행 로그 | 0 |
| 8080 | active, HTTP 200, PID·JAR checksum 불변 |
| 운영 DB | 새 테이블 0, Flyway history 0 |

기동 준비에는 약 33초가 걸렸다. 최대 60초 동안 systemd active와 HTTP 200을 함께 polling했다.

## 운영 시 주의 사항

- 8081은 계속 bot과 scheduler를 비활성화한다. 이 Goal 검증 중 실제 Telegram 메시지는 보내지 않았다.
- 운영에 V2를 적용하기 전 복약 중복 그룹 0건을 다시 확인해야 한다.
- `last_error`에는 예외 클래스만 저장한다. 디버깅을 이유로 token이 포함될 수 있는 전체 URL이나 응답을 저장하지 않는다.
- `EXHAUSTED`는 자동 재시도 종료 상태다. Goal 9 관측성에서 이 상태를 운영자가 발견할 방법을 연결해야 한다.
- 기존 `notified_count`는 호환 목적으로 남아 있지만 새 알림 흐름의 근거로 사용하지 않는다.

## 직접 확인하는 방법

```bash
./gradlew clean test bootJar
```

고도화 DB에서:

```sql
SELECT version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;

SHOW CREATE TABLE notification_delivery;

SELECT status, COUNT(*)
FROM notification_delivery
GROUP BY status;
```

서비스에서:

```bash
systemctl is-active yakmogo yakmogo-enhancement
curl -sS -o /dev/null -w '%{http_code}\n' http://localhost:8080/
curl -sS -o /dev/null -w '%{http_code}\n' http://localhost:8081/
curl -sS -o /dev/null -w '%{http_code}\n' http://localhost:8081/api/v1/users
```

환경 파일과 token 값은 출력하지 않는다.

## 알려진 한계

- Telegram `sendPhoto` API에는 이 애플리케이션이 제어하는 idempotency key가 없다. Telegram이 메시지를 수락한 직후 DB commit 전에 프로세스가 죽으면 다음 재시도가 중복 메시지를 만들 수 있다. 정상 실행·일반 HTTP 실패·같은 scheduler 반복은 DB 유일키와 `SENT` 상태로 막는다.
- 다중 애플리케이션 인스턴스가 동시에 같은 새 전달 행을 만들면 unique constraint가 중복 저장은 막지만 한 트랜잭션이 constraint 예외로 끝날 수 있다. 현재 단일 8080/단일 8081 구조에는 해당하지 않는다. 수평 확장 시 claim/locking 설계가 필요하다.
- `EXHAUSTED` 운영 알람과 재처리 UI는 없다. Goal 9 관측성 후보로 넘긴다.
- 서버의 실제 `ExecStart` 표시가 문서의 직접 `java -jar` 예시와 달리 `start.sh`를 가리킨다. 스크립트에 비밀 인자가 없고 서비스는 정상이나, Goal 7과 무관한 런타임 문서 정합성 후보로 기록하고 수정하지 않았다.
- backend README의 초기 DB 설명 일부가 현재 `ddl-auto=validate`·Flyway 방식과 오래된 문구를 포함한다. Goal 7 범위에서 임의 수정하지 않고 릴리스 문서 정리 후보로 남긴다.

## 후속 Goal에 넘길 내용

- Goal 8: 통합 JAR 생성, checksum, 최대 60초 health polling, 8081 자동 rollback을 CI/CD로 옮긴다.
- Goal 9: `RETRY_WAIT` 장기 체류와 `EXHAUSTED` 건수 관측, DB 백업·복원 절차를 운영 설계에 포함한다.
- Goal 10: 운영 적용 전 중복 데이터 재점검, V2 migration dry-run, Telegram 실제 발송 승인 범위를 최종 릴리스 보고서에 포함한다.
- Android 2차 로드맵: 현재 공통 `IntakeCommandService`와 채널 비종속 알림 정책 경계를 평가하되 Android 코드·API·테이블은 이번 단계에 추가하지 않는다.

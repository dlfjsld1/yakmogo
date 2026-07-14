# 알림 전달과 재시도 트러블슈팅

이 문서는 Goal 7에서 일일 복약 생성 중복과 Telegram 실패 오인을 해결하며 실제로 겪은 문제를 재현 가능한 판단 과정으로 정리한다.

## 1. Telegram 실패인데 알림 횟수는 증가하는 문제

### 증상

기존 `TelegramService.sendPhotoWithButton()`은 HTTP 예외를 catch하고 로그만 남긴 뒤 정상 반환했다. 호출자인 `SchedulerService`는 실패 여부를 알 수 없어 `notifiedCount`를 증가시켰다.

### 원인 확인

호출 흐름을 따라가면 성공·실패를 표현하는 반환값이나 예외가 서비스 경계를 넘지 않았다.

```text
Scheduler -> TelegramService -> RestTemplate
                    실패 catch 후 정상 return
Scheduler -> notifiedCount 증가
```

### 해결

- `TelegramDeliveryResult`로 성공 여부만 반환
- 실패 메시지가 아닌 예외 클래스 이름만 반환·저장
- scheduler가 결과를 보고 `SENT`, `RETRY_WAIT`, `EXHAUSTED`를 기록
- mock `RestTemplate`로 성공·실패 테스트

### 재발 방지 확인

`TelegramServiceTest`는 네트워크 없이 HTTP 성공과 예외를 각각 만들고 결과를 검증한다. `SchedulerServiceTest`는 실패 뒤 backoff와 성공 후 재발송 방지를 검증한다.

## 2. 애플리케이션의 exists 조회만으로는 중복 생성 방어가 끝나지 않는다

### 문제

두 실행이 거의 동시에 다음 순서를 수행할 수 있다.

```text
A: exists=false
B: exists=false
A: insert
B: insert
```

### 해결

일일 생성 코드는 `DailyIntakeLogService` 한 곳으로 모았고, DB에 다음 유일 제약을 추가했다.

```sql
UNIQUE (medicine_group_id, intake_date)
```

서비스의 exists 조회는 불필요한 insert 시도를 줄이고, DB 제약은 경쟁 조건의 최종 방어선이다.

### 적용 전 확인

유일 제약은 기존 중복 데이터가 있으면 migration 전체를 실패시킨다. 그래서 운영과 고도화 DB에서 다음 집계의 결과가 모두 0인지 읽기 전용으로 확인했다.

```sql
SELECT medicine_group_id, intake_date, COUNT(*)
FROM intake_log
GROUP BY medicine_group_id, intake_date
HAVING COUNT(*) > 1;
```

운영 DB에는 migration을 적용하지 않았다.

## 3. 새 DB 제약이 기존 통합 테스트 fixture를 실패시킴

### 증상

첫 전체 `clean test bootJar`에서 API 통합 테스트 하나가 unique constraint 위반으로 실패했다.

### 원인

테스트가 같은 약과 같은 날짜에 `PENDING` 기록과 `MISSED` 기록을 각각 만들었다. 이전 스키마에서는 허용됐지만 실제 도메인에서는 하루 한 약의 복약 기록이 하나여야 한다.

### 해결

테스트가 표현하려던 것은 상태별 집계였으므로 `MISSED` 기록 날짜를 전날로 옮겼다. production 코드를 약화하거나 constraint를 제거하지 않았다.

### 판단 기준

새 제약 때문에 테스트가 깨졌을 때 다음을 구분한다.

- 테스트가 현실적으로 가능한 데이터를 만들었는가?
- 제약이 요구사항보다 강한가?
- fixture 날짜·사용자·약을 분리해 같은 의도를 표현할 수 있는가?

이번 경우는 fixture가 새 도메인 불변식을 위반했다.

## 4. retry와 다음 알림 단계가 같은 분에 겹침

### 위험

정시 알림 재시도가 09:30에 due가 되고 현재 정책도 30분 경과 알림을 선택하면 한 scheduler tick에서 두 메시지가 갈 수 있다.

### 해결

due retry를 먼저 처리하고 해당 복약 기록·보호자 조합을 이번 tick의 새 단계 처리에서 제외한다.

```text
09:30 tick
  1. ON_TIME retry 처리
  2. 같은 log+guardian을 retriedTargets에 기록
  3. OVERDUE_30_MINUTES 신규 발송은 다음 tick으로 미룸
```

`retriesPreviousStageAtBoundaryWithoutSendingNewStageInSameRun` 테스트가 Telegram 호출 1회와 새 단계 조회 0회를 검증한다.

## 5. 시간을 직접 읽으면 날짜 경계 테스트가 불안정함

### 문제

`LocalDate.now()`와 `LocalTime.now()`를 메서드 안에서 직접 사용하면 테스트 시각을 고정할 수 없다. 자정 직전 날짜와 자정 직후 시간이 섞일 가능성도 있다.

### 해결

하나의 `Clock`에서 `LocalDateTime`을 한 번 읽고 날짜와 시간을 나눈다. 테스트는 `Clock.fixed()`를 사용한다.

검증 범위:

- 23:59와 자정
- 월말과 다음 달
- 12월 31일과 1월 1일
- 2월 29일 윤년
- INTERVAL 시작일 이전과 정확한 배수 날짜

## 6. Windows에서 `npm` 실행 실패

### 증상

```text
npm.ps1 cannot be loaded because running scripts is disabled
```

### 원인

PowerShell execution policy가 `npm.ps1` 실행을 막았다. Node 또는 프로젝트 오류가 아니었다.

### 해결

동일 npm 설치의 실행 파일인 `npm.cmd run build`를 사용했다. 시스템 execution policy를 변경하지 않았다.

## 7. Vite가 config를 읽지 못함

### 증상

```text
Cannot read directory "../..": Access is denied
Could not resolve vite.config.ts
```

### 원인

격리 실행 환경에서 esbuild가 config 해석 중 상위 경로를 읽는 동작이 제한됐다.

### 해결

같은 `npm.cmd run build`를 허용된 작업공간 접근으로 다시 실행했다. 소스나 Vite 설정은 바꾸지 않았다.

## 8. DB 점검 스크립트가 `root@localhost`로 접속함

### 증상

```text
Access denied for user 'root'@'localhost'
```

### 원인

점검 스크립트가 `/etc/yakmogo/yakmogo-enhancement.env`에 datasource URL·사용자명·비밀번호가 모두 있다고 가정했다. 실제 Goal 3 구조는 다음과 같다.

- 공개 URL·사용자명: `application.yml`, `application-enhancement.yml`
- 비밀번호: root 전용 `EnvironmentFile`

존재하지 않는 shell 변수를 사용해 MariaDB CLI 기본 사용자 root로 접속했다.

### 해결

공개 설정의 `localhost:3306`, `yakmogo_user`를 사용하고 비밀번호만 root 환경 파일에서 읽었다. 값은 출력하지 않았고 임시 askpass와 점검 스크립트는 즉시 삭제했다.

첫 시도는 SELECT 실행 전 인증에서 실패했으므로 DB 변경이 없었다.

## 9. PowerShell과 원격 shell 인용 충돌

### 증상

원격 명령의 종료 코드 `$?`를 PowerShell 문자열 안에서 보존하려던 명령이 다음 오류로 실행되지 않았다.

```text
unexpected EOF while looking for matching quote
```

### 해결

복잡한 중첩 인용을 제거하고 원격 스크립트 자체가 `set -eu`로 실패를 반환하도록 했다. SSH 명령은 스크립트 실행과 임시 파일 삭제만 순서대로 수행했다.

교훈은 서로 다른 shell을 한 줄에 겹칠수록 변수 확장 주체가 불명확해진다는 점이다. 반복 배포 절차는 Goal 8에서 저장소 스크립트와 CI step으로 분리해야 한다.

## 10. systemd active보다 HTTP 준비가 늦음

### 관찰

8081 재시작 후 약 33초 동안 `curl` 연결 거부가 발생했다. 이후 HTTP 200이 됐고 migration도 성공했다.

### 해석

Java 프로세스가 시작된 시점과 Spring이 DB migration·JPA 초기화를 끝내고 포트를 받을 수 있는 시점은 다르다. `systemctl start` 성공이나 `active`만으로 배포 성공을 선언하면 너무 이르다.

### 해결

최대 60초 동안 다음 두 조건을 함께 polling했다.

```text
systemctl is-active yakmogo-enhancement == active
GET http://localhost:8081/ == 200
```

실패 시 이전 JAR을 복구하고 8081만 재시작하도록 했다. Goal 8 자동화도 같은 readiness 조건을 사용해야 한다.

## 11. 실제 메시지 없이 알림 흐름 검증하기

8081은 다음 설정을 유지했다.

```text
telegram.bot.enabled=false
scheduling.enabled=false
```

자동 테스트는 `RestTemplate`과 `TelegramService`를 mock해 네트워크를 호출하지 않는다. 8081에서는 재기동 이후 journal과 DB를 확인했다.

- Telegram 전달 관련 로그 0건
- scheduler 실행 로그 0건
- `notification_delivery` 행 0건
- 실제 Telegram 메시지 0건

bot polling 비활성과 scheduler 비활성은 서로 다른 경계다. Telegram bot만 꺼도 scheduler가 Telegram HTTP API를 호출할 수 있으므로 둘 다 확인해야 한다.

## 12. 복구 자료와 남은 한계

적용 전 자료:

- JAR과 DB dump: `/home/pi/myprojects/yakmogo-enhancement/backup-goal7-20260714-162049`
- dump mode: 600
- 후보 JAR checksum: Goal 문서 참조

V2는 새 빈 전달 테이블과 unique constraint를 추가한다. 이전 JAR은 추가 객체를 사용하지 않아 JAR rollback과 호환된다. DB 자체 복원은 현재 고도화 데이터를 되돌리는 파괴적 작업이므로 자동 수행하지 않으며, 실제 복원이 필요하면 승인 후 서비스 중지·현재 DB 보존·dump 복원 순서로 수행한다.

Telegram 원격 수락 직후 DB commit 전 장애의 완전한 exactly-once 전달은 보장하지 않는다. Telegram API가 애플리케이션 idempotency key를 제공하지 않기 때문이다. 현재 구현이 보장하는 범위는 정상 scheduler 반복, 일반 HTTP 실패와 retry, 동일 단계 DB 중복 생성 방지다.


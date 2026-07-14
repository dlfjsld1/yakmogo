# 배포와 런타임 트러블슈팅

## 사례 1: 운영 8080에 고도화 빌드를 먼저 배포한 뒤 환경을 분리함

### 상황

초기에는 로컬 변경을 빌드해 Raspberry Pi의 기존 `yakmogo.service`에 직접 배포했다. 이후 브랜치 정책을 `main` 안정판, `enhancement` 고도화 통합으로 정하면서 실행 환경도 같은 방식으로 분리해야 했다.

### 최초 접근

기존 README의 배포 절차가 단일 JAR과 단일 systemd 서비스를 전제로 했기 때문에 다음 경로를 그대로 사용했다.

```text
/home/pi/myprojects/yakmogo/yakmogo-0.0.7-SNAPSHOT.jar
yakmogo.service
port 8080
database yakmogo
```

### 발생한 증상

Git `main`은 변경하지 않았지만 운영 8080 프로세스에는 feature에서 빌드한 JAR이 실행됐다. 브랜치 정책과 실제 실행 환경의 의미가 일치하지 않았다.

### 조사 과정

1. Git 브랜치와 systemd 서비스는 서로 자동으로 연결되지 않는다는 점을 확인했다.
2. 8080이 참조하는 JS bundle과 배포 JAR 백업을 비교했다.
3. 고도화 작업 중 운영 데이터와 Telegram 알림을 건드리지 않으려면 포트뿐 아니라 DB와 스케줄러도 분리해야 함을 확인했다.

### 원인

브랜치 분리만으로 운영 격리가 된다고 볼 수 없었다. 동일 서비스·DB·Telegram bot을 쓰면 feature 빌드가 운영 행위를 그대로 수행한다.

### 해결

다음 구조로 분리했다.

| 구분 | 운영 | 고도화 |
|---|---|---|
| 서비스 | `yakmogo.service` | `yakmogo-enhancement.service` |
| 포트 | 8080 | 8081 |
| DB | `yakmogo` | `yakmogo_enhancement` |
| 경로 | `/home/pi/myprojects/yakmogo` | `/home/pi/myprojects/yakmogo-enhancement` |
| Telegram bot | 활성 | 비활성 |
| scheduler | 활성 | 비활성 |

운영 8080은 직전 안정 JAR로 복원하고 고도화 빌드는 8081에만 배포했다.

### 검증

- 8080과 8081 동시 HTTP 200
- 8080 HTML은 안정 bundle 참조
- 8081 HTML은 고도화 bundle 참조
- 두 systemd 서비스 `active`
- 고도화 DB의 `SELECT DATABASE()` 결과 `yakmogo_enhancement`
- 고도화 시작 로그에 `telegram.bot.enabled=false`, `scheduling.enabled=false`

### 배운 점

브랜치 전략은 실행 환경 전략과 함께 설계해야 한다. 운영 격리는 포트만 바꾸는 작업이 아니라 DB, 외부 메시지, 스케줄러와 롤백 지점까지 나누는 작업이다.

### 재발 방지

- feature와 enhancement 배포는 8081만 사용
- 운영 8080 배포는 최종 승인 전 금지
- 배포 검증에서 두 HTML의 bundle 이름을 동시에 확인
- JAR 교체 전 타임스탬프 백업과 SHA-256 비교

## 사례 2: systemd는 active지만 HTTP는 아직 `000`

### 상황

JAR 교체 후 `systemctl is-active yakmogo`가 `active`를 반환해 곧바로 HTTP 요청을 실행했다.

### 최초 접근

systemd의 active 상태를 애플리케이션 준비 완료로 간주했다.

### 발생한 증상

서비스는 active였지만 `curl`은 다음과 같이 실패했다.

```text
ROOT:000
curl: (7) Failed to connect to localhost port 8080
```

같은 시점의 로그에는 HikariCP가 시작 중이었고 Tomcat 시작 로그는 아직 없었다.

### 조사 과정

1. systemd의 MainPID를 확인했다.
2. `ExecStart`가 Java가 아니라 `start.sh`임을 확인했다.
3. Java 프로세스가 DB 연결과 Hibernate 초기화를 완료하는 데 약 20~30초가 걸리는 것을 로그로 확인했다.
4. 이후 `Tomcat started on port ...` 로그가 발생한 뒤 HTTP 200이 됐다.

### 원인

systemd에서 wrapper shell이 실행되면 서비스는 active가 되지만 Spring Boot의 HTTP 준비 상태까지 보장하지 않는다.

### 해결

재시작 뒤 최대 45~60초 동안 TCP 포트와 HTTP 응답을 폴링하도록 배포 검증을 변경했다.

### 검증

- 8080 또는 8081 포트 open
- `/` 응답 200
- HTML이 예상 bundle 참조
- 보호 API가 예상 상태 401 반환
- 로그에 `Tomcat started`와 `Started YakmogoApplication`

### 배운 점

프로세스 생존, 포트 listen, 애플리케이션 정상 응답은 서로 다른 단계다.

### 재발 방지

Goal 8 자동 배포에서 HTTP health check와 제한 시간, 실패 시 롤백을 필수로 구현한다.

## 사례 3: 봇만 꺼서는 고도화 알림이 격리되지 않음

### 상황

고도화 서비스를 별도로 실행하면서 Telegram bot long polling만 비활성화하려 했다.

### 최초 접근

`TelegramBotConfig`에 `telegram.bot.enabled=false`를 적용하면 Telegram 동작 전체가 멈출 것으로 생각할 수 있었다.

### 발생한 증상

코드 조사에서 `SchedulerService`가 매분 실행되고 `TelegramService`를 통해 Telegram REST API를 직접 호출한다는 사실을 확인했다. 실제 중복 메시지를 발송하기 전에 발견했으므로 운영 장애 로그는 없다.

### 조사 과정

1. `@Scheduled` 사용 위치를 전체 검색했다.
2. `SchedulerService.checkMissedDose()`가 매분 실행됨을 확인했다.
3. `TelegramService`가 bot polling 설정과 무관하게 REST 요청을 보내는 것을 확인했다.
4. `MedicineGroupService`에도 별도 일일 생성 스케줄이 있음을 확인했다.

### 원인

Telegram 수신 polling과 알림 발신 REST 호출은 서로 다른 실행 경로다. 봇 등록 설정 하나로 두 경로를 모두 제어할 수 없었다.

### 해결

커밋 `1187f01`에서 `@EnableScheduling`을 `SchedulingConfig`로 이동하고 다음 조건을 추가했다.

```text
scheduling.enabled=false
telegram.bot.enabled=false
```

운영은 설정을 생략해 기존 기본값 true를 유지하고, 8081에서만 둘 다 false로 설정했다.

### 검증

- 8081 시작 옵션에 두 설정이 false로 표시
- 8080은 기존 동작 유지
- 고도화 DB와 운영 DB 분리

### 배운 점

외부 시스템 연동을 끌 때는 SDK 설정만 보지 말고 해당 외부 시스템을 호출하는 모든 경로를 검색해야 한다.

### 재발 방지

- 고도화 서비스 기본 실행 옵션에 두 비활성 설정 유지
- Goal 7에서 알림 정책과 Telegram 전달 경계를 분리

## 사례 4: 명령행에 노출된 민감정보

### 상황

Raspberry Pi 프로세스를 점검하면서 Java 실행 인자를 조회했다.

### 최초 접근

README에 따라 DB 비밀번호, Telegram token과 관리자 비밀번호를 `java -D...` 인자로 전달했다.

### 발생한 증상

`ps` 출력에서 민감값이 마스킹되지 않은 상태로 보였다. 이 문서에는 실제 값을 남기지 않는다.

```text
java -DYAKMOGO_DB_PASSWORD=<redacted> \
     -DTELEGRAM_BOT_TOKEN=<redacted> \
     -DADMIN_PASSWORD=<redacted> ...
```

### 조사 과정

실행 중 Java 프로세스의 command line과 `start.sh` 구조를 비교해 시스템 속성이 그대로 프로세스 인자에 포함됨을 확인했다.

### 원인

환경별 설정을 빠르게 전달하기 위해 command line system property를 사용했고, 프로세스 목록 노출을 고려하지 않았다.

### 해결

Goal 1·2 범위에서는 운영 설정을 변경하지 않았다. 8081 서명 키는 권한 700인 스크립트에 임시로 보관했다.

### 검증

현재도 기존 운영 start script의 민감정보 구조는 남아 있다. 따라서 이 사례는 완전 해결이 아니라 확인된 후속 작업이다.

### 배운 점

Git에 비밀값을 넣지 않는 것만으로 충분하지 않다. 프로세스 인자, 로그, 서비스 파일 권한도 비밀 관리 범위다.

### 재발 방지

Goal 3에서 systemd `EnvironmentFile` 또는 credentials로 이동하고 파일 권한과 `ps` 비노출을 검증한다.

## 사례 5: sudo 인증 캐시를 가정한 설치 스크립트 실패

### 상황

고도화 DB와 systemd 서비스를 한 번에 설치하는 원격 스크립트를 실행했다.

### 최초 접근

처음 `sudo -v`로 인증한 다음 같은 원격 shell의 후속 명령에서 `sudo -n`을 사용하려 했다.

### 발생한 증상

파일 전송과 일반 사용자 작업 뒤 관리자 단계에서 다음 오류가 발생했다.

```text
sudo: a password is required
```

### 조사 과정

첫 시도에서 어느 단계까지 생성됐는지 확인하고, 후속 `sudo -n`이 인증 캐시를 사용하지 못한 것을 확인했다.

### 원인

해당 비대화형 SSH·sudo 환경에서 인증 캐시가 후속 명령에 유지될 것이라는 가정이 맞지 않았다.

### 해결

DB 생성, unit 설치, daemon reload, service enable 각각에 명시적으로 `sudo -k -S` 인증을 전달했다.

### 검증

- `yakmogo_enhancement` 생성
- systemd unit 설치 및 enable
- 8081 서비스 active, 재시작 0회

### 배운 점

배포 스크립트는 로컬 터미널의 sudo timestamp 동작을 원격 비대화형 실행에도 그대로 가정하면 안 된다.

### 재발 방지

Goal 8에서 권한 경계를 명시한 배포 전용 스크립트와 실패 단계별 재실행 안전성을 구현한다.

## 확인된 배포 근거

- Goal 1 격리 커밋: `1187f01`
- Goal 1 Backend CI: <https://github.com/dlfjsld1/yakmogo/actions/runs/29301528249>
- Goal 1 Web CI: <https://github.com/dlfjsld1/yakmogo-web/actions/runs/29301529443>
- Goal 2 고도화 JAR 백업: `yakmogo-0.0.7-SNAPSHOT.jar.backup-20260714-115720`
- Goal 2 통합 검증: 8081 200, 허용 userId 200, 범위 밖 403, 임의 토큰 401

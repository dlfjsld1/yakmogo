# Goal 3: 런타임 설정과 비밀정보 분리

## 한 문장 요약

고도화 8081의 환경 경계를 Spring profile로 고정하고, Java 명령행과 `start.sh`에 있던 비밀정보를 root 전용 systemd `EnvironmentFile`로 이동했다.

## 작업 배경

Goal 1에서 운영 8080과 고도화 8081을 서비스·포트·DB 단위로 분리했지만, 8081의 실행 설정은 운영 스크립트를 복사한 뒤 Java `-D` 옵션과 `JAVA_TOOL_OPTIONS`로 덮어쓰는 임시 구조였다. 이 방식은 실제 동작은 했지만 실행 명령만 조회해도 DB 비밀번호, Telegram token, 관리자 비밀번호가 보였다.

Goal 2의 인증 변경을 `enhancement`에 통합한 뒤 백엔드와 웹 CI가 성공한 커밋을 기준으로 `feature/runtime-config`를 분기했다. `main`, 운영 8080, 운영 DB는 변경하지 않았다.

## 변경 전 문제

- 8081 `start.sh`가 DB·Telegram·관리자 비밀값을 Java `-D` 인자로 전달했다.
- 포트, DB URL, bot, scheduler 설정이 `JAVA_TOOL_OPTIONS`와 개별 인자에 흩어졌다.
- `ps`에서 8081의 민감정보가 평문으로 보였다.
- `start.sh` 권한이 `700`이어도 이미 실행된 프로세스의 인자 노출은 막지 못했다.
- README가 같은 `-D` 방식을 권장하고 있어 안전하지 않은 구조가 재생산될 수 있었다.

## 이번 Goal 범위

- `application-enhancement.yml`로 8081의 비민감 환경 차이를 선언
- root 전용 systemd `EnvironmentFile`로 8081 비밀값 이동
- 비밀값 없는 unit, 환경파일 예제, 실행 스크립트를 소스에 추가
- 8081 JAR과 설정만 전환하고 자동 롤백 지점 생성
- `ps`, 파일 권한, DB, HTTP, 인증, 웹 bundle을 실제 Raspberry Pi에서 검증
- README와 Goal·트러블슈팅 문서 갱신

## 범위에서 제외한 내용

- `main` 병합
- 운영 8080의 unit, `start.sh`, 비밀값 변경
- 운영 DB 변경
- 비밀 관리 서버나 범용 설정 프레임워크 도입
- Android, Device, Pairing, FCM 관련 설정·API 추가
- 8081 자동 배포와 자동 롤백 파이프라인 구현(Goal 8 범위)

## 도입한 기술과 개념

### Spring profile

`SPRING_PROFILES_ACTIVE=enhancement`가 활성화되면 다음 비민감 차이가 적용된다.

| 설정 | 값 | 목적 |
|---|---:|---|
| `server.port` | `8081` | 운영 8080과 포트 분리 |
| `spring.datasource.url` | `jdbc:mariadb://localhost:3306/yakmogo_enhancement` | 운영 DB와 분리 |
| `telegram.bot.enabled` | `false` | Telegram polling 비활성 |
| `scheduling.enabled` | `false` | 예약 생성·알림 발신 비활성 |

### systemd EnvironmentFile

실제 값은 `/etc/yakmogo/yakmogo-enhancement.env`에 저장한다. 디렉터리는 `root:root 700`, 파일은 `root:root 600`이다. systemd manager가 파일을 읽은 뒤 `User=pi` 프로세스에 환경을 전달하므로 애플리케이션 사용자에게 파일 읽기 권한을 줄 필요가 없다.

환경변수는 프로세스 환경에는 존재한다. 따라서 같은 권한이나 root가 `/proc/<pid>/environ`을 읽을 수 있다는 한계는 남는다. 이번 Goal의 개선점은 일반 프로세스 목록과 스크립트·unit에서 값을 제거하고 파일 접근을 root로 제한한 것이다.

## 선택한 방식과 대안

### 선택: profile + EnvironmentFile

비민감 차이는 버전 관리되는 profile에, 비밀값과 설치 환경별 URL은 서버의 `EnvironmentFile`에 둔다. 설정의 의미와 비밀 보관 위치가 분리되고, Spring의 환경변수 바인딩을 그대로 이용할 수 있다.

### 검토 후 제외: systemd credentials

`LoadCredential`은 더 강한 경계를 제공할 수 있지만 현재 Raspberry Pi의 systemd 버전과 Spring property 연결을 별도로 설계해야 한다. 8081 한 서비스의 현재 규모에서는 `EnvironmentFile`이 더 단순하고 검증 가능했다.

### 검토 후 제외: 모든 값을 EnvironmentFile에 저장

포트·DB 이름·bot/scheduler 상태까지 환경파일에만 두면 8081의 안전 기본값이 저장소에서 보이지 않는다. 비밀이 아닌 환경 경계는 profile에 고정했다.

### 검토 후 제외: 운영 8080 동시 전환

운영 위험과 Goal 범위를 키우므로 하지 않았다. 운영 전환은 별도 승인과 자격증명 회전, 점검 시간, 롤백 절차가 필요하다.

## 실제 동작 흐름

```text
systemd (root)
  ├─ /etc/yakmogo/yakmogo-enhancement.env 읽기
  └─ User=pi로 start.sh 실행
         └─ exec java -jar yakmogo-0.0.7-SNAPSHOT.jar
                ├─ application.yml
                ├─ application-enhancement.yml
                └─ systemd가 전달한 환경변수
```

`start.sh`는 shell 프로세스를 남기지 않도록 `exec`를 사용한다. systemd의 MainPID가 Java PID가 되며 명령행에는 비밀값이 없다.

## 변경된 파일과 역할

| 파일 | 역할 |
|---|---|
| `src/main/resources/application-enhancement.yml` | 8081 비민감 안전 기본값 |
| `deploy/systemd/yakmogo-enhancement.service` | EnvironmentFile을 읽는 8081 unit |
| `deploy/systemd/yakmogo-enhancement.env.example` | 실제 값이 없는 설치 키 목록 |
| `deploy/systemd/start-enhancement.sh` | 비밀값 없이 JAR만 실행 |
| `README.md` | `-D` 비밀 전달 예제를 제거하고 안전한 배포 경로 안내 |
| `docs/troubleshooting/deployment-and-runtime.md` | 실제 노출 원인과 검증 함정 기록 |

## 데이터 및 설정 변경

- DB 스키마 변경 없음
- 운영 DB 데이터 변경 없음
- 8081 DB는 계속 `yakmogo_enhancement` 사용
- 8081 Telegram token과 chat ID는 실제 운영값 대신 비활성 환경용 문자열 사용
- 8081 인증 secret, 관리자 비밀번호, DB 비밀번호는 root 전용 파일로 이동
- 운영 8080 설정은 변경하지 않음

## 시행착오와 문제 해결 과정

### 1. PowerShell에서 `npm`이 실행 정책에 막힘

`npm.ps1`이 차단돼 `npm.cmd`로 lint와 build를 실행했다. 웹 프로젝트에는 `test` 스크립트가 없으므로 존재하지 않는 검사를 성공한 것처럼 기록하지 않고 lint/build만 검증했다.

### 2. Gradle과 Vite가 샌드박스 경계에 막힘

Gradle wrapper 최초 다운로드와 Vite의 상위 경로 조회가 제한됐다. 같은 명령을 필요한 범위에서 권한 승인 후 재실행했고 백엔드 테스트·bootJar와 웹 build가 성공했다.

### 3. 서비스 active 직후 8081 연결 실패

재시작 직후 약 35초 동안 HTTP 연결이 실패했고 이후 200이 됐다. Goal 1에서 확인한 것처럼 systemd `active`는 Spring Boot 준비 완료를 의미하지 않는다. 배포 명령은 5초 간격, 최대 60초의 HTTP polling과 실패 시 자동 복구를 사용했다.

### 4. 일반 사용자 권한 검증이 보호 디렉터리에서 중단됨

`/etc/yakmogo`가 의도대로 `700`이어서 `pi` 사용자의 `stat`도 거부됐다. 설정 오류가 아니라 보호가 작동한 결과였다. 파일 내용은 출력하지 않고 root 구간에서 소유권·mode와 키 이름만 확인하도록 검증을 분리했다.

### 5. 프로세스 출력 마스킹을 신뢰하면 안 됨

초기 기준선 조사에서 shell·정규식 인용 차이 때문에 의도한 마스킹이 적용되지 않는 상황을 확인했다. 이후 검증은 전체 운영 프로세스 인자를 출력하지 않고, 8081 MainPID만 대상으로 금지 키 존재 여부를 먼저 검사한 뒤 안전한 명령 형태만 출력했다. 실제 값은 코드와 문서에 기록하지 않았다.

## 테스트와 보호 시나리오

- `./gradlew clean test bootJar`: 성공
- 웹 `npm run lint`: 성공
- 웹 `npm run build`: 성공
- bootJar 내부에 `application-enhancement.yml`, `index.html`, Goal 2 JS bundle 포함 확인
- 8081 MainPID 명령행에 `PASSWORD`, `TOKEN`, `SECRET` 및 비밀 property 인자 없음
- 인증 없이 `GET /api/v1/users`: `401`
- 8081 HTML의 bundle: Goal 2 빌드와 일치

## 배포 및 검증

2026-07-14에 Raspberry Pi의 `yakmogo-enhancement.service`만 재시작했다.

| 검증 항목 | 결과 |
|---|---|
| 8080 서비스 | `active`, HTTP 200 |
| 8081 서비스 | `active`, HTTP 200 |
| 8081 Java 명령 | `/usr/bin/java -jar ...` |
| 활성 profile | `enhancement` |
| 8081 DB | `yakmogo_enhancement` |
| 보호 API 무토큰 | 401 |
| 환경 디렉터리 | `root:root 700` |
| 환경파일 | `root:root 600` |
| `start.sh` | `pi:pi 750`, 비밀값 없음 |

롤백용 기존 JAR과 unit은 `/home/pi/myprojects/yakmogo-enhancement/backup-goal3-20260714-122559`에 남겼다. 비밀값이 있던 구형 `start.sh`는 `/etc/yakmogo/backups/goal3-20260714-122559/start.sh`로 옮겨 `root:root 600`으로 보호했다.

## 운영 시 주의 사항

- `/etc/yakmogo/yakmogo-enhancement.env`의 내용을 로그나 점검 출력에 표시하지 않는다.
- `systemctl show Environment`와 `/proc/<pid>/environ`도 민감값을 노출할 수 있으므로 일반 점검 명령으로 사용하지 않는다.
- 새 키를 추가할 때는 `.env.example`에는 placeholder만 넣는다.
- `application-enhancement.yml`에서 bot과 scheduler의 false 기본값을 제거하지 않는다.
- 8081 JAR은 Goal 2 웹 `dist`를 포함해야 한다. 백엔드 bootJar만 그대로 배포하면 SPA가 빠진다.

## 직접 확인하는 방법

값을 출력하지 않는 다음 검사만 사용한다.

```bash
systemctl is-active yakmogo yakmogo-enhancement
curl -sS -o /dev/null -w '%{http_code}\n' http://localhost:8080/
curl -sS -o /dev/null -w '%{http_code}\n' http://localhost:8081/
curl -sS -o /dev/null -w '%{http_code}\n' http://localhost:8081/api/v1/users

pid=$(systemctl show -p MainPID --value yakmogo-enhancement)
tr '\0' ' ' < "/proc/$pid/cmdline"

sudo stat -c '%U:%G %a %n' \
  /etc/yakmogo \
  /etc/yakmogo/yakmogo-enhancement.env
```

마지막 프로세스 명령은 `java -jar ...`만 보여야 한다. 환경파일의 내용 자체를 `cat`하지 않는다.

## 알려진 한계

- 운영 8080은 Goal 범위상 전환하지 않았으므로 기존 Java 명령행 비밀 노출 구조가 남아 있다.
- 환경변수는 root와 동일 권한 프로세스에서 완전히 숨길 수 없다.
- DB 계정 비밀번호 등 이미 운영과 공유된 자격증명의 회전은 별도 유지보수 작업이 필요하다.
- 자동 배포와 자동 롤백은 아직 수동 검증 절차다.

## 후속 Goal에 넘길 내용

- Goal 8: 8081 배포 시 통합 JAR 패키징, 60초 health polling, 실패 롤백 자동화
- 운영 유지보수: 8080 EnvironmentFile 전환과 노출 가능 자격증명 회전
- Goal 7: Telegram 정책과 전달 구현 분리 후 비활성 환경의 placeholder 의존 축소

관련 기록: [배포와 런타임 트러블슈팅](../troubleshooting/deployment-and-runtime.md)

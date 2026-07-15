# Goal 9: 관측성·백업·복원

## 현재 상태

**완료.** 저장소 구현과 GitHub-hosted ARM64 격리검증에 이어 2026-07-15 사용자 승인 후 Pi의 8081 업데이트, 정기 백업 timer, 격리 복원 리허설과 기존 Uptime Kuma monitor 설명 갱신을 완료했다. 운영 8080, 운영 DB `yakmogo`, `main`은 변경하지 않았다.

## 한 문장 요약

Yakmogo가 살아 있는지 민감정보 없이 확인하고, 매월 검증된 SQL dump를 최신 3개만 보관하며, 별도 빈 DB에서 복원을 검증할 수 있는 최소 운영 체계를 만든다.

## 작업 배경

Docker portable 전환으로 앱과 MariaDB를 한 Compose project에서 설치할 수 있게 됐지만, 다음 경계가 남아 있었다.

- 루트 화면 200만으로 DB 연결까지 정상인지 알 수 없었다.
- 수동 dump는 만들 수 있지만 정기 실행과 보관 정책이 없었다.
- update backup과 정기 backup이 같은 폴더를 쓰면 서로의 복원 지점을 밀어낼 수 있었다.
- Uptime Kuma의 기존 `Yakmogo` monitor는 설명이 비어 있었다.
- self-hosted runner가 남아 있지만 portable 수동 update 경로에서는 사용하지 않는다.

## 이번 Goal 범위

- Spring Boot Actuator health만 공개
- 월 1일 03:35 KST systemd timer 설치·제거 도구
- 폴더별 최신 3개 논리 dump 보관
- gzip·SHA-256 검증 성공 뒤에만 오래된 백업 삭제
- 정기 backup과 update backup 분리
- 격리 Compose project 복원 검사 강화
- 단일 Yakmogo 운영 monitor의 설명과 승격 시 endpoint 전환 계획
- 운영 런북과 시행착오 문서

## 범위에서 제외한 내용

- `main` 병합과 운영 8080 배포
- 운영 DB migration과 운영 DB 복원 시험
- Pi 전체 복구문서 수정
- Transmission, Jellyfin, Samba 등 다른 홈서버 서비스 문서화
- runner monitor 추가
- Android 관련 코드·API·table

## 도입한 기술과 개념

### 최소 Actuator 노출

`spring-boot-starter-actuator`를 추가하되 web exposure는 `health` 하나로 제한한다. `show-details: never`이므로 DB 장애를 상태에는 반영하지만 DB 이름, component와 오류 상세는 응답하지 않는다.

### 논리 dump와 Docker volume

Named volume은 MariaDB 실행 데이터이고, SQL dump는 version과 환경이 달라도 내용을 검증하고 복원할 수 있는 복구 자료다. Container가 다시 뜬다는 것과 데이터가 복구됐다는 것은 같지 않으므로 dump를 복구 기준으로 삼는다.

### systemd timer

`OnCalendar=*-*-01 03:35:00 Asia/Seoul`로 시간대를 명시한다. `Persistent=true`라서 예약 시각에 Pi가 꺼져 있었으면 다음 부팅 후 누락된 작업을 한 번 실행한다. service와 timer를 분리해 수동 시험은 schedule을 바꾸지 않고 service만 실행할 수 있다.

## 실제 동작 흐름

```text
매월 1일 03:35 KST
  -> yakmogo-backup.timer
  -> yakmogo-backup.service (설치 사용자 권한)
  -> backup.sh backups/scheduled
  -> MariaDB logical dump
  -> gzip 검사
  -> SHA-256 생성·재검증
  -> 검증된 dump가 4개 이상이면 가장 오래된 pair 삭제
  -> 최신 3개 유지
```

Update는 `backups/update`에 같은 검증 절차로 별도 backup을 만든 뒤 image 교체를 시작한다.

## 변경된 파일과 역할

- `build.gradle`: Actuator dependency
- `application.yml`: health만 노출하고 상세정보 숨김
- `ApiIntegrationTests`: health 응답과 민감 endpoint 비노출 검사
- `deploy/portable/backup.sh`: 잠금, checksum 재검증, 최신 3개 보관
- `deploy/portable/update.sh`: update backup 전용 폴더
- `deploy/portable/setup.sh`: 환경값 생성, 앱·DB 설치, timer 등록과 health 확인을 한 명령으로 연결
- `install-backup-timer.sh`, `remove-backup-timer.sh`: timer 설치와 제거
- `scripts/ci/test-portable-install.sh`: backup 보관·복원·update 분리 통합검증
- `scripts/release/package-portable.sh`: timer 도구를 release package에 포함
- `docs/runbooks/yakmogo-operations.md`: 설치·상태확인·복원·Kuma 운영 절차
- `docs/troubleshooting/operations.md`: 실제 실패와 판단 기록

## 확정된 백업 정책

| 항목 | 값 |
|---|---|
| 정기 실행 | 매월 1일 03:35 KST |
| 정기 경로 | `backups/scheduled` |
| update 경로 | `backups/update` |
| 보관 수 | 각 폴더 최신 3개 |
| 삭제 시점 | 새 dump의 gzip·SHA-256 검증 성공 후 |
| 백업 실패 | 기존 dump 삭제 없음 |
| 동시 실행 | `flock`으로 차단 |
| 파일 권한 | umask `077` |

## 새 서버 최소 설치 흐름

일반 사용자는 `./setup.sh` 한 명령만 실행한다. 첫 실행에서 자동 감지한 LAN URL을 확인하고 관리자 비밀번호를 입력한다. DB 비밀번호 두 개와 인증 secret은 자동 생성되며 `.env`에만 저장된다. Docker 앱·DB 설치가 성공하면 같은 진입점이 backup timer를 등록하고 health까지 확인한다.

자동화할 수 없는 Telegram token과 Chat ID는 기본 비활성 상태로 둔다. 고급 사용자를 위한 `install.sh`는 유지하지만 신규 설치 매뉴얼의 기본 경로에서는 숨긴다.

## Pi 읽기 전용 기준선

2026-07-15에 설정을 바꾸지 않고 확인했다.

- timezone: `Asia/Seoul`
- filesystem: 125,436,633,088 bytes 중 99,918,848,000 bytes 사용 가능, 사용률 17%
- Uptime Kuma: `1.23.17`, monitor description column 지원
- Kuma data: bind mount, DB 75,124,736 bytes
- 기존 monitor: 6개, 모두 60초·active, 설명 길이 0
- 기존 Yakmogo monitor: 이름 `Yakmogo`, 운영 8080 루트 HTTP 검사
- enhancement runner service: enabled이며 process 실행 중
- `yakmogo.service`: enabled
- `yakmogo-enhancement.service`: disabled

실제 주소, token, password와 Chat ID는 문서에 기록하지 않았다.

## Uptime Kuma 적용 결과

1. 기존 `Yakmogo` monitor 이름은 유지하고 설명을 추가한다.
2. 8081 enhancement는 임시 release candidate이므로 Kuma monitor를 추가하지 않는다.
3. Goal 10에서 새 release가 8080에 승격되면 같은 monitor의 URL만 `/actuator/health`로 바꿔 기존 이력을 유지한다.
4. 설명에는 감시 대상, 정상 조건, 장애 영향, 확인 명령과 이 프로젝트 런북 경로를 기록한다.
5. MariaDB 상태는 앱 health에 포함하고 DB·runner용 별도 monitor는 만들지 않는다.

실제 설정은 이름 `Yakmogo`, 운영 8080 루트 URL, 60초, active 상태를 유지하고 설명 178자를 추가했다. 변경 전 Kuma DB는 container 내부에 권한 `600`으로 백업했다. 8081 URL monitor 수는 0개이며 MariaDB와 runner monitor도 만들지 않았다. 다른 5개 홈서버 monitor는 Yakmogo 범위 밖이므로 변경하지 않았다.

## 시행착오와 해결

첫 health 보안 테스트는 `/actuator`가 404일 것으로 가정했지만 200이었다. 확인 결과 민감 Actuator endpoint가 열린 것이 아니라 `SpaController`가 React index로 전달한 응답이었다. HTTP status 대신 실제 handler를 검사하도록 바꿨다. 자세한 내용은 [관측·백업·복원 시행착오](../troubleshooting/operations.md)에 기록했다.

Pi 최종 점검에서 DB container의 짧은 이름을 `yakmogo-db`로 가정해 행 수가 비어 보였다. 실제 Compose 이름을 먼저 조회한 뒤 `yakmogo-enhancement-yakmogo-mariadb-1`에서 다시 검사해 5개 핵심 table이 모두 0행이고 Flyway V1·V2가 성공 상태임을 확인했다. 비밀번호 값은 출력하지 않고 환경변수 이름만 확인했다.

## 현재 테스트 결과

- Gradle test: 35개 성공, 1개 MariaDB 전용 test는 환경변수가 없어서 로컬 skip
- health: `UP`, component 상세 없음
- env·configprops·heapdump: Actuator handler에 매핑되지 않음
- `git diff --check`: 성공
- Backend CI `29384729351`: 성공. 첫 설치 모의 환경에서 `.env` 자동 생성, secret 길이, 권한 `600`, 앱 설치·timer 연결·health 흐름 검증
- Enhancement Release Candidate `29384729399`: 성공
- ARM64 임시 Compose project에서 신규 설치, 오래된 백업 3개와 새 dump의 retention, 빈 volume 복원, update backup 분리와 고장 image rollback 성공

## Pi 적용 및 검증 결과

- 배포 image: `yakmogo:0.0.7-rc.13.1`; 8081 루트와 `/actuator/health` HTTP 200
- health body: `{"status":"UP"}`; env·configprops·heapdump는 Actuator handler가 아니라 React SPA 응답
- timer: enabled·active, 다음 실행 `2026-08-01 03:35 KST`
- 수동 정기 backup: gzip 1,840 bytes와 checksum, 권한 `600`, `sha256sum --check` 성공
- update 직전 backup: gzip 1,838 bytes와 checksum, 권한 `600`
- 격리 복원: 별도 project·포트 18082·임시 volume에서 health 200, 5개 핵심 table row 수 일치, Flyway V1·V2 성공, FK orphan 0건
- 리허설 정리: 시험 container 0개, 시험 volume 0개, 시험 directory 없음
- 고도화 DB: 핵심 table row 수 `0, 0, 0, 0, 0`, Flyway V1·V2 성공으로 적용 전과 동일
- Kuma: 기존 monitor 하나만 유지, 설명 추가, 8081 monitor 0개
- 운영 보호: `yakmogo.service` PID `1794958` 유지, 8080 HTTP 200, 운영 DB row 수 `1, 1, 2, 66`과 크기 131,072 bytes 유지
- 다른 container와 runner: ID 불변, runner active
- 디스크: `/home/pi` 117G 중 20G 사용, 93G 여유

## 운영 영향과 위험

- Timer 설치는 `/etc/systemd/system`을 변경하지만 Yakmogo container를 재시작하지 않는다.
- 수동 백업은 짧은 논리 dump 읽기를 발생시키며 현재 DB 크기에서는 부하가 작다.
- Actuator health는 외부에 전체 상태만 공개한다. 상세정보는 숨기지만 서비스가 살아 있는지는 누구나 확인할 수 있다.
- `Persistent=true` 때문에 월 1일에 Pi가 꺼져 있으면 다음 부팅 직후 backup이 실행될 수 있다.
- SQL migration은 dump가 있어도 자동 역변환하지 않는다.

## 후속 결정

- 사용하지 않는 self-hosted runner service와 이전 host enhancement DB·container의 제거 시점
- Goal 10에서 main 승격과 함께 기존 운영 monitor를 `/actuator/health`로 전환
- Goal 10 전체 릴리스 후보 검증과 기존 main 보존 브랜치 생성

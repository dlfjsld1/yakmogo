# Goal 9: 관측성·백업·복원

## 현재 상태

**저장소 구현과 로컬 테스트 완료, 운영 설정 활성화 전 승인 대기.** Pi의 timer, Uptime Kuma, systemd, 실제 DB volume과 운영 서비스는 아직 변경하지 않았다.

## 한 문장 요약

Yakmogo가 살아 있는지 민감정보 없이 확인하고, 매월 검증된 SQL dump를 최신 3개만 보관하며, 별도 빈 DB에서 복원을 검증할 수 있는 최소 운영 체계를 만든다.

## 작업 배경

Docker portable 전환으로 앱과 MariaDB를 한 Compose project에서 설치할 수 있게 됐지만, 다음 경계가 남아 있었다.

- 루트 화면 200만으로 DB 연결까지 정상인지 알 수 없었다.
- 수동 dump는 만들 수 있지만 정기 실행과 보관 정책이 없었다.
- update backup과 정기 backup이 같은 폴더를 쓰면 서로의 복원 지점을 밀어낼 수 있었다.
- Uptime Kuma의 기존 `Yakmogo` monitor는 이름이 모호하고 설명이 비어 있었다.
- self-hosted runner가 남아 있지만 portable 수동 update 경로에서는 사용하지 않는다.

## 이번 Goal 범위

- Spring Boot Actuator health만 공개
- 월 1일 03:35 KST systemd timer 설치·제거 도구
- 폴더별 최신 3개 논리 dump 보관
- gzip·SHA-256 검증 성공 뒤에만 오래된 백업 삭제
- 정기 backup과 update backup 분리
- 격리 Compose project 복원 검사 강화
- Yakmogo용 Uptime Kuma monitor 이름·설명 계획
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

## Uptime Kuma 변경 계획

1. 기존 `Yakmogo` monitor를 `Yakmogo PROD 8080 Web`으로 이름 변경하고 설명을 추가한다.
2. `Yakmogo ENH 8081 Health` monitor를 하나 추가한다.
3. 동일 8080 대상 monitor를 중복 생성하지 않는다.
4. 설명에는 감시 대상, 정상 조건, 장애 영향, 확인 명령과 이 프로젝트 런북 경로를 기록한다.
5. runner는 기본 배포에 사용하지 않으므로 monitor를 추가하지 않는다.

Kuma의 다른 5개 홈서버 monitor 설명은 비어 있지만 Yakmogo Goal의 저장소 범위 밖이다. 실제 Kuma에서 설명을 보강할지는 승인 보고에서 별도 선택으로 제시하고, Yakmogo 저장소에는 다른 서비스의 복구 내용을 섞지 않는다.

## 시행착오와 해결

첫 health 보안 테스트는 `/actuator`가 404일 것으로 가정했지만 200이었다. 확인 결과 민감 Actuator endpoint가 열린 것이 아니라 `SpaController`가 React index로 전달한 응답이었다. HTTP status 대신 실제 handler를 검사하도록 바꿨다. 자세한 내용은 [관측·백업·복원 시행착오](../troubleshooting/operations.md)에 기록했다.

## 현재 테스트 결과

- Gradle test: 35개 성공, 1개 MariaDB 전용 test는 환경변수가 없어서 로컬 skip
- health: `UP`, component 상세 없음
- env·configprops·heapdump: Actuator handler에 매핑되지 않음
- `git diff --check`: 성공
- portable ARM64 통합검증: feature push 후 GitHub-hosted runner에서 확인 예정

## 승인 후 실행할 작업

- 새 image를 8081에 update하고 health endpoint 확인
- Pi에 `yakmogo-backup.service`와 `.timer` 설치
- service 수동 1회 실행, dump·checksum·권한·retention 확인
- 별도 Compose project·임시 volume 복원 리허설
- Uptime Kuma 기존 8080 monitor 이름·설명 변경
- Uptime Kuma 8081 health monitor 추가
- 실제 설정과 문서 표 비교

## 운영 영향과 위험

- Timer 설치는 `/etc/systemd/system`을 변경하지만 Yakmogo container를 재시작하지 않는다.
- 수동 백업은 짧은 논리 dump 읽기를 발생시키며 현재 DB 크기에서는 부하가 작다.
- Actuator health는 외부에 전체 상태만 공개한다. 상세정보는 숨기지만 서비스가 살아 있는지는 누구나 확인할 수 있다.
- `Persistent=true` 때문에 월 1일에 Pi가 꺼져 있으면 다음 부팅 직후 backup이 실행될 수 있다.
- SQL migration은 dump가 있어도 자동 역변환하지 않는다.

## 후속 결정

- 사용하지 않는 self-hosted runner service와 이전 host enhancement DB·container의 제거 시점
- Goal 10에서 main 승격 후 운영 8080 monitor를 `/actuator/health`로 전환할지 여부
- 다른 홈서버 Kuma monitor의 설명 보강을 Yakmogo와 분리해 수행할지 여부

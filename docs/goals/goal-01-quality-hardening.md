# Goal 1: 품질 기반과 고도화 환경 격리

## 한 문장 요약

수동으로만 검사·배포하던 backend와 web에 기본 CI를 추가하고, 운영 8080과 분리된 8081·별도 DB 고도화 환경을 구축했다.

## 작업 배경

프로젝트는 Raspberry Pi에서 Spring Boot JAR과 MariaDB를 직접 실행하고 React 빌드 결과를 JAR의 정적 리소스로 포함하는 구조였다. 고도화 개발을 시작하려면 `main`과 운영 서비스를 고정한 채 변경을 검증할 별도 환경이 필요했다.

## 변경 전 문제

- backend context test가 운영 DB 환경변수에 의존했다.
- web ESLint 오류가 남아 있었다.
- feature push를 검사하는 CI가 없었다.
- 운영과 고도화가 같은 JAR 경로, 포트, DB, Telegram 실행 경로를 사용했다.
- systemd active 상태만으로 HTTP 준비 완료를 판단했다.

## 이번 Goal 범위

- 기존 `feature/quality-hardening` 검증과 enhancement 통합
- backend test 격리
- web lint 오류 정리
- feature·enhancement 기본 CI
- 운영 8080과 고도화 8081 실행 환경 분리
- 고도화 DB, Telegram bot, scheduler 격리
- 백업·체크섬·HTTP 검증을 포함한 수동 배포

## 범위에서 제외한 내용

- `main` 병합과 운영 8080 기능 변경
- 운영 DB 스키마 변경
- 자동 배포와 자동 롤백 파이프라인
- Android, Device, Pairing, FCM, Android 동기화 API
- 민감정보의 최종 systemd 보안 설정 이동

## 도입한 기술과 개념

- H2 기반 Spring context test
- 조건부 `SchedulingConfig`
- 조건부 `TelegramBotConfig`
- GitHub Actions
- 장기 통합 브랜치 `enhancement`
- feature 브랜치 검사와 enhancement 배포의 역할 분리
- systemd 별도 서비스와 MariaDB 별도 데이터베이스

## 선택한 방식과 대안

### 선택: 단일 Raspberry Pi에서 8080·8081 병행

새 장비나 컨테이너를 추가하지 않고 현재 Pi의 별도 디렉터리, 서비스, 포트와 DB를 사용했다. 현재 규모에서 가장 빠르게 운영 영향을 격리할 수 있었다.

### 제외: 포트만 분리

DB와 스케줄러를 공유하면 고도화 인스턴스가 운영 데이터를 수정하고 Telegram 알림을 중복 발송할 수 있어 제외했다.

### 제외: Goal 1에서 자동 배포까지 구현

CI 품질 검사와 배포 자동화를 한 Goal에 묶으면 실패 원인과 권한 범위가 커진다. feature 기본 검사는 Goal 1에, 8081 자동 배포·롤백은 Goal 8에 배정했다.

## 실제 동작 흐름

```text
feature/* push
  ├─ Backend CI: Java 21 → test → bootJar
  └─ Web CI: Node 22 → npm ci → lint → test(if present) → build

수동 고도화 배포
  └─ web dist를 backend static에 통합
       └─ bootJar
            └─ SHA-256 확인
                 └─ 8081 JAR 백업·교체
                      └─ yakmogo-enhancement 재시작
                           └─ TCP·HTTP·bundle·API 검증
```

## 변경된 파일과 역할

Backend 커밋 `1187f01`, `a9600ba`:

- `YakmogoApplication.java`: 전역 `@EnableScheduling` 제거
- `SchedulingConfig.java`: `scheduling.enabled` 조건으로 scheduling 활성화
- `TelegramBotConfig.java`: `telegram.bot.enabled` 조건 추가
- `YakmogoApplicationTests.java`: H2와 비활성 외부 연동 설정
- `.github/workflows/ci.yml`: backend 기본 CI

Web 커밋 `d79a4e7`, `b8d8076`:

- `App.tsx`: effect와 인증 초기 상태 정리
- `MedicineManage.tsx`: hook 의존성과 미사용 예외 변수 정리
- `TelegramLoginHandler.tsx`: effect lint 문제 정리
- `.github/workflows/ci.yml`: web 기본 CI

## 데이터 및 설정 변경

고도화 환경에만 다음을 추가했다.

```text
database: yakmogo_enhancement
service: yakmogo-enhancement.service
port: 8081
telegram.bot.enabled=false
scheduling.enabled=false
```

운영 DB `yakmogo`에는 DDL이나 데이터 변경을 적용하지 않았다.

## 시행착오와 문제 해결 과정

### 테스트가 `${RASPI_IP}`를 실제 호스트명으로 사용

초기 `contextLoads()`는 다음 원인으로 실패했다.

```text
UnknownHostException: ${RASPI_IP}
Socket fail to connect to ${RASPI_IP}
```

테스트가 MariaDB 운영 설정을 그대로 읽고 있었기 때문이다. H2 in-memory DB, 테스트용 속성, Telegram·scheduler 비활성 설정으로 context test를 격리했다.

### web lint 오류 10건

실제 ESLint 결과에서 선언 전 함수 접근, effect 내부 동기 state 변경, 미사용 catch 변수, hook 의존성 누락이 확인됐다. lazy state initializer, effect 내부 요청, `useCallback`, 불필요한 변수 제거로 해결했다.

### Gradle·Vite 환경 권한 실패

Gradle 다운로드의 `Permission denied`와 Vite 상위 경로 `Access is denied`는 코드 오류가 아니라 실행 샌드박스 제한이었다. 제한된 승인 범위로 동일 명령을 재실행해 빌드 성공을 확인했다.

### 운영에 feature JAR을 먼저 배포

브랜치 정책 확정 전에 8080에 feature 빌드를 배포했다. Git main을 안 바꿔도 실행 중인 운영 코드는 바뀐다는 점을 확인하고 안정 JAR 복원 후 8081 환경을 만들었다.

### systemd active와 HTTP 준비 상태 차이

서비스 active 직후 HTTP가 `000`이었고 HikariCP 초기화 뒤 Tomcat이 약 20~30초 후 시작됐다. 포트와 HTTP를 제한 시간 동안 폴링하도록 검증 절차를 변경했다.

상세 과정:

- [배포와 런타임 트러블슈팅](../troubleshooting/deployment-and-runtime.md)
- [CI/CD 트러블슈팅](../troubleshooting/ci-cd.md)

## 테스트와 보호 시나리오

- Backend `clean test bootJar`
- Web `lint`, `test --if-present`, `build`
- 두 enhancement GitHub Actions 성공
- 고도화 bot·scheduler 비활성
- 운영과 고도화 서비스 동시 active
- JAR 전송 전후 SHA-256 동일성 확인

## 배포 및 검증

Goal 1 완료 당시:

- 운영 `http://192.168.50.192:8080/` → 200
- 고도화 `http://192.168.50.192:8081/` → 200
- 보호 API 인증 없음 → 401
- 고도화 DB → `yakmogo_enhancement`
- 고도화 서비스 재시작 횟수 → 0

## 운영 시 주의 사항

- `enhancement`나 feature JAR은 8080에 배포하지 않는다.
- 8081에서 bot과 scheduler를 활성화하지 않는다.
- systemd active만 확인하지 말고 HTTP와 예상 bundle을 확인한다.
- 배포 JAR은 항상 백업하고 체크섬을 비교한다.

## 직접 확인하는 방법

```powershell
Test-NetConnection 192.168.50.192 -Port 8080
Test-NetConnection 192.168.50.192 -Port 8081
Invoke-WebRequest http://192.168.50.192:8080/
Invoke-WebRequest http://192.168.50.192:8081/
```

Raspberry Pi:

```bash
systemctl is-active yakmogo yakmogo-enhancement
systemctl show yakmogo-enhancement -p NRestarts -p ActiveEnterTimestamp
journalctl -u yakmogo-enhancement --since=-10min --no-pager
```

## 알려진 한계

- 자동 배포와 자동 롤백은 아직 없다.
- web 자동 테스트 script가 없어 현재 `npm test --if-present`는 실행할 테스트가 없다.
- 운영 start script의 민감정보가 command line에 노출된다.
- H2와 MariaDB의 스키마 차이는 Goal 4에서 별도 확인해야 한다.

## 후속 Goal에 넘길 내용

- Goal 2: 인증·인가 강화
- Goal 3: systemd 비밀정보와 환경 설정 정리
- Goal 4: Flyway 기준선 확정
- Goal 8: enhancement 자동 배포·health check·롤백

## 근거

- Backend: `1187f01`, `a9600ba`, `1352a0a`, `53f60d9`
- Web: `d79a4e7`, `b8d8076`, `0ee11cc`, `b8a93e0`
- Backend CI: <https://github.com/dlfjsld1/yakmogo/actions/runs/29301528249>
- Web CI: <https://github.com/dlfjsld1/yakmogo-web/actions/runs/29301529443>

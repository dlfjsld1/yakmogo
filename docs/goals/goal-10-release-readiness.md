# Goal 10: 전체 검증과 main 승격

## 현재 상태

**릴리스 후보 검증 완료, main 보존·승격 준비.** 코드 기준 backend `6f7b80c`, web `3330335`에 대한 로컬·CI·Pi 8081 검증을 완료했다. 문서 변경 외 런타임 코드는 추가하지 않는다.

## 한 문장 요약

Goal 1~9와 Docker 전환 결과를 하나의 릴리스 후보로 검증하고, 기존 `main`을 버전 보존 브랜치로 남긴 뒤 검증된 `enhancement`를 `main`으로 승격한다.

## 검증 기준선

| 저장소 | 기존 `origin/main` | 코드 릴리스 후보 |
|---|---|---|
| backend | `f5982fb773629dfe490739d0996d1bcfd50de8a8` | `6f7b80cd2369d27e679624bd42a29aca917bb25e` |
| web | `a4f727b1736287e4234059b4c3008a2e923f7407` | `3330335bbdb9cf4e87a2d4873df884c252207624` |

두 저장소 모두 기존 `main`이 `enhancement`의 조상임을 확인했다. `archive/v0.0.7-pre-enhancement` 원격 브랜치는 검증 시작 시 존재하지 않았다.

## 변경 규모와 핵심 설계

- backend: 기존 main 이후 60개 commit, 139개 파일, 10,642줄 추가·385줄 삭제
- web: 기존 main 이후 8개 commit, 13개 파일, 1,873줄 추가·348줄 삭제
- 인증: 임의 chatId 로그인을 폐지하고 서명된 Telegram login proof와 만료되는 access token을 사용한다.
- 인가: `AuthenticatedPrincipal`의 주체 유형, 허용 userId, 관리자 여부를 서비스 계층에서 사용한다.
- 복용 완료: REST와 Telegram callback이 `IntakeCommandService`의 상태 전이 규칙을 공유한다.
- 알림: 시간 판단은 `MedicationAlertPolicy`, 전달과 재시도 기록은 별도 경계로 나눴다.
- DB: `ddl-auto=validate`, Flyway V1·V2, clean 금지와 순서 검증을 적용했다.
- 설치: Spring Boot와 Yakmogo 전용 MariaDB를 하나의 Compose project 안의 별도 container로 배포한다.
- 운영: health만 공개하고 월간 SQL dump, checksum, 최신 3개 보관과 격리 복원 절차를 제공한다.

## 자동 검증 결과

| 검증 | 결과 |
|---|---|
| backend 로컬 `clean test build` | 성공 |
| backend CI #52 | 성공, MariaDB 11.8 Flyway 통합 test 포함 |
| web Vitest | 2개 파일·7개 test 성공 |
| web ESLint | 성공 |
| web production build | 성공, JS `index-CdQ-TAxj.js` |
| web CI #7 | 성공 |
| ARM64 Release Candidate #14 | 성공, 9분 59초; 신규 설치·Flyway·백업·빈 volume 복원·정상 update·의도적 실패 rollback 검증 |

Release Candidate workflow는 GitHub-hosted runner에서 web을 다시 빌드하고 versioned ARM64 image와 checksum을 만든다. Pi self-hosted runner나 자동 CD 없이 portable package만으로 설치·업데이트·복구가 완결되는 경로다.

생성 artifact는 `yakmogo-enhancement-6f7b80cd2369d27e679624bd42a29aca917bb25e`, 159MB이며 artifact digest는 `sha256:fe2d41b058536d63ab44acf224ea5c73ecb4c8dfb71775d59af679932ca094d2`다.

## Pi 8081 인수 시험

시험 전 Telegram과 scheduler가 모두 비활성임을 확인했다. 임시 데이터는 허용된 시험 Chat ID를 사용했지만 값은 로그와 문서에 남기지 않았다.

- `/actuator/health`: HTTP 200, `{"status":"UP"}`
- 미인증 사용자 목록: HTTP 401
- 빈 사용자 이름: HTTP 400
- 복용자와 보호자 생성·조회: HTTP 200
- DAILY 약 생성: HTTP 200
- WEEKLY 약 수정과 목록 조회: HTTP 200
- 약과 복용자 삭제: HTTP 200
- 시험 전후 고도화 핵심 table 행 수: `0, 0, 0, 0, 0`
- 실제 Telegram 메시지: 발송 없음

브라우저에서는 8081 React 관리자 인증 화면과 production bundle 로딩을 확인했다. 상세 CRUD는 같은 배포본의 HTTP API와 React component test로 검증했다.

## DB·백업·복원 결과

- Flyway V1·V2가 고도화 MariaDB에서 성공 상태다.
- 정기 backup과 update 직전 backup은 서로 다른 폴더에 저장한다.
- dump의 gzip·SHA-256 검증 뒤에만 각 폴더의 오래된 파일을 지운다.
- Pi 격리 복원에서 별도 Compose project, 포트 18082와 임시 volume을 사용했다.
- 복원 후 health, Flyway 이력, 핵심 table 행 수와 FK orphan 0건을 확인했다.
- 시험 container·volume·directory는 모두 제거했다.
- 운영 DB `yakmogo`에는 migration, 쓰기와 복원 시험을 수행하지 않았다.

## 환경변수 변경

설치 패키지는 다음 이름을 사용한다. 실제 값은 권한 `600`인 `.env`에만 있고 Git과 문서에 기록하지 않는다.

- Compose와 image: `COMPOSE_PROJECT_NAME`, `YAKMOGO_IMAGE`, `YAKMOGO_BIND_ADDRESS`, `YAKMOGO_HTTP_PORT`, `YAKMOGO_FRONTEND_URL`
- DB: `YAKMOGO_DB_NAME`, `YAKMOGO_DB_USER`, `YAKMOGO_DB_PASSWORD`, `YAKMOGO_DB_ROOT_PASSWORD`
- 인증: `ADMIN_PASSWORD`, `AUTH_TOKEN_SECRET`, `AUTH_TOKEN_TTL_SECONDS`, `AUTH_LOGIN_PROOF_TTL_SECONDS`
- 선택 기능: `TELEGRAM_BOT_ENABLED`, `SCHEDULING_ENABLED`, `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID`

신규 설치는 `setup.sh`가 DB 비밀번호와 인증 secret을 생성한다. 관리자 비밀번호와 자동 감지한 접속 URL만 사용자가 확인한다. Telegram 값은 명시적으로 활성화할 때만 필요하다.

## 운영 무변경 확인

- `yakmogo.service` PID: 시험 전후 `1794958`
- 운영 8080 루트: 시험 전후 HTTP 200
- 운영 DB 행 수: users 1, guardian 1, medicine_group 2, intake_log 66
- 운영 DB 논리 크기: 시험 전후 131,072 bytes
- 운영 서비스 재시작, 운영 DB migration과 release tag 생성: 수행하지 않음

## 알려진 이슈와 잔여 위험

- Flyway는 MariaDB 11.8에서 검증에 성공하지만 현재 library가 출력하는 공식 확인 범위 경고가 남아 있다.
- Telegram SDK deprecated API compile 경고와 scheduler test의 unchecked 경고가 남아 있다.
- web build는 오래된 Browserslist·baseline mapping 데이터 경고를 출력한다. 결과물 생성에는 영향이 없었다.
- GitHub Actions는 일부 `@v4`·Docker setup action이 Node.js 20을 대상으로 해 Node.js 24 강제 실행 경고를 출력한다. 현재 검증은 성공했지만 action major version 갱신 가능 여부를 후속 점검한다.
- 기존 운영 DB는 아직 Flyway 운영 migration을 받지 않았다. 운영 승격 때 별도 백업과 승인된 migration 절차가 필요하다.
- `NotificationDelivery`는 현재 Guardian·Telegram 전달 모델이다. Android FCM을 추가할 때 channel과 device token 수명 경계를 새 요구사항에 맞춰 재설계해야 한다.
- legacy self-hosted runner와 JAR 자동 배포 파일은 역사적 rollback 근거로 남아 있지만 portable 수동 update의 필수 구성은 아니다.
- 실제 Telegram 발송은 Goal 10에서 하지 않았다. 중복·재시도는 격리 test와 이전 승인된 시험으로 검증했다.

## 릴리스와 롤백 계획

1. backend와 web의 최신 `origin/main`이 위 기준선과 같은지 다시 확인한다.
2. 두 저장소의 기존 main을 `archive/v0.0.7-pre-enhancement`로 push하고 SHA를 재검증한다.
3. 검증된 enhancement를 각 main에 `--no-ff`로 병합하고 push한다.
4. 두 main CI 성공과 enhancement 포함 여부를 확인한다.
5. 운영 승격은 별도 승인 후 운영 DB dump, migration, image update, health와 사용자 흐름 순서로 수행한다.
6. 앱 image 장애는 직전 image와 `.env` release 기록으로 rollback한다. DB migration rollback은 자동 실행하지 않고 승인된 dump를 별도 복원 검증한 뒤 수행한다.

릴리스 tag 후보는 `v0.0.7-enhancement.1`이지만 이번 Goal에서는 생성하지 않는다. 최종 main merge commit은 자기 자신을 문서에 포함할 수 없으므로 완료 보고와 Git history에 기록한다.

## 시행착오와 해결

Windows PowerShell에서 `npm`을 호출하자 script 실행 정책이 `npm.ps1`을 차단했지만 `$LASTEXITCODE`가 설정되지 않아 명령 전체가 0처럼 보였다. `npm.cmd`를 직접 호출하고 각 단계의 종료코드를 검사하도록 바꿨다. 첫 sandbox 실행은 esbuild의 상위 경로 조회가 거부되어 test 시작 전에 실패했고, 저장소 범위의 비격리 실행으로 같은 명령을 검증했다.

## Android 2차 개발

이번 Goal에서는 Android 코드, API, table, Device, Pairing과 FCM 설정을 만들지 않았다. 후속 판단과 단계는 [Android 2차 개발 로드맵](../roadmaps/android-phase-2.md)에 정리했다.
